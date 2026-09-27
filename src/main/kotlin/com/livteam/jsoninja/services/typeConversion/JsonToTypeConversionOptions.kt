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
    CSHARP_JSON_PROPERTY_NAME,
    ;

    override fun toString(): String = if (this == CSHARP_JSON_PROPERTY_NAME)
        com.livteam.jsoninja.LocalizationBundle.message("dialog.json.to.type.annotation.csharp") else name

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
