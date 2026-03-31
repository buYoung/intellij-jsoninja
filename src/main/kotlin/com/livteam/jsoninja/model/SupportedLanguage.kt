package com.livteam.jsoninja.model

import com.intellij.openapi.util.IconLoader
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.typeConversion.TypeConversionLanguage
import javax.swing.Icon

enum class NamingConvention(
    private val displayNameKey: String,
) {
    CAMEL_CASE("naming.convention.camel.case"),
    PASCAL_CASE("naming.convention.pascal.case"),
    SNAKE_CASE("naming.convention.snake.case");

    val displayName: String
        get() = LocalizationBundle.message(displayNameKey)
}

enum class SupportedLanguage(
    private val displayNameKey: String,
    val fileExtension: String,
    val defaultNamingConvention: NamingConvention,
    val iconPath: String,
) {
    JAVA(
        displayNameKey = "language.java",
        fileExtension = "java",
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        iconPath = "/icons/languages/java.svg",
    ),
    KOTLIN(
        displayNameKey = "language.kotlin",
        fileExtension = "kt",
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        iconPath = "/icons/languages/kotlin.svg",
    ),
    TYPESCRIPT(
        displayNameKey = "language.typescript",
        fileExtension = "ts",
        defaultNamingConvention = NamingConvention.CAMEL_CASE,
        iconPath = "/icons/languages/typescript.svg",
    ),
    GO(
        displayNameKey = "language.go",
        fileExtension = "go",
        defaultNamingConvention = NamingConvention.PASCAL_CASE,
        iconPath = "/icons/languages/go.svg",
    );

    val displayName: String
        get() = LocalizationBundle.message(displayNameKey)

    val icon: Icon? by lazy(LazyThreadSafetyMode.NONE) {
        runCatching { IconLoader.getIcon(iconPath, SupportedLanguage::class.java) }.getOrNull()
    }

    fun toTypeConversionLanguage(): TypeConversionLanguage {
        return when (this) {
            JAVA -> TypeConversionLanguage.JAVA
            KOTLIN -> TypeConversionLanguage.KOTLIN
            TYPESCRIPT -> TypeConversionLanguage.TYPESCRIPT
            GO -> TypeConversionLanguage.GO
        }
    }

    companion object {
        fun fromNameOrDefault(name: String?): SupportedLanguage {
            return entries.firstOrNull { it.name == name } ?: KOTLIN
        }
    }
}
