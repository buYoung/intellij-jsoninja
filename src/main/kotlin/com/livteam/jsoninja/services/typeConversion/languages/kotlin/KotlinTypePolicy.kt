package com.livteam.jsoninja.services.typeConversion.languages.kotlin

import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object KotlinTypePolicy : TypeLanguagePolicy {
    override val objectDeclarationKind = TypeDeclarationKind.CLASS
    override val reservedFieldNames = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
            "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while", "data",
        )
    override fun escapeLiteralCharacter(character: Char): String? =
        if (character == '$') "\\$" else null
}
