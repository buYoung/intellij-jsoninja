package com.livteam.jsoninja.services

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonFactoryBuilder
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.components.Service

/**
 * Service for providing shared and configured ObjectMapper instances.
 *
 * [standardJsonFactory] / [standardObjectMapper] parse standard RFC-8259 JSON (plus trailing commas)
 * directly without running [Json5InputNormalizer]; [objectMapper] uses [Json5Factory] for full JSON5 support.
 */
@Service(Service.Level.APP)
class JsonObjectMapperService {
    internal val standardJsonFactory: JsonFactory = JsonFactoryBuilder()
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .build()

    val standardObjectMapper: ObjectMapper = ObjectMapper(standardJsonFactory)
        .registerModule(KotlinModule.Builder().build())
        .apply {
            configureSharedFeatures()
        }

    val objectMapper: ObjectMapper = ObjectMapper(Json5Factory())
        .registerModule(KotlinModule.Builder().build())
        .apply {
            configureSharedFeatures()

            // JSON5 support
            configure(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(), true)
            configure(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(), true)
            configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), true)
        }

    /**
     * Parses [content] via [standardObjectMapper] first (zero JSON5 preprocessing overhead),
     * falling back to [objectMapper] ([Json5Factory]) only on [JsonProcessingException].
     */
    fun readTreeFast(content: String): JsonNode {
        return try {
            standardObjectMapper.readTree(content)
        } catch (_: JsonProcessingException) {
            objectMapper.readTree(content)
        }
    }

    /**
     * Validates that [content] contains exactly one complete JSON or JSON5 value with no trailing tokens.
     * Rejects empty, whitespace-only, BOM-only, and comment-only inputs.
     */
    internal fun isValidJsonTokenStream(content: String): Boolean {
        val standardResult = try {
            validateTokenStream(standardJsonFactory, content)
        } catch (_: JsonProcessingException) {
            null
        }
        if (standardResult != null) {
            return standardResult
        }

        return try {
            validateTokenStream(objectMapper.factory, content)
        } catch (_: JsonProcessingException) {
            false
        }
    }

    private fun validateTokenStream(factory: JsonFactory, content: String): Boolean {
        return factory.createParser(content).use { parser ->
            if (parser.nextToken() == null) return false
            parser.skipChildren()
            parser.nextToken() == null
        }
    }

    private fun ObjectMapper.configureSharedFeatures() {
        // Serialization settings
        configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

        // Deserialization settings
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
        configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)

        // Parser settings
        configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
    }
}
