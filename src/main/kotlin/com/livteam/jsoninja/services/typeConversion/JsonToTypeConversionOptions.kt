package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.NamingConvention
import com.livteam.jsoninja.model.SupportedLanguage

enum class JsonToTypeAnnotationStyle(
    private val displayNameKey: String,
) {
    NONE("dialog.json.to.type.annotation.none"),
    GSON_SERIALIZED_NAME("dialog.json.to.type.annotation.gson"),
    JACKSON_JSON_PROPERTY("dialog.json.to.type.annotation.jackson"),
    KOTLIN_SERIAL_NAME("dialog.json.to.type.annotation.kotlinx"),
    GO_JSON_TAG("dialog.json.to.type.annotation.go");

    val displayName: String
        get() = LocalizationBundle.message(displayNameKey)

    fun isSupported(language: SupportedLanguage): Boolean {
        return when (this) {
            NONE -> true
            GSON_SERIALIZED_NAME -> language == SupportedLanguage.JAVA
            JACKSON_JSON_PROPERTY -> {
                language == SupportedLanguage.JAVA || language == SupportedLanguage.KOTLIN
            }
            KOTLIN_SERIAL_NAME -> language == SupportedLanguage.KOTLIN
            GO_JSON_TAG -> language == SupportedLanguage.GO
        }
    }

    companion object {
        fun availableValues(language: SupportedLanguage): List<JsonToTypeAnnotationStyle> {
            return entries.filter { it.isSupported(language) }
        }

        fun fromNameOrDefault(
            name: String?,
            language: SupportedLanguage,
        ): JsonToTypeAnnotationStyle {
            val resolvedStyle = entries.firstOrNull { it.name == name } ?: NONE
            return if (resolvedStyle.isSupported(language)) {
                resolvedStyle
            } else {
                availableValues(language).first()
            }
        }
    }
}

data class JsonToTypeConversionOptions(
    val rootTypeName: String = "Root",
    val namingConvention: NamingConvention = NamingConvention.CAMEL_CASE,
    val annotationStyle: JsonToTypeAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
    val allowsNullableFields: Boolean = true,
    val maximumDepth: Int = 10,
)
