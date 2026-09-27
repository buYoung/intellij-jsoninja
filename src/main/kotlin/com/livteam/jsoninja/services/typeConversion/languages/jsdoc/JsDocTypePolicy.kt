package com.livteam.jsoninja.services.typeConversion.languages.jsdoc

import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object JsDocTypePolicy : TypeLanguagePolicy {
    override val objectDeclarationKind = TypeDeclarationKind.CLASS
    override val reservedFieldNames = emptySet<String>()
    override fun escapeDeclarationName(candidate: String) = if (candidate in setOf("Object", "Array", "String", "Number", "Boolean", "Function")) "${candidate}Model" else candidate
    override fun normalizeDeclaration(declaration: TypeDeclaration) = declaration.copy(fields = declaration.fields.map { it.copy(name = it.sourceName) })
}
