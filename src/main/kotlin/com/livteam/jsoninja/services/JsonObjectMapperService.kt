package com.livteam.jsoninja.services

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.components.Service

/**
 * Service for providing a shared and configured ObjectMapper instance.
 */
@Service(Service.Level.APP)
class JsonObjectMapperService {
    val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .apply {
        // Serialization settings
        configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        
        // Deserialization settings
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
        
        // Parser settings
        configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)

        // JSON5 support
        configure(JsonParser.Feature.ALLOW_COMMENTS, true)
        configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
        configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
    }
}
