package com.livteam.jsoninja.model

enum class JsonQueryType {
    JAYWAY_JSONPATH,
    JMESPATH;

    companion object {
        fun fromString(value: String): JsonQueryType {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                JAYWAY_JSONPATH
            }
        }
    }
}
