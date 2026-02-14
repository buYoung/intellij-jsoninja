package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
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
            val generatedNode = valueGenerator.generateValue(preparedSchema.rootConstraint)
            val instanceValidationResult = validationService.validateInstance(preparedSchema.compiledSchema, generatedNode)
            if (!instanceValidationResult.isValid) {
                throw JsonSchemaGenerationException(
                    message = instanceValidationResult.errorMessage ?: "Generated data failed schema validation.",
                    jsonPointer = instanceValidationResult.jsonPointer
                )
            }
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
}
