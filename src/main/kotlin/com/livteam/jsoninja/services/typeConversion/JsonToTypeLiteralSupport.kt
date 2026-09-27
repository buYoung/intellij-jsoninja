package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRegistry
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object JsonToTypeLiteralSupport {
    fun quote(value: String, language: SupportedLanguage): String = quote(value, TypeLanguageRegistry.forLanguage(language).policy)

    fun quote(value: String, policy: TypeLanguagePolicy): String = buildString {
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
                else -> when {
                    policy.escapeLiteralCharacter(character) != null ->
                        append(policy.escapeLiteralCharacter(character))
                    Character.isISOControl(character) || character in "\u2028\u2029" ->
                        append("\\u").append(character.code.toString(16).padStart(4, '0'))
                    else -> append(character)
                }
            }
        }
        append('"')
    }

}
