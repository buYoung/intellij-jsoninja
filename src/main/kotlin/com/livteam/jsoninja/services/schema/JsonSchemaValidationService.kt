package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage

@Service(Service.Level.PROJECT)
class JsonSchemaValidationService(private val project: Project) {
    private val strictObjectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .apply {
            configure(JsonParser.Feature.ALLOW_COMMENTS, false)
            configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, false)
            configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, false)
            configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, false)
        }

    private val schemaFactory: JsonSchemaFactory =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    data class SchemaValidationResult(
        val isValid: Boolean,
        val compiledSchema: JsonSchema? = null,
        val errorMessage: String? = null,
        val jsonPointer: String? = null,
        val schemaNode: JsonNode? = null
    )

    data class InstanceValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val jsonPointer: String? = null,
        val validationMessages: List<String> = emptyList()
    )

    fun parseStrictSchema(schemaText: String): JsonNode {
        try {
            val parsedSchemaNode = strictObjectMapper.readTree(schemaText)
                ?: throw JsonSchemaGenerationException("Schema text is empty.")
            if (!parsedSchemaNode.isObject && !parsedSchemaNode.isBoolean) {
                throw JsonSchemaGenerationException("Schema root must be an object or boolean schema.", "#")
            }
            return parsedSchemaNode
        } catch (generationException: JsonSchemaGenerationException) {
            throw generationException
        } catch (exception: Exception) {
            throw JsonSchemaGenerationException(
                message = "Schema must be strict JSON: ${exception.message}",
                jsonPointer = "#",
                cause = exception
            )
        }
    }

    fun compileSchema(schemaNode: JsonNode): JsonSchema {
        try {
            return schemaFactory.getSchema(schemaNode)
        } catch (exception: Exception) {
            throw JsonSchemaGenerationException(
                message = "Failed to compile JSON Schema (2020-12): ${exception.message}",
                jsonPointer = "#",
                cause = exception
            )
        }
    }

    fun validateSchema(schemaNode: JsonNode): SchemaValidationResult {
        return try {
            val compiledSchema = compileSchema(schemaNode)
            SchemaValidationResult(
                isValid = true,
                compiledSchema = compiledSchema,
                schemaNode = schemaNode
            )
        } catch (exception: JsonSchemaGenerationException) {
            SchemaValidationResult(
                isValid = false,
                errorMessage = exception.message,
                jsonPointer = exception.jsonPointer,
                schemaNode = schemaNode
            )
        }
    }

    fun validateInstance(compiledSchema: JsonSchema, instanceNode: JsonNode): InstanceValidationResult {
        return try {
            val validationMessageSet = compiledSchema.validate(instanceNode)
            if (validationMessageSet.isEmpty()) {
                InstanceValidationResult(isValid = true)
            } else {
                val validationMessageList = validationMessageSet.map { validationMessage ->
                    resolveValidationMessage(validationMessage)
                }
                val firstValidationMessage = validationMessageSet.first()
                InstanceValidationResult(
                    isValid = false,
                    errorMessage = validationMessageList.firstOrNull(),
                    jsonPointer = resolveValidationPointer(firstValidationMessage),
                    validationMessages = validationMessageList
                )
            }
        } catch (exception: Exception) {
            InstanceValidationResult(
                isValid = false,
                errorMessage = exception.message ?: "Unknown validation failure",
                jsonPointer = "#"
            )
        }
    }

    fun validateAgainstSchemaNode(schemaNode: JsonNode, instanceNode: JsonNode): Boolean {
        val compiledSchema = compileSchema(schemaNode)
        return validateInstance(compiledSchema, instanceNode).isValid
    }

    fun getStrictObjectMapper(): ObjectMapper = strictObjectMapper

    private fun resolveValidationMessage(validationMessage: ValidationMessage): String {
        val messageText = runCatching { validationMessage.message }.getOrNull()
        if (!messageText.isNullOrBlank()) {
            return messageText
        }

        return validationMessage.toString()
    }

    private fun resolveValidationPointer(validationMessage: ValidationMessage): String? {
        val messageText = validationMessage.toString()
        val atIndex = messageText.indexOf(" at ")
        if (atIndex >= 0 && atIndex + 4 < messageText.length) {
            return messageText.substring(atIndex + 4).trim()
        }
        return "#"
    }
}
