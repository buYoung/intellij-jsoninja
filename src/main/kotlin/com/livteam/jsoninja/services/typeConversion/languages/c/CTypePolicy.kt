package com.livteam.jsoninja.services.typeConversion.languages.c

import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object CTypePolicy : TypeLanguagePolicy {
    override val objectDeclarationKind = TypeDeclarationKind.STRUCT
    override val reservedFieldNames = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum",
        "extern", "float", "for", "goto", "if", "inline", "int", "long", "register", "restrict", "return",
        "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void",
        "volatile", "while", "_Alignas", "_Alignof", "_Atomic", "_Bool", "_Complex", "_Generic", "_Imaginary",
        "_Noreturn", "_Static_assert", "_Thread_local", "bool", "true", "false",
    )

    override fun resolveEnumValues(members: List<Pair<String, String?>>) = CEnumValues.resolve(members)
}
