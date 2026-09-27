package com.livteam.jsoninja.services.typeConversion.languages.go

import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object GoTypePolicy : TypeLanguagePolicy {
    override val objectDeclarationKind = TypeDeclarationKind.STRUCT
    override val reservedFieldNames = setOf(
            "break", "default", "func", "interface", "select", "case", "defer", "go", "map", "struct",
            "chan", "else", "goto", "package", "switch", "const", "fallthrough", "if", "range", "type",
            "continue", "for", "import", "return", "var",
        )
}
