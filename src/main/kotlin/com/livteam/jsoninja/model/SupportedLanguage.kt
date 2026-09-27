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
    val availableNamingConventions: List<NamingConvention>,
    val availableAnnotationStyles: List<JsonToTypeAnnotationStyle>,
    val showsAnalysisWarnings: Boolean = false,
    val usesOriginalJsonFieldNames: Boolean = false,
) {
    KOTLIN(
        displayNameKey = "language.kotlin",
        fileExtension = "kt",
        wasmLanguageId = 1,
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
        availableNamingConventions = NamingConvention.entries,
        availableAnnotationStyles = listOf(
            JsonToTypeAnnotationStyle.NONE,
            JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY,
            JsonToTypeAnnotationStyle.KOTLIN_SERIAL_NAME,
        ),
    ),
    JAVA(
        displayNameKey = "language.java",
        fileExtension = "java",
        wasmLanguageId = 0,
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
        availableNamingConventions = NamingConvention.entries,
        availableAnnotationStyles = listOf(
            JsonToTypeAnnotationStyle.NONE,
            JsonToTypeAnnotationStyle.GSON_SERIALIZED_NAME,
            JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY,
        ),
    ),
    TYPESCRIPT(
        displayNameKey = "language.typescript",
        fileExtension = "ts",
        wasmLanguageId = 2,
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
        availableNamingConventions = NamingConvention.entries,
        availableAnnotationStyles = listOf(JsonToTypeAnnotationStyle.NONE),
    ),
    GO(
        displayNameKey = "language.go",
        fileExtension = "go",
        wasmLanguageId = 3,
        defaultNamingConvention = NamingConvention.PASCAL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.GO_JSON_TAG,
        availableNamingConventions = listOf(NamingConvention.PASCAL_CASE),
        availableAnnotationStyles = listOf(
            JsonToTypeAnnotationStyle.NONE,
            JsonToTypeAnnotationStyle.GO_JSON_TAG,
        ),
    ),
    C(
        displayNameKey = "language.c",
        fileExtension = "c",
        wasmLanguageId = 4,
        defaultNamingConvention = NamingConvention.SNAKE_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
        availableNamingConventions = NamingConvention.entries,
        availableAnnotationStyles = listOf(JsonToTypeAnnotationStyle.NONE),
        showsAnalysisWarnings = true,
    ),
    CPP(
        displayNameKey = "language.cpp",
        fileExtension = "cpp",
        wasmLanguageId = 5,
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
        availableNamingConventions = NamingConvention.entries,
        availableAnnotationStyles = listOf(JsonToTypeAnnotationStyle.NONE),
        showsAnalysisWarnings = true,
    ),
    CSHARP(
        displayNameKey = "language.csharp",
        fileExtension = "cs",
        wasmLanguageId = 6,
        defaultNamingConvention = NamingConvention.PASCAL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.CSHARP_JSON_PROPERTY_NAME,
        availableNamingConventions = NamingConvention.entries,
        availableAnnotationStyles = listOf(JsonToTypeAnnotationStyle.NONE, JsonToTypeAnnotationStyle.CSHARP_JSON_PROPERTY_NAME),
        showsAnalysisWarnings = true,
    ),
    PYTHON(
        displayNameKey = "language.python",
        fileExtension = "py",
        wasmLanguageId = 7,
        defaultNamingConvention = NamingConvention.SNAKE_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
        availableNamingConventions = listOf(NamingConvention.SNAKE_CASE),
        availableAnnotationStyles = listOf(JsonToTypeAnnotationStyle.NONE),
        showsAnalysisWarnings = true,
        usesOriginalJsonFieldNames = true,
    ),
    RUST(
        displayNameKey = "language.rust",
        fileExtension = "rs",
        wasmLanguageId = 8,
        defaultNamingConvention = NamingConvention.SNAKE_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
        availableNamingConventions = NamingConvention.entries,
        availableAnnotationStyles = listOf(JsonToTypeAnnotationStyle.NONE),
        showsAnalysisWarnings = true,
    ),
    SCALA(
        displayNameKey = "language.scala",
        fileExtension = "scala",
        wasmLanguageId = 9,
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
        availableNamingConventions = NamingConvention.entries,
        availableAnnotationStyles = listOf(JsonToTypeAnnotationStyle.NONE),
        showsAnalysisWarnings = true,
    ),
    JSDOC(
        displayNameKey = "language.jsdoc",
        fileExtension = "js",
        wasmLanguageId = 10,
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        defaultAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
        availableNamingConventions = listOf(NamingConvention.CAMEL_CASE),
        availableAnnotationStyles = listOf(JsonToTypeAnnotationStyle.NONE),
        showsAnalysisWarnings = true,
        usesOriginalJsonFieldNames = true,
    ),
    ;

    val resourceKey: String get() = name.lowercase(java.util.Locale.ROOT)
    val inputPlaceholderKey: String get() = "dialog.type.to.json.input.placeholder.$resourceKey"

    fun getDisplayName(): String = LocalizationBundle.message(displayNameKey)

    fun getSupportedNamingConvention(namingConvention: NamingConvention?): NamingConvention {
        return namingConvention?.takeIf { it in availableNamingConventions } ?: defaultNamingConvention
    }

    fun getSupportedAnnotationStyle(annotationStyle: JsonToTypeAnnotationStyle?): JsonToTypeAnnotationStyle {
        return annotationStyle?.takeIf { it in availableAnnotationStyles } ?: defaultAnnotationStyle
    }

    companion object {
        fun fromPersistedValue(value: String?): SupportedLanguage {
            return entries.firstOrNull { it.name == value } ?: KOTLIN
        }
    }
}
