package com.livteam.jsoninja.services.schema

class JsonSchemaGenerationException(
    message: String,
    val jsonPointer: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
