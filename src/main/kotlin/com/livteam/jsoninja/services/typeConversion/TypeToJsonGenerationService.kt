package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisResult
import com.livteam.jsoninja.model.typeConversion.TypeConversionLanguage
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeField
import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import com.livteam.jsoninja.model.typeConversion.TypeReference
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import net.datafaker.Faker
import java.util.Locale

@Service(Service.Level.PROJECT)
class TypeToJsonGenerationService(private val project: Project) {
    private val analyzerService = project.service<TypeDeclarationAnalyzerService>()
    private val formatterService = project.service<com.livteam.jsoninja.services.JsonFormatterService>()
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val faker = Faker(Locale.ENGLISH)

    fun generateJsonFromTypeDeclaration(
        language: TypeConversionLanguage,
        sourceCode: String,
        rootTypeName: String? = null,
        options: TypeToJsonGenerationOptions = TypeToJsonGenerationOptions(),
    ): String {
        val analysisResult = analyzerService.analyzeTypeDeclaration(
            language = language,
            sourceCode = sourceCode,
            rootTypeName = rootTypeName,
        )
        return generateJsonFromAnalysisResult(analysisResult, options)
    }

    fun generateJsonFromAnalysisResult(
        analysisResult: TypeAnalysisResult,
        options: TypeToJsonGenerationOptions = TypeToJsonGenerationOptions(),
    ): String {
        require(options.outputCount in 1..100) { "Output count must be between 1 and 100." }

        val rootJsonNode = if (options.outputCount == 1) {
            createJsonNode(
                typeReference = TypeReference.Named(analysisResult.rootType.name),
                typeDeclarations = analysisResult.typeDeclarations,
                visitedTypeNames = mutableSetOf(),
                options = options,
                fieldName = analysisResult.rootType.name,
            )
        } else {
            objectMapper.createArrayNode().apply {
                repeat(options.outputCount) {
                    add(
                        createJsonNode(
                            typeReference = TypeReference.Named(analysisResult.rootType.name),
                            typeDeclarations = analysisResult.typeDeclarations,
                            visitedTypeNames = mutableSetOf(),
                            options = options,
                            fieldName = analysisResult.rootType.name,
                        )
                    )
                }
            }
        }
        val rawJson = objectMapper.writeValueAsString(rootJsonNode)
        return formatterService.formatJson(rawJson, options.formatState)
    }

    private fun createJsonNode(
        typeReference: TypeReference,
        typeDeclarations: Map<String, TypeDeclaration>,
        visitedTypeNames: MutableSet<String>,
        options: TypeToJsonGenerationOptions,
        fieldName: String,
    ): JsonNode {
        return when (typeReference) {
            is TypeReference.AnyValue -> objectMapper.nullNode()
            is TypeReference.InlineObject -> createObjectNode(
                fields = typeReference.fields,
                typeDeclarations = typeDeclarations,
                visitedTypeNames = visitedTypeNames,
                options = options,
            )
            is TypeReference.ListReference -> objectMapper.createArrayNode().apply {
                add(
                    createJsonNode(
                        typeReference = typeReference.elementType,
                        typeDeclarations = typeDeclarations,
                        visitedTypeNames = visitedTypeNames,
                        options = options,
                        fieldName = singularizeFieldName(fieldName),
                    )
                )
            }

            is TypeReference.MapReference -> objectMapper.createObjectNode().apply {
                set<JsonNode>(
                    "key",
                    createJsonNode(
                        typeReference = typeReference.valueType,
                        typeDeclarations = typeDeclarations,
                        visitedTypeNames = visitedTypeNames,
                        options = options,
                        fieldName = "value",
                    ),
                )
            }

            is TypeReference.Named -> createNamedTypeNode(
                typeName = typeReference.name,
                typeDeclarations = typeDeclarations,
                visitedTypeNames = visitedTypeNames,
                options = options,
            )
            is TypeReference.Nullable -> {
                if (options.includesNullableFieldWithNullValue) {
                    objectMapper.nullNode()
                } else {
                    createJsonNode(
                        typeReference = typeReference.wrappedType,
                        typeDeclarations = typeDeclarations,
                        visitedTypeNames = visitedTypeNames,
                        options = options,
                        fieldName = fieldName,
                    )
                }
            }

            is TypeReference.Primitive -> createPrimitiveNode(
                primitiveKind = typeReference.primitiveKind,
                fieldName = fieldName,
                usesRealisticSampleData = options.usesRealisticSampleData,
            )
        }
    }

    private fun createNamedTypeNode(
        typeName: String,
        typeDeclarations: Map<String, TypeDeclaration>,
        visitedTypeNames: MutableSet<String>,
        options: TypeToJsonGenerationOptions,
    ): JsonNode {
        val declaration = typeDeclarations[typeName] ?: return objectMapper.createObjectNode()
        if (declaration.declarationKind == com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind.ENUM) {
            return objectMapper.nodeFactory.textNode(declaration.enumValues.firstOrNull().orEmpty())
        }
        if (!visitedTypeNames.add(typeName)) {
            return objectMapper.nullNode()
        }

        return try {
            createObjectNode(
                fields = collectFields(typeName, typeDeclarations),
                typeDeclarations = typeDeclarations,
                visitedTypeNames = visitedTypeNames,
                options = options,
            )
        } finally {
            visitedTypeNames.remove(typeName)
        }
    }

    private fun createObjectNode(
        fields: List<TypeField>,
        typeDeclarations: Map<String, TypeDeclaration>,
        visitedTypeNames: MutableSet<String>,
        options: TypeToJsonGenerationOptions,
    ): ObjectNode {
        return objectMapper.createObjectNode().apply {
            fields.forEach { field ->
                if (!shouldIncludeField(field, options.propertyGenerationMode)) {
                    return@forEach
                }
                set<JsonNode>(
                    field.name,
                    createJsonNode(
                        typeReference = field.typeReference,
                        typeDeclarations = typeDeclarations,
                        visitedTypeNames = visitedTypeNames,
                        options = options,
                        fieldName = field.name,
                    ),
                )
            }
        }
    }

    private fun collectFields(
        typeName: String,
        typeDeclarations: Map<String, TypeDeclaration>,
        visitedTypeNames: MutableSet<String> = mutableSetOf(),
    ): List<TypeField> {
        val declaration = typeDeclarations[typeName] ?: return emptyList()
        if (!visitedTypeNames.add(typeName)) {
            return declaration.fields
        }

        val inheritedFields = declaration.superTypeNames.flatMap { superTypeName ->
            collectFields(superTypeName, typeDeclarations, visitedTypeNames)
        }

        visitedTypeNames.remove(typeName)

        return (inheritedFields + declaration.fields).distinctBy { field -> field.name }
    }

    private fun createPrimitiveNode(
        primitiveKind: TypePrimitiveKind,
        fieldName: String,
        usesRealisticSampleData: Boolean,
    ): JsonNode {
        return when (primitiveKind) {
            TypePrimitiveKind.STRING -> objectMapper.nodeFactory.textNode(
                if (usesRealisticSampleData) {
                    createSampleStringValue(fieldName)
                } else {
                    ""
                }
            )

            TypePrimitiveKind.NUMBER -> objectMapper.nodeFactory.numberNode(
                if (usesRealisticSampleData) {
                    createSampleNumberValue(fieldName)
                } else {
                    0
                }
            )

            TypePrimitiveKind.BOOLEAN -> objectMapper.nodeFactory.booleanNode(
                if (usesRealisticSampleData) {
                    fieldName.startsWith("is", ignoreCase = true) ||
                        fieldName.startsWith("has", ignoreCase = true) ||
                        fieldName.startsWith("can", ignoreCase = true)
                } else {
                    false
                }
            )
        }
    }

    private fun shouldIncludeField(
        field: TypeField,
        propertyGenerationMode: SchemaPropertyGenerationMode,
    ): Boolean {
        return when (propertyGenerationMode) {
            SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL,
            SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED -> true

            SchemaPropertyGenerationMode.REQUIRED_ONLY -> {
                !field.isOptional && field.typeReference !is TypeReference.Nullable
            }
        }
    }

    private fun createSampleStringValue(fieldName: String): String {
        val normalizedFieldName = fieldName.lowercase()
        return when {
            "email" in normalizedFieldName -> faker.internet().emailAddress()
            "name" in normalizedFieldName -> faker.name().fullName()
            "phone" in normalizedFieldName -> faker.phoneNumber().cellPhone()
            "city" in normalizedFieldName -> faker.address().city()
            "country" in normalizedFieldName -> faker.address().country()
            "street" in normalizedFieldName -> faker.address().streetAddress()
            "zip" in normalizedFieldName || "postal" in normalizedFieldName -> faker.address().zipCode()
            "url" in normalizedFieldName || "uri" in normalizedFieldName -> faker.internet().url()
            "id" == normalizedFieldName || normalizedFieldName.endsWith("id") -> faker.number().digits(8)
            "company" in normalizedFieldName -> faker.company().name()
            else -> faker.lorem().word()
        }
    }

    private fun createSampleNumberValue(fieldName: String): Int {
        val normalizedFieldName = fieldName.lowercase()
        return when {
            "age" in normalizedFieldName -> faker.number().numberBetween(18, 80)
            "count" in normalizedFieldName -> faker.number().numberBetween(1, 10)
            "year" in normalizedFieldName -> faker.number().numberBetween(2020, 2030)
            else -> faker.number().numberBetween(1, 1000)
        }
    }

    private fun singularizeFieldName(fieldName: String): String {
        return if (fieldName.endsWith("s", ignoreCase = true) && fieldName.length > 1) {
            fieldName.dropLast(1)
        } else {
            fieldName
        }
    }
}
