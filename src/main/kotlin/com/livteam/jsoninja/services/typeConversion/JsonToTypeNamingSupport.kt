package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.SupportedLanguage
import java.util.Locale

object JsonToTypeNamingSupport {
    private val invalidIdentifierCharacters = Regex("[^A-Za-z0-9]+")
    private val camelCaseBoundary = Regex("([a-z0-9])([A-Z])")
    private val leadingDigits = Regex("^[0-9]+")
    private val reservedWordsByLanguage = mapOf(
        SupportedLanguage.KOTLIN to setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
            "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while", "data",
        ),
        SupportedLanguage.JAVA to setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "_", "true", "false", "null",
        ),
        SupportedLanguage.TYPESCRIPT to setOf("type", "interface", "class", "enum", "extends", "function"),
        SupportedLanguage.GO to setOf(
            "break", "default", "func", "interface", "select", "case", "defer", "go", "map", "struct",
            "chan", "else", "goto", "package", "switch", "const", "fallthrough", "if", "range", "type",
            "continue", "for", "import", "return", "var",
        ),
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
        // Java accessors for a PascalCase `Class` property would override final Object.getClass().
        if (candidate !in reservedWords && !(language == SupportedLanguage.JAVA && candidate == "Class")) {
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
