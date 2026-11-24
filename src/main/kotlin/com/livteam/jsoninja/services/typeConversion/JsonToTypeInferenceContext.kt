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
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRegistry
import java.util.LinkedHashMap

class JsonToTypeInferenceContext(
    private val language: SupportedLanguage,
    private val options: JsonToTypeConversionOptions,
) {
    private val languagePolicy = TypeLanguageRegistry.forLanguage(language).policy
    private val warnings = mutableListOf<TypeConversionWarning>()
    private val declarationsByName = LinkedHashMap<String, TypeDeclaration>()
    private val signatureToDeclarationName = HashMap<String, String>()
    private val declarationNamesByPath = HashMap<SourcePathNode, String>()
    private val usedDeclarationNames = HashSet<String>()

    fun infer(jsonNode: JsonNode): JsonToTypeConversionResult {
        val rootTypeName = declarationName(options.rootTypeName)
        usedDeclarationNames += rootTypeName
        val rootTypeReference = inferType(
            jsonNode = jsonNode,
            suggestedTypeName = rootTypeName,
            depth = 0,
            forceDeclarationName = true,
            sourcePath = SourcePathNode.ROOT,
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
        sourcePath: SourcePathNode,
    ): TypeReference {
        if (depth >= options.maximumDepth) {
            warnings += TypeConversionWarning(
                "Maximum type inference depth ${options.maximumDepth} reached near $suggestedTypeName.",
            )
            return TypeReference.AnyValue
        }

        inferPrimitiveOrNull(jsonNode)?.let { return it }

        return when {
            jsonNode.isArray -> inferArrayType(
                arrayNode = jsonNode as ArrayNode,
                suggestedTypeName = suggestedTypeName,
                depth = depth,
                sourcePath = sourcePath,
            )

            jsonNode.isObject -> inferSingleObjectType(
                objectNode = jsonNode as ObjectNode,
                suggestedTypeName = suggestedTypeName,
                depth = depth,
                forceDeclarationName = forceDeclarationName,
                sourcePath = sourcePath,
            )

            else -> TypeReference.AnyValue
        }
    }

    private fun inferArrayType(
        arrayNode: ArrayNode,
        suggestedTypeName: String,
        depth: Int,
        sourcePath: SourcePathNode,
    ): TypeReference {
        val size = arrayNode.size()
        if (size == 0) {
            return EMPTY_LIST_REFERENCE
        }

        var hasNull = false
        var nonNullCount = 0
        var allObjects = true
        for (index in 0 until size) {
            val element = arrayNode.get(index)
            if (element.isNull) {
                hasNull = true
            } else {
                nonNullCount++
                if (!element.isObject) {
                    allObjects = false
                }
            }
        }

        if (nonNullCount == 0) {
            return NULL_LIST_REFERENCE
        }

        val elementType = if (allObjects) {
            val elementTypeName = JsonToTypeNamingSupport.buildNestedTypeName(
                parentTypeName = suggestedTypeName,
                fieldSourceName = "items",
            )
            if (depth + 1 >= options.maximumDepth) {
                warnings += TypeConversionWarning(
                    "Maximum type inference depth ${options.maximumDepth} reached near $elementTypeName.",
                )
                TypeReference.AnyValue
            } else {
                val objectNodes = ArrayList<ObjectNode>(nonNullCount)
                for (index in 0 until size) {
                    val element = arrayNode.get(index)
                    if (!element.isNull) {
                        objectNodes.add(element as ObjectNode)
                    }
                }
                inferObjectType(
                    objectNodes = objectNodes,
                    suggestedTypeName = elementTypeName,
                    depth = depth + 1,
                    sourcePath = SourcePathNode(sourcePath, ARRAY_SEGMENT),
                )
            }
        } else {
            var cachedElementTypeName: String? = null
            var cachedArrayPath: SourcePathNode? = null
            var mergedTypeReference: TypeReference? = null

            for (index in 0 until size) {
                val elementNode = arrayNode.get(index)
                if (elementNode.isNull) continue

                val inferredElementType = if (depth + 1 < options.maximumDepth) {
                    inferPrimitiveOrNull(elementNode) ?: run {
                        val elementTypeName = cachedElementTypeName ?: JsonToTypeNamingSupport.buildNestedTypeName(
                            parentTypeName = suggestedTypeName,
                            fieldSourceName = "items",
                        ).also { cachedElementTypeName = it }
                        val arrayPath = cachedArrayPath ?: SourcePathNode(sourcePath, ARRAY_SEGMENT).also {
                            cachedArrayPath = it
                        }
                        inferType(
                            jsonNode = elementNode,
                            suggestedTypeName = elementTypeName,
                            depth = depth + 1,
                            sourcePath = arrayPath,
                        )
                    }
                } else {
                    val elementTypeName = cachedElementTypeName ?: JsonToTypeNamingSupport.buildNestedTypeName(
                        parentTypeName = suggestedTypeName,
                        fieldSourceName = "items",
                    ).also { cachedElementTypeName = it }
                    val arrayPath = cachedArrayPath ?: SourcePathNode(sourcePath, ARRAY_SEGMENT).also {
                        cachedArrayPath = it
                    }
                    inferType(
                        jsonNode = elementNode,
                        suggestedTypeName = elementTypeName,
                        depth = depth + 1,
                        sourcePath = arrayPath,
                    )
                }

                mergedTypeReference = mergeFoldedType(mergedTypeReference, inferredElementType)
            }
            mergedTypeReference ?: TypeReference.AnyValue
        }

        val finalElementType = if (hasNull) {
            mergeWithNull(elementType)
        } else {
            elementType
        }

        return TypeReference.ListReference(finalElementType)
    }

    /**
     * Folds multiple non-empty [ArrayNode] values collected for the same object field across an array of objects,
     * batching nested object elements into a single [inferObjectType] pass instead of re-merging per element.
     */
    private fun inferBatchedNonEmptyArrays(
        arrayNodes: List<ArrayNode>,
        suggestedTypeName: String,
        depth: Int,
        sourcePath: SourcePathNode,
    ): TypeReference {
        var hasNullElement = false
        var totalNonNullElements = 0
        var allNonNullAreObjects = true

        for (arrayIndex in arrayNodes.indices) {
            val arrayNode = arrayNodes[arrayIndex]
            for (elementIndex in 0 until arrayNode.size()) {
                val element = arrayNode.get(elementIndex)
                if (element.isNull) {
                    hasNullElement = true
                } else {
                    totalNonNullElements++
                    if (!element.isObject) {
                        allNonNullAreObjects = false
                    }
                }
            }
        }

        if (totalNonNullElements == 0) {
            return NULL_LIST_REFERENCE
        }

        val elementType = if (allNonNullAreObjects && depth + 1 < options.maximumDepth) {
            val elementTypeName = JsonToTypeNamingSupport.buildNestedTypeName(
                parentTypeName = suggestedTypeName,
                fieldSourceName = "items",
            )
            val objectElements = ArrayList<ObjectNode>(totalNonNullElements)
            for (arrayIndex in arrayNodes.indices) {
                val arrayNode = arrayNodes[arrayIndex]
                for (elementIndex in 0 until arrayNode.size()) {
                    val element = arrayNode.get(elementIndex)
                    if (!element.isNull) {
                        objectElements.add(element as ObjectNode)
                    }
                }
            }
            inferObjectType(
                objectNodes = objectElements,
                suggestedTypeName = elementTypeName,
                depth = depth + 1,
                sourcePath = SourcePathNode(sourcePath, ARRAY_SEGMENT),
            )
        } else {
            var cachedElementTypeName: String? = null
            var cachedArrayPath: SourcePathNode? = null
            var mergedElementType: TypeReference? = null

            for (arrayIndex in arrayNodes.indices) {
                val arrayNode = arrayNodes[arrayIndex]
                for (elementIndex in 0 until arrayNode.size()) {
                    val element = arrayNode.get(elementIndex)
                    if (element.isNull) continue

                    val inferredElementType = if (depth + 1 < options.maximumDepth) {
                        inferPrimitiveOrNull(element) ?: run {
                            val elementTypeName = cachedElementTypeName ?: JsonToTypeNamingSupport.buildNestedTypeName(
                                parentTypeName = suggestedTypeName,
                                fieldSourceName = "items",
                            ).also { cachedElementTypeName = it }
                            val arrayPath = cachedArrayPath ?: SourcePathNode(sourcePath, ARRAY_SEGMENT).also {
                                cachedArrayPath = it
                            }
                            inferType(
                                jsonNode = element,
                                suggestedTypeName = elementTypeName,
                                depth = depth + 1,
                                sourcePath = arrayPath,
                            )
                        }
                    } else {
                        val elementTypeName = cachedElementTypeName ?: JsonToTypeNamingSupport.buildNestedTypeName(
                            parentTypeName = suggestedTypeName,
                            fieldSourceName = "items",
                        ).also { cachedElementTypeName = it }
                        val arrayPath = cachedArrayPath ?: SourcePathNode(sourcePath, ARRAY_SEGMENT).also {
                            cachedArrayPath = it
                        }
                        inferType(
                            jsonNode = element,
                            suggestedTypeName = elementTypeName,
                            depth = depth + 1,
                            sourcePath = arrayPath,
                        )
                    }

                    mergedElementType = mergeFoldedType(mergedElementType, inferredElementType)
                }
            }
            mergedElementType ?: TypeReference.AnyValue
        }

        val finalElementType = if (hasNullElement) mergeWithNull(elementType) else elementType
        return TypeReference.ListReference(finalElementType)
    }

    private fun inferSingleObjectType(
        objectNode: ObjectNode,
        suggestedTypeName: String,
        depth: Int,
        forceDeclarationName: Boolean = false,
        sourcePath: SourcePathNode,
    ): TypeReference {
        val fieldCount = objectNode.size()
        val usedFieldNames = HashSet<String>(fieldCount * 2 + 1)
        val inferredFields = ArrayList<TypeField>(fieldCount)

        val fieldsIterator = objectNode.fields()
        while (fieldsIterator.hasNext()) {
            val entry = fieldsIterator.next()
            val fieldSourceName = entry.key
            val fieldValue = entry.value

            val fieldTypeReference = if (depth + 1 < options.maximumDepth) {
                inferPrimitiveOrNull(fieldValue) ?: run {
                    val fieldTypeName = JsonToTypeNamingSupport.buildNestedTypeName(suggestedTypeName, fieldSourceName)
                    inferType(
                        jsonNode = fieldValue,
                        suggestedTypeName = fieldTypeName,
                        depth = depth + 1,
                        sourcePath = SourcePathNode(sourcePath, "field:$fieldSourceName"),
                    )
                }
            } else {
                val fieldTypeName = JsonToTypeNamingSupport.buildNestedTypeName(suggestedTypeName, fieldSourceName)
                inferType(
                    jsonNode = fieldValue,
                    suggestedTypeName = fieldTypeName,
                    depth = depth + 1,
                    sourcePath = SourcePathNode(sourcePath, "field:$fieldSourceName"),
                )
            }

            val normalizedFieldName = JsonToTypeNamingSupport.toFieldName(
                rawName = fieldSourceName,
                namingConvention = options.namingConvention,
                language = language,
                usedNames = usedFieldNames,
            )
            usedFieldNames += normalizedFieldName

            inferredFields.add(
                TypeField(
                    name = normalizedFieldName,
                    typeReference = fieldTypeReference,
                    isOptional = false,
                    sourceName = fieldSourceName,
                ),
            )
        }

        inferredFields.sortBy(TypeField::name)
        return finalizeObjectDeclaration(
            inferredFields = inferredFields,
            suggestedTypeName = suggestedTypeName,
            forceDeclarationName = forceDeclarationName,
            sourcePath = sourcePath,
        )
    }

    private fun inferObjectType(
        objectNodes: List<ObjectNode>,
        suggestedTypeName: String,
        depth: Int,
        forceDeclarationName: Boolean = false,
        sourcePath: SourcePathNode,
    ): TypeReference {
        if (objectNodes.size == 1) {
            return inferSingleObjectType(
                objectNode = objectNodes[0],
                suggestedTypeName = suggestedTypeName,
                depth = depth,
                forceDeclarationName = forceDeclarationName,
                sourcePath = sourcePath,
            )
        }

        val fieldSourceNames = LinkedHashMap<String, MutableList<JsonNode>>()
        for (nodeIndex in objectNodes.indices) {
            val fieldsIterator = objectNodes[nodeIndex].fields()
            while (fieldsIterator.hasNext()) {
                val entry = fieldsIterator.next()
                fieldSourceNames.getOrPut(entry.key) { ArrayList() }.add(entry.value)
            }
        }

        val usedFieldNames = HashSet<String>(fieldSourceNames.size * 2 + 1)
        val inferredFields = ArrayList<TypeField>(fieldSourceNames.size)

        for ((fieldSourceName, fieldValues) in fieldSourceNames) {
            val finalMergedTypeReference = inferFieldValuesType(
                fieldSourceName = fieldSourceName,
                fieldValues = fieldValues,
                suggestedTypeName = suggestedTypeName,
                depth = depth,
                parentSourcePath = sourcePath,
            )

            val normalizedFieldName = JsonToTypeNamingSupport.toFieldName(
                rawName = fieldSourceName,
                namingConvention = options.namingConvention,
                language = language,
                usedNames = usedFieldNames,
            )
            usedFieldNames += normalizedFieldName

            val isOptional = fieldValues.size < objectNodes.size
            inferredFields.add(
                TypeField(
                    name = normalizedFieldName,
                    typeReference = if (isOptional) {
                        mergeWithNull(finalMergedTypeReference)
                    } else {
                        finalMergedTypeReference
                    },
                    isOptional = isOptional,
                    sourceName = fieldSourceName,
                ),
            )
        }

        inferredFields.sortBy(TypeField::name)
        return finalizeObjectDeclaration(
            inferredFields = inferredFields,
            suggestedTypeName = suggestedTypeName,
            forceDeclarationName = forceDeclarationName,
            sourcePath = sourcePath,
        )
    }

    private fun inferFieldValuesType(
        fieldSourceName: String,
        fieldValues: List<JsonNode>,
        suggestedTypeName: String,
        depth: Int,
        parentSourcePath: SourcePathNode,
    ): TypeReference {
        val nextDepth = depth + 1
        if (nextDepth < options.maximumDepth) {
            var hasNull = false
            var objectCount = 0
            var nonEmptyArrayCount = 0
            var hasEmptyArray = false
            var hasNonNullPrimitiveOrOther = false

            for (index in fieldValues.indices) {
                val value = fieldValues[index]
                when {
                    value.isNull -> hasNull = true
                    value.isObject -> objectCount++
                    value.isArray -> {
                        if (value.isEmpty) hasEmptyArray = true else nonEmptyArrayCount++
                    }
                    else -> hasNonNullPrimitiveOrOther = true
                }
            }

            // Fast path 1: pure primitives / nulls — zero regexes, zero SourcePathNode allocations, zero TypeReference copies.
            if (objectCount == 0 && nonEmptyArrayCount == 0 && !hasEmptyArray) {
                var merged: TypeReference? = null
                for (index in fieldValues.indices) {
                    val inferred = inferPrimitiveOrNull(fieldValues[index]) ?: TypeReference.AnyValue
                    merged = mergeFoldedType(merged, inferred)
                }
                return merged ?: TypeReference.AnyValue
            }

            // Fast path 2: all non-null values are ObjectNodes — batch into a single inferObjectType pass.
            if (objectCount > 0 && nonEmptyArrayCount == 0 && !hasEmptyArray && !hasNonNullPrimitiveOrOther) {
                val fieldTypeName = JsonToTypeNamingSupport.buildNestedTypeName(suggestedTypeName, fieldSourceName)
                val childSourcePath = SourcePathNode(parentSourcePath, "field:$fieldSourceName")
                val objectValues = ArrayList<ObjectNode>(objectCount)
                for (index in fieldValues.indices) {
                    val value = fieldValues[index]
                    if (value.isObject) {
                        objectValues.add(value as ObjectNode)
                    }
                }
                val objectType = inferObjectType(
                    objectNodes = objectValues,
                    suggestedTypeName = fieldTypeName,
                    depth = nextDepth,
                    sourcePath = childSourcePath,
                )
                return if (hasNull) mergeWithNull(objectType) else objectType
            }

            // Fast path 3: all non-null values are non-empty ArrayNodes — batch array element folding.
            if (nonEmptyArrayCount > 0 && objectCount == 0 && !hasEmptyArray && !hasNonNullPrimitiveOrOther) {
                val fieldTypeName = JsonToTypeNamingSupport.buildNestedTypeName(suggestedTypeName, fieldSourceName)
                val childSourcePath = SourcePathNode(parentSourcePath, "field:$fieldSourceName")
                val arrayValues = ArrayList<ArrayNode>(nonEmptyArrayCount)
                for (index in fieldValues.indices) {
                    val value = fieldValues[index]
                    if (value.isArray) {
                        arrayValues.add(value as ArrayNode)
                    }
                }
                val listType = inferBatchedNonEmptyArrays(
                    arrayNodes = arrayValues,
                    suggestedTypeName = fieldTypeName,
                    depth = nextDepth,
                    sourcePath = childSourcePath,
                )
                return if (hasNull) mergeWithNull(listType) else listType
            }
        }

        // General mixed / empty-array / max-depth path (shares single childSourcePath & fieldTypeName across all values).
        val fieldTypeName = JsonToTypeNamingSupport.buildNestedTypeName(suggestedTypeName, fieldSourceName)
        val childSourcePath = SourcePathNode(parentSourcePath, "field:$fieldSourceName")
        var mergedTypeReference: TypeReference? = null
        for (index in fieldValues.indices) {
            val inferredFieldType = inferType(
                jsonNode = fieldValues[index],
                suggestedTypeName = fieldTypeName,
                depth = nextDepth,
                sourcePath = childSourcePath,
            )
            mergedTypeReference = mergeFoldedType(mergedTypeReference, inferredFieldType)
        }
        return mergedTypeReference ?: TypeReference.AnyValue
    }

    private fun finalizeObjectDeclaration(
        inferredFields: List<TypeField>,
        suggestedTypeName: String,
        forceDeclarationName: Boolean,
        sourcePath: SourcePathNode,
    ): TypeReference {
        val signature = inferredFields.joinToString(prefix = "{", postfix = "}") {
            "${it.sourceName.length}:${it.sourceName}:${it.name}:${JsonToTypeSupport.buildTypeSignature(it.typeReference)}:${it.isOptional}"
        }
        val previousName = declarationNamesByPath[sourcePath]
        if (!forceDeclarationName && previousName == null) {
            signatureToDeclarationName[signature]?.let {
                declarationNamesByPath[sourcePath] = it
                return TypeReference.Named(it)
            }
        }

        val declarationName = if (previousName != null) {
            previousName
        } else if (forceDeclarationName) {
            declarationName(suggestedTypeName)
        } else {
            val baseName = declarationName(suggestedTypeName)
            var candidate = baseName
            var suffixIndex = 2
            while (candidate in usedDeclarationNames) candidate = "$baseName${suffixIndex++}"
            candidate
        }
        declarationNamesByPath[sourcePath] = declarationName
        usedDeclarationNames += declarationName
        signatureToDeclarationName.putIfAbsent(signature, declarationName)
        val inferredDeclaration = TypeDeclaration(
            name = declarationName,
            declarationKind = languagePolicy.objectDeclarationKind,
            fields = inferredFields,
        )
        val mergedDeclaration = declarationsByName[declarationName]?.let { existingDeclaration ->
            existingDeclaration.copy(
                fields = mergeDeclarationFields(
                    existingFields = existingDeclaration.fields,
                    incomingFields = inferredFields,
                ),
            )
        } ?: inferredDeclaration
        declarationsByName[declarationName] = languagePolicy.normalizeDeclaration(mergedDeclaration)
        return TypeReference.Named(declarationName)
    }

    private fun inferPrimitiveOrNull(jsonNode: JsonNode): TypeReference? {
        return when {
            jsonNode.isNull -> NULL_PLACEHOLDER
            jsonNode.isTextual -> PRIMITIVE_STRING
            jsonNode.isBoolean -> PRIMITIVE_BOOLEAN
            jsonNode.isIntegralNumber -> PRIMITIVE_INTEGER
            jsonNode.isFloatingPointNumber || jsonNode.isBigDecimal -> PRIMITIVE_DECIMAL
            jsonNode.isNumber -> PRIMITIVE_NUMBER
            else -> null
        }
    }

    private fun mergeFoldedType(current: TypeReference?, incoming: TypeReference): TypeReference {
        if (current == null || current === incoming || current == incoming) {
            return incoming
        }
        return JsonToTypeSupport.mergeTypeReferences(
            current,
            incoming,
            allowsNullableFields = options.allowsNullableFields,
            usesExperimentalGoUnionTypes = options.usesExperimentalGoUnionTypes,
        )
    }

    private fun mergeWithNull(typeReference: TypeReference): TypeReference {
        return JsonToTypeSupport.mergeTypeReferences(
            typeReference,
            NULL_PLACEHOLDER,
            allowsNullableFields = options.allowsNullableFields,
            usesExperimentalGoUnionTypes = options.usesExperimentalGoUnionTypes,
        )
    }

    private fun declarationName(candidate: String): String =
        languagePolicy.escapeDeclarationName(JsonToTypeNamingSupport.toTypeName(candidate))

    private fun mergeDeclarationFields(
        existingFields: List<TypeField>,
        incomingFields: List<TypeField>,
    ): List<TypeField> {
        val existingFieldsBySourceName = existingFields.associateBy(TypeField::sourceName)
        val incomingFieldsBySourceName = incomingFields.associateBy(TypeField::sourceName)
        val fieldSourceNames = existingFields.map(TypeField::sourceName) +
            incomingFields.map(TypeField::sourceName).filterNot(existingFieldsBySourceName::containsKey)

        val usedFieldNames = mutableSetOf<String>()
        return fieldSourceNames.map { fieldSourceName ->
            val existingField = existingFieldsBySourceName[fieldSourceName]
            val incomingField = incomingFieldsBySourceName[fieldSourceName]

            when {
                existingField != null && incomingField != null -> existingField.copy(
                    typeReference = JsonToTypeSupport.mergeTypeReferences(
                        existingField.typeReference,
                        incomingField.typeReference,
                        allowsNullableFields = options.allowsNullableFields,
                        usesExperimentalGoUnionTypes = options.usesExperimentalGoUnionTypes,
                    ),
                    isOptional = existingField.isOptional || incomingField.isOptional,
                )
                existingField != null -> existingField.copy(
                    typeReference = mergeWithNull(existingField.typeReference),
                    isOptional = true,
                )
                incomingField != null -> incomingField.copy(
                    typeReference = mergeWithNull(incomingField.typeReference),
                    isOptional = true,
                )
                else -> error("Cannot merge missing field `$fieldSourceName`.")
            }
        }.map { field ->
            val name = JsonToTypeNamingSupport.toFieldName(field.sourceName, options.namingConvention, language, usedFieldNames)
            usedFieldNames += name
            field.copy(name = name)
        }.sortedBy(TypeField::name)
    }

    private class SourcePathNode(
        private val parent: SourcePathNode?,
        private val segment: String,
    ) {
        private val cachedHashCode: Int = 31 * (parent?.cachedHashCode ?: 1) + segment.hashCode()

        override fun hashCode(): Int = cachedHashCode

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SourcePathNode) return false
            if (cachedHashCode != other.cachedHashCode) return false
            var left: SourcePathNode? = this
            var right: SourcePathNode? = other
            while (left != null && right != null) {
                if (left === right) return true
                if (left.cachedHashCode != right.cachedHashCode || left.segment != right.segment) return false
                left = left.parent
                right = right.parent
            }
            return left == null && right == null
        }

        companion object {
            val ROOT = SourcePathNode(null, "")
        }
    }

    companion object {
        private const val ARRAY_SEGMENT = "array"
        private val PRIMITIVE_STRING = TypeReference.Primitive(TypePrimitiveKind.STRING)
        private val PRIMITIVE_BOOLEAN = TypeReference.Primitive(TypePrimitiveKind.BOOLEAN)
        private val PRIMITIVE_INTEGER = TypeReference.Primitive(TypePrimitiveKind.INTEGER)
        private val PRIMITIVE_DECIMAL = TypeReference.Primitive(TypePrimitiveKind.DECIMAL)
        private val PRIMITIVE_NUMBER = TypeReference.Primitive(TypePrimitiveKind.NUMBER)
        private val NULL_PLACEHOLDER = JsonToTypeSupport.nullPlaceholder()
        private val EMPTY_LIST_REFERENCE = TypeReference.ListReference(TypeReference.AnyValue)
        private val NULL_LIST_REFERENCE = TypeReference.ListReference(NULL_PLACEHOLDER)
    }
}
