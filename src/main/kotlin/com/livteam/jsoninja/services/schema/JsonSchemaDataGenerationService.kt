package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.RandomJsonDataCreator
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import com.networknt.schema.JsonSchema

@Service(Service.Level.PROJECT)
class JsonSchemaDataGenerationService(private val project: Project) {
    private val LOG = logger<JsonSchemaDataGenerationService>()
    private val validationService = project.service<JsonSchemaValidationService>()
    private val normalizer = project.service<JsonSchemaNormalizer>()
    private val valueGenerator = project.service<JsonSchemaValueGenerator>()
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val randomJsonDataCreator = RandomJsonDataCreator()

    data class PreparedSchema(
        val resolvedSchemaNode: JsonNode,
        val compiledSchema: JsonSchema,
        val rootConstraint: JsonSchemaConstraint
    )

    private data class CommentedSchemaNodes(
        val activeNode: JsonNode,
        val completeNode: JsonNode
    )

    /**
     * EDT에서 호출 가능한 가벼운 스키마 텍스트 검증 (JSON 파싱 + 기본 구조 확인만 수행).
     * 전체 $ref 해석 및 networknt 컴파일은 수행하지 않음.
     */
    fun validateSchemaText(schemaText: String) {
        validationService.parseStrictSchema(schemaText)
    }

    fun prepareSchema(schemaText: String): PreparedSchema {
        val parsedSchemaNode = validationService.parseStrictSchema(schemaText)
        val normalizedJsonSchema = normalizer.normalize(parsedSchemaNode)
        val schemaValidationResult = validationService.validateSchema(normalizedJsonSchema.resolvedSchemaNode)
        if (!schemaValidationResult.isValid || schemaValidationResult.compiledSchema == null) {
            throw JsonSchemaGenerationException(
                message = schemaValidationResult.errorMessage ?: "Invalid JSON Schema.",
                jsonPointer = schemaValidationResult.jsonPointer
            )
        }

        return PreparedSchema(
            resolvedSchemaNode = normalizedJsonSchema.resolvedSchemaNode,
            compiledSchema = schemaValidationResult.compiledSchema,
            rootConstraint = normalizedJsonSchema.rootConstraint
        )
    }

    fun generateFromSchema(config: JsonGenerationConfig): String {
        if (config.generationMode != JsonGenerationMode.SCHEMA) {
            throw JsonSchemaGenerationException("Schema generation mode is required for generateFromSchema.")
        }

        if (config.schemaOutputCount <= 0) {
            throw JsonSchemaGenerationException("Schema output count must be greater than zero.")
        }

        val preparedSchema = prepareSchema(config.schemaText)
        if (config.schemaPropertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED) {
            return generateCommentedSchemaOutput(preparedSchema, config.schemaOutputCount)
        }

        val generatedNodes = mutableListOf<JsonNode>()
        repeat(config.schemaOutputCount) {
            val generatedNode = generateSingleNode(preparedSchema, config.schemaPropertyGenerationMode)
            generatedNodes.add(generatedNode)
        }

        val resultNode: JsonNode = if (generatedNodes.size == 1) {
            generatedNodes.first()
        } else {
            val resultArrayNode = objectMapper.createArrayNode()
            generatedNodes.forEach { generatedNode -> resultArrayNode.add(generatedNode) }
            resultArrayNode
        }

        val generatedJsonString = objectMapper.writerWithDefaultPrettyPrinter()
            .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(resultNode)

        return if (config.isJson5) {
            randomJsonDataCreator.applyJson5Features(generatedJsonString)
        } else {
            generatedJsonString
        }
    }

    private fun generateSingleNode(
        preparedSchema: PreparedSchema,
        schemaPropertyGenerationMode: SchemaPropertyGenerationMode
    ): JsonNode {
        return try {
            val generatedNode = valueGenerator.generateValue(preparedSchema.rootConstraint, schemaPropertyGenerationMode)
            val instanceValidationResult = validationService.validateInstance(preparedSchema.compiledSchema, generatedNode)
            if (!instanceValidationResult.isValid) {
                throw JsonSchemaGenerationException(
                    message = instanceValidationResult.errorMessage ?: "Generated data failed schema validation.",
                    jsonPointer = instanceValidationResult.jsonPointer
                )
            }
            generatedNode
        } catch (generationException: Exception) {
            LOG.warn("Primary generation failed. Trying fallback strategy.", generationException)
            generateFallbackNode(preparedSchema, generationException)
        }
    }

    private fun generateCommentedSchemaOutput(preparedSchema: PreparedSchema, schemaOutputCount: Int): String {
        val commentedSchemaNodes = mutableListOf<CommentedSchemaNodes>()
        repeat(schemaOutputCount) {
            val activeNode = generateSingleNode(preparedSchema, SchemaPropertyGenerationMode.REQUIRED_ONLY)
            val completeNode = generateCompleteNodeForCommentedMode(preparedSchema, activeNode)
            val mergedCompleteNode = mergeActiveValuesIntoCompleteNode(activeNode, completeNode)
            commentedSchemaNodes.add(
                CommentedSchemaNodes(
                    activeNode = activeNode,
                    completeNode = mergedCompleteNode
                )
            )
        }

        if (commentedSchemaNodes.size == 1) {
            return renderCommentedJson5(
                rootConstraint = preparedSchema.rootConstraint,
                activeNode = commentedSchemaNodes.first().activeNode,
                completeNode = commentedSchemaNodes.first().completeNode
            )
        }

        val arrayLines = mutableListOf<String>()
        arrayLines.add("[")
        commentedSchemaNodes.forEachIndexed { index, commentedSchemaNode ->
            val renderedNodeLines = renderCommentedJson5(
                rootConstraint = preparedSchema.rootConstraint,
                activeNode = commentedSchemaNode.activeNode,
                completeNode = commentedSchemaNode.completeNode
            ).lines().map { renderedLine -> "$INDENT_UNIT$renderedLine" }.toMutableList()

            if (index < commentedSchemaNodes.lastIndex && renderedNodeLines.isNotEmpty()) {
                val lastLineIndex = renderedNodeLines.lastIndex
                renderedNodeLines[lastLineIndex] = renderedNodeLines[lastLineIndex] + ","
            }
            arrayLines.addAll(renderedNodeLines)
        }
        arrayLines.add("]")
        return arrayLines.joinToString("\n")
    }

    private fun generateCompleteNodeForCommentedMode(preparedSchema: PreparedSchema, activeNode: JsonNode): JsonNode {
        val generatedCompleteNode = runCatching {
            generateSingleNode(preparedSchema, SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL)
        }.recoverCatching {
            valueGenerator.generateValue(
                preparedSchema.rootConstraint,
                SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL
            )
        }.getOrElse {
            buildMinimalValueCandidate(preparedSchema.resolvedSchemaNode, depth = 0)
        }

        if (!generatedCompleteNode.isObject || !activeNode.isObject) {
            return generatedCompleteNode
        }

        return mergeActiveValuesIntoCompleteNode(activeNode, generatedCompleteNode)
    }

    private fun mergeActiveValuesIntoCompleteNode(activeNode: JsonNode, completeNode: JsonNode): JsonNode {
        if (activeNode.isObject && completeNode.isObject) {
            val activeObjectNode = activeNode as ObjectNode
            val completeObjectNode = completeNode.deepCopy<ObjectNode>()
            activeObjectNode.properties().forEach { activeField ->
                val mergedChildNode = if (completeObjectNode.has(activeField.key)) {
                    mergeActiveValuesIntoCompleteNode(activeField.value, completeObjectNode.path(activeField.key))
                } else {
                    activeField.value.deepCopy()
                }
                completeObjectNode.set<JsonNode>(activeField.key, mergedChildNode)
            }
            return completeObjectNode
        }

        if (activeNode.isArray && completeNode.isArray) {
            val activeArrayNode = activeNode as ArrayNode
            val completeArrayNode = completeNode.deepCopy<ArrayNode>()
            val overlappingSize = minOf(activeArrayNode.size(), completeArrayNode.size())
            for (index in 0 until overlappingSize) {
                val mergedChildNode = mergeActiveValuesIntoCompleteNode(activeArrayNode[index], completeArrayNode[index])
                completeArrayNode.set(index, mergedChildNode)
            }
            return completeArrayNode
        }

        return activeNode.deepCopy()
    }

    private fun renderCommentedJson5(
        rootConstraint: JsonSchemaConstraint,
        activeNode: JsonNode,
        completeNode: JsonNode
    ): String {
        val renderedLines = renderNodeWithOptionalComments(
            activeNode = activeNode,
            completeNode = completeNode,
            constraint = rootConstraint,
            indentDepth = 0
        )
        return renderedLines.joinToString("\n")
    }

    private fun renderNodeWithOptionalComments(
        activeNode: JsonNode,
        completeNode: JsonNode,
        constraint: JsonSchemaConstraint?,
        indentDepth: Int
    ): List<String> {
        val objectConstraint = resolveObjectConstraint(constraint)
        if (objectConstraint != null && activeNode.isObject && completeNode.isObject) {
            return renderObjectWithOptionalComments(
                objectConstraint = objectConstraint,
                activeObjectNode = activeNode as ObjectNode,
                completeObjectNode = completeNode as ObjectNode,
                indentDepth = indentDepth
            )
        }
        if (activeNode.isObject && completeNode.isObject) {
            return renderObjectWithNodeDifference(
                activeObjectNode = activeNode as ObjectNode,
                completeObjectNode = completeNode as ObjectNode,
                indentDepth = indentDepth
            )
        }

        val arrayConstraint = resolveArrayConstraint(constraint)
        if (arrayConstraint != null && activeNode.isArray && completeNode.isArray) {
            return renderArrayWithOptionalComments(
                arrayConstraint = arrayConstraint,
                activeArrayNode = activeNode as ArrayNode,
                completeArrayNode = completeNode as ArrayNode,
                indentDepth = indentDepth
            )
        }

        return renderPlainNode(activeNode, indentDepth)
    }

    private fun renderObjectWithOptionalComments(
        objectConstraint: ObjectSchemaConstraint,
        activeObjectNode: ObjectNode,
        completeObjectNode: ObjectNode,
        indentDepth: Int
    ): List<String> {
        val lines = mutableListOf<String>()
        val currentIndent = indent(indentDepth)
        lines.add("$currentIndent{")

        val activePropertyNames = activeObjectNode.fieldNames().asSequence().toList()
        activePropertyNames.forEachIndexed { index, propertyName ->
            val propertyConstraint = objectConstraint.propertyConstraints[propertyName]
            val activePropertyNode = activeObjectNode.path(propertyName)
            val completePropertyNode = completeObjectNode.path(propertyName).takeUnless { it.isMissingNode } ?: activePropertyNode
            val renderedPropertyValueLines = renderNodeWithOptionalComments(
                activeNode = activePropertyNode,
                completeNode = completePropertyNode,
                constraint = propertyConstraint,
                indentDepth = indentDepth + 1
            )
            val propertyLines = buildPropertyLines(
                propertyName = propertyName,
                renderedPropertyValueLines = renderedPropertyValueLines,
                indentDepth = indentDepth + 1,
                hasTrailingComma = index < activePropertyNames.lastIndex
            )
            lines.addAll(propertyLines)
        }

        val optionalCommentPropertyNames = objectConstraint.propertyConstraints.keys
            .filter { propertyName ->
                !objectConstraint.requiredProperties.contains(propertyName) &&
                    !activeObjectNode.has(propertyName)
            }

        optionalCommentPropertyNames.forEach { optionalPropertyName ->
            val optionalPropertyConstraint = objectConstraint.propertyConstraints[optionalPropertyName]
            val optionalPropertyNode = completeObjectNode.path(optionalPropertyName)
                .takeUnless { it.isMissingNode }
                ?: buildCommentPreviewNode(optionalPropertyConstraint)
            val renderedOptionalPropertyValueLines = renderNodeWithOptionalComments(
                activeNode = optionalPropertyNode,
                completeNode = optionalPropertyNode,
                constraint = optionalPropertyConstraint,
                indentDepth = indentDepth + 1
            )
            val optionalPropertyLines = buildPropertyLines(
                propertyName = optionalPropertyName,
                renderedPropertyValueLines = renderedOptionalPropertyValueLines,
                indentDepth = indentDepth + 1,
                hasTrailingComma = true
            )
            lines.addAll(convertToCommentedLines(optionalPropertyLines, indentDepth + 1))
        }

        lines.add("$currentIndent}")
        return lines
    }

    private fun renderObjectWithNodeDifference(
        activeObjectNode: ObjectNode,
        completeObjectNode: ObjectNode,
        indentDepth: Int
    ): List<String> {
        val lines = mutableListOf<String>()
        val currentIndent = indent(indentDepth)
        lines.add("$currentIndent{")

        val activePropertyNames = activeObjectNode.fieldNames().asSequence().toList()
        activePropertyNames.forEachIndexed { index, propertyName ->
            val activePropertyNode = activeObjectNode.path(propertyName)
            val completePropertyNode = completeObjectNode.path(propertyName).takeUnless { it.isMissingNode } ?: activePropertyNode
            val renderedPropertyValueLines = renderNodeWithOptionalComments(
                activeNode = activePropertyNode,
                completeNode = completePropertyNode,
                constraint = null,
                indentDepth = indentDepth + 1
            )
            val propertyLines = buildPropertyLines(
                propertyName = propertyName,
                renderedPropertyValueLines = renderedPropertyValueLines,
                indentDepth = indentDepth + 1,
                hasTrailingComma = index < activePropertyNames.lastIndex
            )
            lines.addAll(propertyLines)
        }

        val optionalCommentPropertyNames = completeObjectNode.fieldNames().asSequence().toList()
            .filter { propertyName -> !activeObjectNode.has(propertyName) }
        optionalCommentPropertyNames.forEach { optionalPropertyName ->
            val optionalPropertyNode = completeObjectNode.path(optionalPropertyName)
            val renderedOptionalPropertyValueLines = renderNodeWithOptionalComments(
                activeNode = optionalPropertyNode,
                completeNode = optionalPropertyNode,
                constraint = null,
                indentDepth = indentDepth + 1
            )
            val optionalPropertyLines = buildPropertyLines(
                propertyName = optionalPropertyName,
                renderedPropertyValueLines = renderedOptionalPropertyValueLines,
                indentDepth = indentDepth + 1,
                hasTrailingComma = true
            )
            lines.addAll(convertToCommentedLines(optionalPropertyLines, indentDepth + 1))
        }

        lines.add("$currentIndent}")
        return lines
    }

    private fun buildCommentPreviewNode(optionalPropertyConstraint: JsonSchemaConstraint?): JsonNode {
        if (optionalPropertyConstraint == null) {
            return TextNode("value")
        }

        return runCatching {
            valueGenerator.generateValue(
                optionalPropertyConstraint,
                SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL
            )
        }.getOrElse {
            buildMinimalValueCandidate(optionalPropertyConstraint.schemaNode, depth = 0)
        }
    }

    private fun renderArrayWithOptionalComments(
        arrayConstraint: ArraySchemaConstraint,
        activeArrayNode: ArrayNode,
        completeArrayNode: ArrayNode,
        indentDepth: Int
    ): List<String> {
        if (activeArrayNode.isEmpty) {
            return listOf("${indent(indentDepth)}[]")
        }

        val lines = mutableListOf<String>()
        val currentIndent = indent(indentDepth)
        lines.add("$currentIndent[")
        for (index in 0 until activeArrayNode.size()) {
            val elementConstraint = arrayConstraint.prefixItemConstraints.getOrNull(index) ?: arrayConstraint.itemConstraint
            val activeElementNode = activeArrayNode[index]
            val completeElementNode = completeArrayNode.path(index).takeUnless { it.isMissingNode } ?: activeElementNode
            val renderedElementLines = renderNodeWithOptionalComments(
                activeNode = activeElementNode,
                completeNode = completeElementNode,
                constraint = elementConstraint,
                indentDepth = indentDepth + 1
            ).toMutableList()
            if (index < activeArrayNode.size() - 1 && renderedElementLines.isNotEmpty()) {
                val lastElementLineIndex = renderedElementLines.lastIndex
                renderedElementLines[lastElementLineIndex] = renderedElementLines[lastElementLineIndex] + ","
            }
            lines.addAll(renderedElementLines)
        }
        lines.add("$currentIndent]")
        return lines
    }

    private fun renderPlainNode(node: JsonNode, indentDepth: Int): List<String> {
        val serializedNode = if (node.isContainerNode) {
            objectMapper.writerWithDefaultPrettyPrinter()
                .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writeValueAsString(node)
        } else {
            objectMapper.writeValueAsString(node)
        }

        val baseIndent = indent(indentDepth)
        return serializedNode.lines().map { serializedLine -> "$baseIndent$serializedLine" }
    }

    private fun resolveObjectConstraint(constraint: JsonSchemaConstraint?): ObjectSchemaConstraint? {
        return when (constraint) {
            is ObjectSchemaConstraint -> constraint
            is CompositeSchemaConstraint -> {
                val allOfObjectConstraints = constraint.allOfConstraints.mapNotNull { allOfConstraint ->
                    resolveObjectConstraint(allOfConstraint)
                }
                if (allOfObjectConstraints.isNotEmpty()) {
                    mergeObjectConstraints(constraint, allOfObjectConstraints)
                } else {
                    resolveObjectConstraint(constraint.baseConstraint)
                        ?: findFirstObjectConstraint(constraint.anyOfConstraints)
                        ?: findFirstObjectConstraint(constraint.oneOfConstraints)
                }
            }
            else -> null
        }
    }

    private fun findFirstObjectConstraint(constraints: List<JsonSchemaConstraint>): ObjectSchemaConstraint? {
        constraints.forEach { candidateConstraint ->
            val resolvedObjectConstraint = resolveObjectConstraint(candidateConstraint)
            if (resolvedObjectConstraint != null) {
                return resolvedObjectConstraint
            }
        }
        return null
    }

    private fun mergeObjectConstraints(
        parentConstraint: CompositeSchemaConstraint,
        allOfObjectConstraints: List<ObjectSchemaConstraint>
    ): ObjectSchemaConstraint {
        val mergedPropertyConstraints = linkedMapOf<String, JsonSchemaConstraint>()
        val mergedRequiredProperties = linkedSetOf<String>()
        val mergedPatternPropertyConstraints = linkedMapOf<String, JsonSchemaConstraint>()
        var mergedAdditionalPropertiesConstraint: JsonSchemaConstraint? = null
        var allowsAdditionalProperties = true
        var mergedUnevaluatedPropertiesConstraint: JsonSchemaConstraint? = null
        var allowsUnevaluatedProperties = true
        val mergedDependentRequiredProperties = linkedMapOf<String, MutableSet<String>>()
        val mergedDependentSchemaConstraints = linkedMapOf<String, JsonSchemaConstraint>()
        var mergedMinimumProperties: Int? = null
        var mergedMaximumProperties: Int? = null

        allOfObjectConstraints.forEach { allOfObjectConstraint ->
            mergedPropertyConstraints.putAll(allOfObjectConstraint.propertyConstraints)
            mergedRequiredProperties.addAll(allOfObjectConstraint.requiredProperties)
            mergedPatternPropertyConstraints.putAll(allOfObjectConstraint.patternPropertyConstraints)

            mergedAdditionalPropertiesConstraint =
                allOfObjectConstraint.additionalPropertiesConstraint ?: mergedAdditionalPropertiesConstraint
            allowsAdditionalProperties = allowsAdditionalProperties && allOfObjectConstraint.allowsAdditionalProperties

            mergedUnevaluatedPropertiesConstraint =
                allOfObjectConstraint.unevaluatedPropertiesConstraint ?: mergedUnevaluatedPropertiesConstraint
            allowsUnevaluatedProperties = allowsUnevaluatedProperties && allOfObjectConstraint.allowsUnevaluatedProperties

            allOfObjectConstraint.dependentRequiredProperties.forEach { (triggerPropertyName, dependentPropertyNames) ->
                val existingDependentPropertyNames = mergedDependentRequiredProperties.getOrPut(triggerPropertyName) {
                    linkedSetOf()
                }
                existingDependentPropertyNames.addAll(dependentPropertyNames)
            }
            mergedDependentSchemaConstraints.putAll(allOfObjectConstraint.dependentSchemaConstraints)

            val minimumProperties = allOfObjectConstraint.minimumProperties
            if (minimumProperties != null) {
                mergedMinimumProperties = mergedMinimumProperties?.let { maxOf(it, minimumProperties) } ?: minimumProperties
            }
            val maximumProperties = allOfObjectConstraint.maximumProperties
            if (maximumProperties != null) {
                mergedMaximumProperties = mergedMaximumProperties?.let { minOf(it, maximumProperties) } ?: maximumProperties
            }
        }

        return ObjectSchemaConstraint(
            jsonPointer = parentConstraint.jsonPointer,
            schemaNode = parentConstraint.schemaNode,
            propertyConstraints = mergedPropertyConstraints,
            requiredProperties = mergedRequiredProperties,
            patternPropertyConstraints = mergedPatternPropertyConstraints,
            additionalPropertiesConstraint = mergedAdditionalPropertiesConstraint,
            allowsAdditionalProperties = allowsAdditionalProperties,
            unevaluatedPropertiesConstraint = mergedUnevaluatedPropertiesConstraint,
            allowsUnevaluatedProperties = allowsUnevaluatedProperties,
            dependentRequiredProperties = mergedDependentRequiredProperties.mapValues { (_, dependentPropertyNames) ->
                dependentPropertyNames.toSet()
            },
            dependentSchemaConstraints = mergedDependentSchemaConstraints,
            minimumProperties = mergedMinimumProperties,
            maximumProperties = mergedMaximumProperties
        )
    }

    private fun resolveArrayConstraint(constraint: JsonSchemaConstraint?): ArraySchemaConstraint? {
        return when (constraint) {
            is ArraySchemaConstraint -> constraint
            is CompositeSchemaConstraint -> constraint.baseConstraint as? ArraySchemaConstraint
            else -> null
        }
    }

    private fun buildPropertyLines(
        propertyName: String,
        renderedPropertyValueLines: List<String>,
        indentDepth: Int,
        hasTrailingComma: Boolean
    ): List<String> {
        if (renderedPropertyValueLines.isEmpty()) {
            return emptyList()
        }

        val propertyIndent = indent(indentDepth)
        val normalizedValueLines = renderedPropertyValueLines.map { renderedValueLine ->
            renderedValueLine.removePrefix(propertyIndent)
        }
        val escapedPropertyName = propertyName
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        val propertyLines = mutableListOf<String>()
        propertyLines.add("$propertyIndent\"$escapedPropertyName\": ${normalizedValueLines.first()}")
        normalizedValueLines.drop(1).forEach { normalizedValueLine ->
            propertyLines.add("$propertyIndent$normalizedValueLine")
        }

        if (hasTrailingComma) {
            val lastLineIndex = propertyLines.lastIndex
            propertyLines[lastLineIndex] = propertyLines[lastLineIndex] + ","
        }
        return propertyLines
    }

    private fun convertToCommentedLines(propertyLines: List<String>, indentDepth: Int): List<String> {
        val propertyIndent = indent(indentDepth)
        return propertyLines.map { propertyLine ->
            val normalizedPropertyLine = propertyLine.removePrefix(propertyIndent)
            "$propertyIndent// $normalizedPropertyLine"
        }
    }

    private fun indent(indentDepth: Int): String {
        return INDENT_UNIT.repeat(indentDepth)
    }

    private fun generateFallbackNode(preparedSchema: PreparedSchema, cause: Exception): JsonNode {
        val fallbackCandidates = buildFallbackCandidates(preparedSchema.resolvedSchemaNode)
        for (candidateNode in fallbackCandidates) {
            val instanceValidationResult = validationService.validateInstance(preparedSchema.compiledSchema, candidateNode)
            if (instanceValidationResult.isValid) {
                LOG.warn("Fallback generation succeeded with minimal candidate.")
                return candidateNode
            }
        }

        LOG.error("Fallback generation failed. No schema-compliant candidate was found.", cause)
        throw JsonSchemaGenerationException(
            message = "Unable to generate schema-compliant data.",
            jsonPointer = "#",
            cause = cause
        )
    }

    private fun buildFallbackCandidates(schemaNode: JsonNode): List<JsonNode> {
        val candidates = mutableListOf<JsonNode>()
        val objectMapper = validationService.getStrictObjectMapper()

        if (schemaNode.has("const")) {
            candidates.add(schemaNode.path("const").deepCopy())
        }
        if (schemaNode.path("enum").isArray) {
            schemaNode.path("enum").forEach { enumNode ->
                candidates.add(enumNode.deepCopy())
            }
        }
        if (schemaNode.has("default")) {
            candidates.add(schemaNode.path("default").deepCopy())
        }
        if (schemaNode.path("examples").isArray) {
            schemaNode.path("examples").forEach { exampleNode ->
                candidates.add(exampleNode.deepCopy())
            }
        }

        val typeNames = extractTypeNames(schemaNode)
        if (typeNames.isEmpty()) {
            candidates.add(buildMinimalObjectCandidate(schemaNode, depth = 0))
            candidates.add(objectMapper.createArrayNode())
            candidates.add(TextNode("value"))
            candidates.add(objectMapper.nodeFactory.numberNode(0))
            candidates.add(objectMapper.nodeFactory.numberNode(1))
            candidates.add(objectMapper.nodeFactory.booleanNode(false))
            candidates.add(objectMapper.nodeFactory.booleanNode(true))
            candidates.add(NullNode.instance)
            return candidates
        }

        typeNames.forEach { typeName ->
            when (typeName) {
                "object" -> candidates.add(buildMinimalObjectCandidate(schemaNode, depth = 0))
                "array" -> candidates.add(objectMapper.createArrayNode())
                "string" -> {
                    candidates.add(TextNode(""))
                    candidates.add(TextNode("value"))
                }
                "integer" -> {
                    candidates.add(objectMapper.nodeFactory.numberNode(0))
                    candidates.add(objectMapper.nodeFactory.numberNode(1))
                }
                "number" -> {
                    candidates.add(objectMapper.nodeFactory.numberNode(0))
                    candidates.add(objectMapper.nodeFactory.numberNode(0.1))
                }
                "boolean" -> {
                    candidates.add(objectMapper.nodeFactory.booleanNode(false))
                    candidates.add(objectMapper.nodeFactory.booleanNode(true))
                }
                "null" -> candidates.add(NullNode.instance)
            }
        }
        return candidates
    }

    private fun buildMinimalObjectCandidate(schemaNode: JsonNode, depth: Int): JsonNode {
        val objectMapper = validationService.getStrictObjectMapper()
        val objectNode = objectMapper.createObjectNode()
        if (depth > 2) {
            return objectNode
        }

        val propertiesNode = schemaNode.path("properties")
        val requiredNames = schemaNode.path("required")
            .takeIf { it.isArray }
            ?.mapNotNull { requiredNameNode ->
                requiredNameNode.takeIf { it.isTextual }?.asText()
            }
            ?: emptyList()

        requiredNames.forEach { requiredName ->
            val propertySchemaNode = propertiesNode.path(requiredName)
            objectNode.set<JsonNode>(requiredName, buildMinimalValueCandidate(propertySchemaNode, depth + 1))
        }

        return objectNode
    }

    private fun buildMinimalValueCandidate(schemaNode: JsonNode, depth: Int): JsonNode {
        val objectMapper = validationService.getStrictObjectMapper()

        if (schemaNode.has("const")) {
            return schemaNode.path("const").deepCopy()
        }
        if (schemaNode.path("enum").isArray && schemaNode.path("enum").size() > 0) {
            return schemaNode.path("enum").first().deepCopy()
        }

        val typeNames = extractTypeNames(schemaNode)
        val primaryTypeName = typeNames.firstOrNull()

        return when (primaryTypeName) {
            "object" -> buildMinimalObjectCandidate(schemaNode, depth)
            "array" -> objectMapper.createArrayNode()
            "string" -> TextNode("value")
            "integer" -> objectMapper.nodeFactory.numberNode(0)
            "number" -> objectMapper.nodeFactory.numberNode(0)
            "boolean" -> objectMapper.nodeFactory.booleanNode(false)
            "null" -> NullNode.instance
            else -> TextNode("value")
        }
    }

    private fun extractTypeNames(schemaNode: JsonNode): List<String> {
        val typeNode = schemaNode.path("type")
        if (typeNode.isTextual) {
            return listOf(typeNode.asText())
        }
        if (typeNode.isArray) {
            return typeNode
                .mapNotNull { typeElementNode ->
                    typeElementNode.takeIf { it.isTextual }?.asText()
                }
        }
        return emptyList()
    }

    companion object {
        private const val INDENT_UNIT = "  "
    }
}
