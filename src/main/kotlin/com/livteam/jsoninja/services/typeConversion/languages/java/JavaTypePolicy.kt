package com.livteam.jsoninja.services.typeConversion.languages.java

import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object JavaTypePolicy : TypeLanguagePolicy {
    override val objectDeclarationKind = TypeDeclarationKind.CLASS
    override val reservedFieldNames = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "_", "true", "false", "null",
        ) + "Class"
    override fun escapeLiteralCharacter(character: Char): String? =
        if (Character.isISOControl(character)) "\\" + character.code.toString(8).padStart(3, '0') else null
}
