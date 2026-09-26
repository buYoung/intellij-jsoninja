package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.SupportedLanguage

internal object JsonToTypeLiteralSupport {
    fun quote(value: String, language: SupportedLanguage): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '$' -> {
                    if (language == SupportedLanguage.KOTLIN) append('\\')
                    append(character)
                }
                else -> when {
                    Character.isISOControl(character) && language == SupportedLanguage.JAVA ->
                        append('\\').append(character.code.toString(8).padStart(3, '0'))
                    Character.isISOControl(character) || character in "\u2028\u2029" ->
                        append("\\u").append(character.code.toString(16).padStart(4, '0'))
                    else -> append(character)
                }
            }
        }
        append('"')
    }

    fun goJsonTag(sourceName: String, isOptional: Boolean): String {
        val value = sourceName + if (isOptional) ",omitempty" else ""
        val tag = "json:" + quote(value, SupportedLanguage.GO)
        return if ('`' in tag) quote(tag, SupportedLanguage.GO) else "`$tag`"
    }
}
