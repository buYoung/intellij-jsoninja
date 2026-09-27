package com.livteam.jsoninja.services.typeConversion.languages.python

import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object PythonTypePolicy : TypeLanguagePolicy {
    override fun escapeDeclarationName(candidate: String): String = if (candidate in setOf("Any", "TypedDict", "NotRequired", "None", "True", "False")) "${candidate}Model" else candidate
    override val objectDeclarationKind = TypeDeclarationKind.CLASS
    override val reservedFieldNames = "False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield".split(' ').toSet()
    override fun normalizeDeclaration(declaration: TypeDeclaration): TypeDeclaration =
        declaration.copy(fields = declaration.fields.map { it.copy(name = it.sourceName) })
}
