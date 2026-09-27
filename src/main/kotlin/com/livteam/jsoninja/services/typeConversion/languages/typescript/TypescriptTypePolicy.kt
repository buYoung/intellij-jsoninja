package com.livteam.jsoninja.services.typeConversion.languages.typescript

import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object TypescriptTypePolicy : TypeLanguagePolicy {
    override val objectDeclarationKind = TypeDeclarationKind.INTERFACE
    override val reservedFieldNames = setOf("type", "interface", "class", "enum", "extends", "function")
}
