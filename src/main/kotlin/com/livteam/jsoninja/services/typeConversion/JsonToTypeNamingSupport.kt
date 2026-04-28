package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.SupportedLanguage
import java.util.Locale

object JsonToTypeNamingSupport {
    private val invalidIdentifierCharacters = Regex("[^A-Za-z0-9]+")
    private val camelCaseBoundary = Regex("([a-z0-9])([A-Z])")
    private val leadingDigits = Regex("^[0-9]+")
    private val reservedWordsByLanguage = mapOf(
        SupportedLanguage.KOTLIN to setOf("class", "object", "interface", "val", "var", "when", "data"),
        SupportedLanguage.JAVA to setOf("class", "interface", "enum", "public", "private", "package"),
        SupportedLanguage.TYPESCRIPT to setOf("type", "interface", "class", "enum", "extends", "function"),
        SupportedLanguage.GO to setOf("type", "struct", "interface", "map", "func", "package"),
    )

    fun toFieldName(
        rawName: String,
        namingConvention: NamingConvention,
        language: SupportedLanguage,
        usedNames: Set<String> = emptySet(),
    ): String {
        val words = splitWords(rawName).ifEmpty { listOf("value") }
        val normalizedName = applyNamingConvention(words, namingConvention).sanitizeIdentifier("value")
        val escapedName = escapeReservedWord(normalizedName, language, suffix = "Value")
        return deduplicateName(escapedName, usedNames)
    }

    fun toTypeName(rawName: String): String {
        val words = splitWords(rawName).ifEmpty { listOf("Root") }
        return applyNamingConvention(words, NamingConvention.PASCAL_CASE).sanitizeIdentifier("Root")
    }

    fun buildNestedTypeName(
        parentTypeName: String,
        fieldSourceName: String,
    ): String {
        return toTypeName("$parentTypeName ${singularize(fieldSourceName)}")
    }

    fun singularize(rawName: String): String {
        val trimmedName = rawName.trim()
        return when {
            trimmedName.endsWith("ies", ignoreCase = true) && trimmedName.length > 3 ->
                trimmedName.dropLast(3) + "y"
            trimmedName.endsWith("ses", ignoreCase = true) && trimmedName.length > 3 ->
                trimmedName.dropLast(2)
            trimmedName.endsWith("s", ignoreCase = true) && trimmedName.length > 1 ->
                trimmedName.dropLast(1)
            else -> trimmedName
        }
    }

    fun isValidTypeIdentifier(candidate: String): Boolean {
        return candidate.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))
    }

    private fun splitWords(rawName: String): List<String> {
        val withSpaces = rawName
            .replace(camelCaseBoundary, "$1 $2")
            .replace(invalidIdentifierCharacters, " ")
        return withSpaces
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .map { it.lowercase(Locale.ENGLISH) }
    }

    private fun applyNamingConvention(
        words: List<String>,
        namingConvention: NamingConvention,
    ): String {
        return when (namingConvention) {
            NamingConvention.CAMEL_CASE -> {
                words.first().lowercase(Locale.ENGLISH) +
                    words.drop(1).joinToString("") { it.replaceFirstChar(Char::titlecase) }
            }
            NamingConvention.PASCAL_CASE -> words.joinToString("") { it.replaceFirstChar(Char::titlecase) }
            NamingConvention.SNAKE_CASE -> words.joinToString("_")
        }
    }

    private fun String.sanitizeIdentifier(fallback: String): String {
        val withoutLeadingDigits = replace(leadingDigits, "")
        return withoutLeadingDigits.ifBlank { fallback }
    }

    private fun escapeReservedWord(
        candidate: String,
        language: SupportedLanguage,
        suffix: String,
    ): String {
        val reservedWords = reservedWordsByLanguage[language].orEmpty()
        if (candidate !in reservedWords) {
            return candidate
        }
        return candidate + suffix
    }

    private fun deduplicateName(
        baseName: String,
        usedNames: Set<String>,
    ): String {
        if (baseName !in usedNames) {
            return baseName
        }

        var suffixIndex = 2
        while ("$baseName$suffixIndex" in usedNames) {
            suffixIndex++
        }
        return "$baseName$suffixIndex"
    }
}
