package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeReference
import com.livteam.jsoninja.model.typeConversion.TypeEnumValue
import com.livteam.jsoninja.LocalizationBundle
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
    ): JsonNode = generateNodeWithOptionalFields(typeReference, declarationsByName, options, visitedTypeNames, fieldName)

    internal fun generateNodeWithOptionalFields(
        typeReference: TypeReference,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        visitedTypeNames: Set<String> = emptySet(),
        fieldName: String = "value",
        onOptionalField: ((ObjectNode, String) -> Unit)? = null,
    ): JsonNode = when (typeReference) {
        TypeReference.AnyValue -> objectMapper.nullNode()
        is TypeReference.InlineObject -> generateInlineObjectNode(typeReference, declarationsByName, options, visitedTypeNames, onOptionalField)
        is TypeReference.ListReference -> generateArrayNode(typeReference, declarationsByName, options, visitedTypeNames, fieldName, onOptionalField)
        is TypeReference.MapReference -> generateMapNode(typeReference, declarationsByName, options, visitedTypeNames, fieldName, onOptionalField)
        is TypeReference.Named -> generateNamedNode(typeReference, declarationsByName, options, visitedTypeNames, onOptionalField)
        is TypeReference.Nullable -> {
            if (options.includesNullableFieldWithNullValue) objectMapper.nullNode()
            else generateNodeWithOptionalFields(typeReference.wrappedType, declarationsByName, options, visitedTypeNames, fieldName, onOptionalField)
        }
        is TypeReference.Primitive -> objectMapper.valueToTree(sampleValueGenerator.generatePrimitiveValue(fieldName, typeReference.primitiveKind, options.usesRealisticSampleData))
        is TypeReference.Union -> {
            val firstMember = typeReference.members.firstOrNull() ?: TypeReference.AnyValue
            generateNodeWithOptionalFields(firstMember, declarationsByName, options, visitedTypeNames, fieldName, onOptionalField)
        }
    }

    private fun generateInlineObjectNode(
        typeReference: TypeReference.InlineObject,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        visitedTypeNames: Set<String>,
        onOptionalField: ((ObjectNode, String) -> Unit)?,
    ): ObjectNode {
        val objectNode = objectMapper.createObjectNode()
        typeReference.fields.forEach { field ->
            if (field.isOptional) onOptionalField?.invoke(objectNode, field.sourceName)
            if (shouldIncludeField(field.isOptional, options.propertyGenerationMode)) {
                objectNode.set<JsonNode>(
                    field.sourceName,
                    generateNodeWithOptionalFields(field.typeReference, declarationsByName, options, visitedTypeNames, field.sourceName, onOptionalField),
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
        onOptionalField: ((ObjectNode, String) -> Unit)?,
    ): ArrayNode {
        return objectMapper.createArrayNode().add(
            generateNodeWithOptionalFields(typeReference.elementType, declarationsByName, options, visitedTypeNames, fieldName, onOptionalField),
        )
    }

    private fun generateMapNode(
        typeReference: TypeReference.MapReference,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        visitedTypeNames: Set<String>,
        fieldName: String,
        onOptionalField: ((ObjectNode, String) -> Unit)?,
    ): ObjectNode {
        val objectNode = objectMapper.createObjectNode()
        val keyValue = when (typeReference.keyType) {
            is TypeReference.Primitive -> sampleValueGenerator.generatePrimitiveValue("key", typeReference.keyType.primitiveKind, false).toString()
            else -> "key"
        }
        objectNode.set<JsonNode>(
            keyValue,
            generateNodeWithOptionalFields(typeReference.valueType, declarationsByName, options, visitedTypeNames, fieldName, onOptionalField),
        )
        return objectNode
    }

    private fun generateNamedNode(
        typeReference: TypeReference.Named,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        visitedTypeNames: Set<String>,
        onOptionalField: ((ObjectNode, String) -> Unit)?,
    ): JsonNode {
        if (typeReference.name in visitedTypeNames) {
            return objectMapper.createObjectNode()
        }

        val declaration = declarationsByName[typeReference.name] ?: return objectMapper.nullNode()
        return when (declaration.aliasedTypeReference) {
            null -> {
                if (declaration.enumLiteralValues.isNotEmpty()) {
                    when (val value = declaration.enumLiteralValues.first()) {
                        is TypeEnumValue.StringValue -> objectMapper.nodeFactory.textNode(value.value)
                        is TypeEnumValue.NumberValue -> objectMapper.nodeFactory.numberNode(value.value)
                        is TypeEnumValue.Unresolved -> throw IllegalStateException(LocalizationBundle.message(
                            "validation.type.to.json.enum.value.unsupported", "${declaration.name}.${declaration.enumValues.firstOrNull().orEmpty()}"
                        ))
                    }
                } else if (declaration.enumValues.isNotEmpty()) {
                    objectMapper.valueToTree<JsonNode>(declaration.enumValues.firstOrNull().orEmpty())
                } else {
                    val objectNode = objectMapper.createObjectNode()
                    TypeDeclarationFieldResolver.resolveFields(
                        declaration = declaration,
                        declarationsByName = declarationsByName,
                        visitedTypeNames = visitedTypeNames,
                    ).forEach { field ->
                        if (field.isOptional) onOptionalField?.invoke(objectNode, field.sourceName)
                        if (shouldIncludeField(field.isOptional, options.propertyGenerationMode)) {
                            objectNode.set<JsonNode>(
                                field.sourceName,
                                generateNodeWithOptionalFields(
                                    typeReference = field.typeReference,
                                    declarationsByName = declarationsByName,
                                    options = options,
                                    visitedTypeNames = visitedTypeNames + typeReference.name,
                                    fieldName = field.sourceName,
                                    onOptionalField = onOptionalField,
                                ),
                            )
                        }
                    }
                    objectNode
                }
            }
            else -> generateNodeWithOptionalFields(
                declaration.aliasedTypeReference,
                declarationsByName,
                options,
                visitedTypeNames + typeReference.name,
                typeReference.name,
                onOptionalField,
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
            SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED -> !isOptional
        }
    }
}
