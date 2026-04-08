package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.JsonToTypeConversionResult
import com.livteam.jsoninja.model.typeConversion.TypeConversionWarning
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeField
import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import com.livteam.jsoninja.model.typeConversion.TypeReference
import java.util.LinkedHashMap

class JsonToTypeInferenceContext(
    private val language: SupportedLanguage,
    private val options: JsonToTypeConversionOptions,
) {
    private val warnings = mutableListOf<TypeConversionWarning>()
    private val declarationsByName = LinkedHashMap<String, TypeDeclaration>()
    private val signatureToDeclarationName = mutableMapOf<String, String>()

    fun infer(jsonNode: JsonNode): JsonToTypeConversionResult {
        val rootTypeName = JsonToTypeNamingSupport.toTypeName(options.rootTypeName)
        val rootTypeReference = inferType(
            jsonNode = jsonNode,
            suggestedTypeName = rootTypeName,
            depth = 0,
            forceDeclarationName = true,
        )
        if (!jsonNode.isObject) {
            declarationsByName[rootTypeName] = TypeDeclaration(
                name = rootTypeName,
                declarationKind = TypeDeclarationKind.TYPE_ALIAS,
                aliasedTypeReference = rootTypeReference,
            )
        }

        return JsonToTypeConversionResult(
            sourceCode = "",
            warnings = warnings.toList(),
            declarations = declarationsByName.values.toList(),
        )
    }

    private fun inferType(
        jsonNode: JsonNode,
        suggestedTypeName: String,
        depth: Int,
        forceDeclarationName: Boolean = false,
    ): TypeReference {
        if (depth >= options.maximumDepth) {
            warnings += TypeConversionWarning(
                "Maximum type inference depth ${options.maximumDepth} reached near $suggestedTypeName.",
            )
            return TypeReference.AnyValue
        }

        return when {
            jsonNode.isNull -> JsonToTypeSupport.nullPlaceholder()
            jsonNode.isTextual -> TypeReference.Primitive(TypePrimitiveKind.STRING)
            jsonNode.isBoolean -> TypeReference.Primitive(TypePrimitiveKind.BOOLEAN)
            jsonNode.isIntegralNumber -> TypeReference.Primitive(TypePrimitiveKind.INTEGER)
            jsonNode.isFloatingPointNumber || jsonNode.isBigDecimal -> {
                TypeReference.Primitive(TypePrimitiveKind.DECIMAL)
            }

            jsonNode.isNumber -> TypeReference.Primitive(TypePrimitiveKind.NUMBER)
            jsonNode.isArray -> inferArrayType(
                arrayNode = jsonNode as ArrayNode,
                suggestedTypeName = suggestedTypeName,
                depth = depth,
            )

            jsonNode.isObject -> inferObjectType(
                objectNodes = listOf(jsonNode as ObjectNode),
                suggestedTypeName = suggestedTypeName,
                depth = depth,
                forceDeclarationName = forceDeclarationName,
            )

            else -> TypeReference.AnyValue
        }
    }

    private fun inferArrayType(
        arrayNode: ArrayNode,
        suggestedTypeName: String,
        depth: Int,
    ): TypeReference {
        if (arrayNode.isEmpty) {
            return TypeReference.ListReference(TypeReference.AnyValue)
        }

        val nonNullElements = arrayNode.toList().filterNot(JsonNode::isNull)
        if (nonNullElements.isEmpty()) {
            return TypeReference.ListReference(JsonToTypeSupport.nullPlaceholder())
        }

        val elementTypeName = JsonToTypeNamingSupport.buildNestedTypeName(
            parentTypeName = suggestedTypeName,
            fieldSourceName = "items",
        )

        val elementType = if (nonNullElements.all(JsonNode::isObject)) {
            inferObjectType(
                objectNodes = nonNullElements.map { it as ObjectNode },
                suggestedTypeName = elementTypeName,
                depth = depth + 1,
            )
        } else {
            var mergedTypeReference: TypeReference? = null
            nonNullElements.forEach { elementNode ->
                val inferredElementType = inferType(
                    jsonNode = elementNode,
                    suggestedTypeName = elementTypeName,
                    depth = depth + 1,
                )
                mergedTypeReference = if (mergedTypeReference == null) {
                    inferredElementType
                } else {
                    JsonToTypeSupport.mergeTypeReferences(
                        mergedTypeReference,
                        inferredElementType,
                        allowsNullableFields = options.allowsNullableFields,
                        usesExperimentalGoUnionTypes = options.usesExperimentalGoUnionTypes,
                    )
                }
            }
            mergedTypeReference ?: TypeReference.AnyValue
        }

        val finalElementType = if (arrayNode.any(JsonNode::isNull)) {
            JsonToTypeSupport.mergeTypeReferences(
                elementType,
                JsonToTypeSupport.nullPlaceholder(),
                allowsNullableFields = options.allowsNullableFields,
                usesExperimentalGoUnionTypes = options.usesExperimentalGoUnionTypes,
            )
        } else {
            elementType
        }

        return TypeReference.ListReference(finalElementType)
    }

    private fun inferObjectType(
        objectNodes: List<ObjectNode>,
        suggestedTypeName: String,
        depth: Int,
        forceDeclarationName: Boolean = false,
    ): TypeReference {
        val usedFieldNames = mutableSetOf<String>()
        val fieldSourceNames = linkedMapOf<String, MutableList<JsonNode>>()
        objectNodes.forEach { objectNode ->
            objectNode.fields().forEachRemaining { (fieldName, fieldValue) ->
                fieldSourceNames.getOrPut(fieldName) { mutableListOf() }.add(fieldValue)
            }
        }

        val inferredFields = fieldSourceNames.entries.map { entry ->
            val fieldSourceName = entry.key
            val fieldValues = entry.value
            val fieldTypeName = JsonToTypeNamingSupport.buildNestedTypeName(suggestedTypeName, fieldSourceName)
            var mergedTypeReference: TypeReference? = null
            fieldValues.forEach { fieldValue ->
                val inferredFieldType = inferType(
                    jsonNode = fieldValue,
                    suggestedTypeName = fieldTypeName,
                    depth = depth + 1,
                )
                mergedTypeReference = if (mergedTypeReference == null) {
                    inferredFieldType
                } else {
                    JsonToTypeSupport.mergeTypeReferences(
                        mergedTypeReference,
                        inferredFieldType,
                        allowsNullableFields = options.allowsNullableFields,
                        usesExperimentalGoUnionTypes = options.usesExperimentalGoUnionTypes,
                    )
                }
            }
            val finalMergedTypeReference = mergedTypeReference ?: TypeReference.AnyValue

            val normalizedFieldName = JsonToTypeNamingSupport.toFieldName(
                rawName = fieldSourceName,
                namingConvention = options.namingConvention,
                language = language,
                usedNames = usedFieldNames,
            )
            usedFieldNames += normalizedFieldName

            TypeField(
                name = normalizedFieldName,
                typeReference = if (fieldValues.size < objectNodes.size) {
                    JsonToTypeSupport.mergeTypeReferences(
                        finalMergedTypeReference,
                        JsonToTypeSupport.nullPlaceholder(),
                        allowsNullableFields = options.allowsNullableFields,
                        usesExperimentalGoUnionTypes = options.usesExperimentalGoUnionTypes,
                    )
                } else {
                    finalMergedTypeReference
                },
                isOptional = fieldValues.size < objectNodes.size,
                sourceName = fieldSourceName,
            )
        }.sortedBy(TypeField::name)

        val signature = inferredFields.joinToString(prefix = "{", postfix = "}") {
            "${it.name}:${JsonToTypeSupport.buildTypeSignature(it.typeReference)}:${it.isOptional}"
        }
        if (!forceDeclarationName) {
            signatureToDeclarationName[signature]?.let { return TypeReference.Named(it) }
        }

        val declarationName = if (forceDeclarationName) {
            JsonToTypeNamingSupport.toTypeName(suggestedTypeName)
        } else {
            signatureToDeclarationName[signature] ?: JsonToTypeNamingSupport.toTypeName(suggestedTypeName)
        }
        signatureToDeclarationName.putIfAbsent(signature, declarationName)
        declarationsByName[declarationName] = TypeDeclaration(
            name = declarationName,
            declarationKind = when (language) {
                SupportedLanguage.TYPESCRIPT -> TypeDeclarationKind.INTERFACE
                SupportedLanguage.GO -> TypeDeclarationKind.STRUCT
                else -> TypeDeclarationKind.CLASS
            },
            fields = inferredFields,
        )
        return TypeReference.Named(declarationName)
    }
}
