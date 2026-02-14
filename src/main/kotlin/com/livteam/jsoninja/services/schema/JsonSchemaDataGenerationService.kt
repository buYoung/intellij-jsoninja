package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.RandomJsonDataCreator
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
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

        val generatedNodes = mutableListOf<JsonNode>()
        repeat(config.schemaOutputCount) {
            val generatedNode = generateSingleNode(preparedSchema)
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

    private fun generateSingleNode(preparedSchema: PreparedSchema): JsonNode {
        return try {
            val generatedNode = valueGenerator.generateValue(preparedSchema.rootConstraint)
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
}
