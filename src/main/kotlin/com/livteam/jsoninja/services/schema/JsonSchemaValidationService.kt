package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SchemaRegistryConfig
import com.networknt.schema.SpecificationVersion
import com.networknt.schema.path.PathType
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import com.networknt.schema.Error as SchemaError

@Service(Service.Level.PROJECT)
class JsonSchemaValidationService(private val project: Project) : Disposable {
    private val lazyStrictObjectMapper: ObjectMapper by lazy {
        ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .apply {
                configure(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(), false)
                configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), false)
                configure(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(), false)
                configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), false)
            }
    }

    private val schemaRegistryConfig: SchemaRegistryConfig by lazy {
        SchemaRegistryConfig.builder().pathType(PathType.JSON_POINTER).build()
    }

    private val schemaRegistryByVersion = ConcurrentHashMap<SpecificationVersion, SchemaRegistry>()

    data class SchemaValidationResult(
        val isValid: Boolean,
        val compiledSchema: Schema? = null,
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
            val parsedSchemaNode = lazyStrictObjectMapper.readTree(schemaText)
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

    fun compileSchema(schemaNode: JsonNode): Schema {
        try {
            val version = JsonSchemaTraversal.dialect(schemaNode)
            return getSchemaRegistry(version).getSchema(JsonSchemaNodeAdapter.convert(schemaNode))
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: JsonSchemaGenerationException) {
            throw exception
        } catch (exception: Exception) {
            throw JsonSchemaGenerationException(
                message = "Failed to compile JSON Schema: ${exception.message}",
                jsonPointer = "#",
                cause = exception
            )
        }
    }

    private fun getSchemaRegistry(version: SpecificationVersion): SchemaRegistry {
        return schemaRegistryByVersion.computeIfAbsent(version) { targetVersion ->
            SchemaRegistry.withDefaultDialect(targetVersion) { builder ->
                builder.schemaRegistryConfig(schemaRegistryConfig)
                // JsonSchemaNormalizer resolves external references using our cache/timeouts.
                // Keep the validator's remote fetching disabled to avoid a second I/O path.
            }
        }
    }

    override fun dispose() {
        schemaRegistryByVersion.clear()
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

    fun validateInstance(compiledSchema: Schema, instanceNode: JsonNode): InstanceValidationResult {
        return try {
            val validationErrors = compiledSchema.validate(JsonSchemaNodeAdapter.convert(instanceNode))
            if (validationErrors.isEmpty()) {
                InstanceValidationResult(isValid = true)
            } else {
                val validationMessageList = validationErrors.map { validationMessage ->
                    resolveValidationMessage(validationMessage)
                }
                val firstValidationMessage = validationErrors.first()
                InstanceValidationResult(
                    isValid = false,
                    errorMessage = validationMessageList.firstOrNull(),
                    jsonPointer = resolveValidationPointer(firstValidationMessage),
                    validationMessages = validationMessageList
                )
            }
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (exception: CancellationException) {
            throw exception
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

    fun getStrictObjectMapper(): ObjectMapper = lazyStrictObjectMapper

    private fun resolveValidationMessage(validationMessage: SchemaError): String {
        val messageText = runCatching { validationMessage.message }.getOrNull()
        if (!messageText.isNullOrBlank()) {
            return messageText
        }

        return validationMessage.toString()
    }

    private fun resolveValidationPointer(validationMessage: SchemaError): String =
        "#${validationMessage.instanceLocation ?: ""}"
}
