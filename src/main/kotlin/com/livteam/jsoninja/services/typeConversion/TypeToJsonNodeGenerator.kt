package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeReference
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode

class TypeToJsonNodeGenerator(
    private val objectMapper: ObjectMapper,
    private val sampleValueGenerator: SampleValueGenerator = SampleValueGenerator(),
) {
    fun generateNode(
        typeReference: TypeReference,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        visitedTypeNames: Set<String> = emptySet(),
        fieldName: String = "value",
    ): JsonNode = when (typeReference) {
        TypeReference.AnyValue -> objectMapper.nullNode()
        is TypeReference.InlineObject -> generateInlineObjectNode(typeReference, declarationsByName, options, visitedTypeNames)
        is TypeReference.ListReference -> generateArrayNode(typeReference, declarationsByName, options, visitedTypeNames, fieldName)
        is TypeReference.MapReference -> generateMapNode(typeReference, declarationsByName, options, visitedTypeNames, fieldName)
        is TypeReference.Named -> generateNamedNode(typeReference, declarationsByName, options, visitedTypeNames)
        is TypeReference.Nullable -> {
            if (options.includesNullableFieldWithNullValue) objectMapper.nullNode()
            else generateNode(typeReference.wrappedType, declarationsByName, options, visitedTypeNames, fieldName)
        }
        is TypeReference.Primitive -> objectMapper.valueToTree(sampleValueGenerator.generatePrimitiveValue(fieldName, typeReference.primitiveKind, options.usesRealisticSampleData))
        is TypeReference.Union -> {
            val firstMember = typeReference.members.firstOrNull() ?: TypeReference.AnyValue
            generateNode(firstMember, declarationsByName, options, visitedTypeNames, fieldName)
        }
    }

    private fun generateInlineObjectNode(
        typeReference: TypeReference.InlineObject,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        visitedTypeNames: Set<String>,
    ): ObjectNode {
        val objectNode = objectMapper.createObjectNode()
        typeReference.fields.forEach { field ->
            if (shouldIncludeField(field.isOptional, options.propertyGenerationMode)) {
                objectNode.set<JsonNode>(
                    field.sourceName,
                    generateNode(field.typeReference, declarationsByName, options, visitedTypeNames, field.sourceName),
                )
            }
        }
        return objectNode
    }

    private fun generateArrayNode(
        typeReference: TypeReference.ListReference,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        visitedTypeNames: Set<String>,
        fieldName: String,
    ): ArrayNode {
        return objectMapper.createArrayNode().add(
            generateNode(typeReference.elementType, declarationsByName, options, visitedTypeNames, fieldName),
        )
    }

    private fun generateMapNode(
        typeReference: TypeReference.MapReference,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        visitedTypeNames: Set<String>,
        fieldName: String,
    ): ObjectNode {
        val objectNode = objectMapper.createObjectNode()
        val keyValue = when (typeReference.keyType) {
            is TypeReference.Primitive -> sampleValueGenerator.generatePrimitiveValue("key", typeReference.keyType.primitiveKind, false).toString()
            else -> "key"
        }
        objectNode.set<JsonNode>(
            keyValue,
            generateNode(typeReference.valueType, declarationsByName, options, visitedTypeNames, fieldName),
        )
        return objectNode
    }

    private fun generateNamedNode(
        typeReference: TypeReference.Named,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        visitedTypeNames: Set<String>,
    ): JsonNode {
        if (typeReference.name in visitedTypeNames) {
            return objectMapper.createObjectNode()
        }

        val declaration = declarationsByName[typeReference.name] ?: return objectMapper.nullNode()
        return when (declaration.aliasedTypeReference) {
            null -> {
                if (declaration.enumValues.isNotEmpty()) {
                    objectMapper.valueToTree(declaration.enumValues.firstOrNull().orEmpty())
                } else {
                    val objectNode = objectMapper.createObjectNode()
                    TypeDeclarationFieldResolver.resolveFields(
                        declaration = declaration,
                        declarationsByName = declarationsByName,
                        visitedTypeNames = visitedTypeNames,
                    ).forEach { field ->
                        if (shouldIncludeField(field.isOptional, options.propertyGenerationMode)) {
                            objectNode.set<JsonNode>(
                                field.sourceName,
                                generateNode(
                                    typeReference = field.typeReference,
                                    declarationsByName = declarationsByName,
                                    options = options,
                                    visitedTypeNames = visitedTypeNames + typeReference.name,
                                    fieldName = field.sourceName,
                                ),
                            )
                        }
                    }
                    objectNode
                }
            }
            else -> generateNode(
                declaration.aliasedTypeReference,
                declarationsByName,
                options,
                visitedTypeNames + typeReference.name,
                typeReference.name,
            )
        }
    }

    private fun shouldIncludeField(
        isOptional: Boolean,
        propertyGenerationMode: SchemaPropertyGenerationMode,
    ): Boolean {
        return when (propertyGenerationMode) {
            SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL -> true
            SchemaPropertyGenerationMode.REQUIRED_ONLY -> !isOptional
            SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED -> true
        }
    }
}
