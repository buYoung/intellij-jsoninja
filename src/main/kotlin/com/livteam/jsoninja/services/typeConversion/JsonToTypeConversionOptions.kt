package com.livteam.jsoninja.services.typeConversion

enum class NamingConvention {
    CAMEL_CASE,
    PASCAL_CASE,
    SNAKE_CASE,
    ;

    companion object {
        fun fromPersistedValue(value: String?): NamingConvention? {
            return entries.firstOrNull { it.name == value }
        }
    }
}

enum class JsonToTypeAnnotationStyle {
    NONE,
    GSON_SERIALIZED_NAME,
    JACKSON_JSON_PROPERTY,
    KOTLIN_SERIAL_NAME,
    GO_JSON_TAG,
    ;

    companion object {
        fun fromPersistedValue(value: String?): JsonToTypeAnnotationStyle? {
            return entries.firstOrNull { it.name == value }
        }
    }
}

data class JsonToTypeConversionOptions(
    val rootTypeName: String = "Root",
    val namingConvention: NamingConvention,
    val annotationStyle: JsonToTypeAnnotationStyle,
    val allowsNullableFields: Boolean = true,
    val usesExperimentalGoUnionTypes: Boolean = false,
    val maximumDepth: Int = 10,
)
