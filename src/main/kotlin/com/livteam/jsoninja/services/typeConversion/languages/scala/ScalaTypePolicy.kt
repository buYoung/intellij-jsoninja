package com.livteam.jsoninja.services.typeConversion.languages.scala

import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object ScalaTypePolicy : TypeLanguagePolicy {
    override val objectDeclarationKind = TypeDeclarationKind.CLASS
    override val reservedFieldNames = "abstract case catch class def do else enum export extends false final finally for forSome given if implicit import lazy macro match new null object opaque open override package private protected return sealed super then this throw trait transparent true try type using val var while with yield end inline infix extension derives".split(' ').toSet()
    override fun escapeDeclarationName(candidate: String) = if (candidate in setOf("String", "Long", "Double", "Boolean", "List", "Map", "Option", "Any", "Null")) "${candidate}Model" else candidate
}
