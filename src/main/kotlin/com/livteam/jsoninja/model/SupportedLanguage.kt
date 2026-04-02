package com.livteam.jsoninja.model

import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.typeConversion.JsonToTypeAnnotationStyle
import com.livteam.jsoninja.services.typeConversion.NamingConvention

enum class SupportedLanguage(
    val displayNameKey: String,
    val fileExtension: String,
    val wasmLanguageId: Int,
    val defaultNamingConvention: NamingConvention,
    val defaultAnnotationStyle: JsonToTypeAnnotationStyle,
) {
    KOTLIN(
        displayNameKey = "language.kotlin",
        fileExtension = "kt",
        wasmLanguageId = 1,
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY,
    ),
    JAVA(
        displayNameKey = "language.java",
        fileExtension = "java",
        wasmLanguageId = 0,
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY,
    ),
    TYPESCRIPT(
        displayNameKey = "language.typescript",
        fileExtension = "ts",
        wasmLanguageId = 2,
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
    ),
    GO(
        displayNameKey = "language.go",
        fileExtension = "go",
        wasmLanguageId = 3,
        defaultNamingConvention = NamingConvention.PASCAL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.GO_JSON_TAG,
    ),
    ;

    fun getDisplayName(): String = LocalizationBundle.message(displayNameKey)

    companion object {
        fun fromPersistedValue(value: String?): SupportedLanguage {
            return entries.firstOrNull { it.name == value } ?: KOTLIN
        }
    }
}
