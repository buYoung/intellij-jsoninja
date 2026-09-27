package com.livteam.jsoninja.services.typeConversion.languages

import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeEnumValue
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration

internal interface TypeLanguagePolicy {
    val objectDeclarationKind: TypeDeclarationKind
    val reservedFieldNames: Set<String>
    fun escapeDeclarationName(candidate: String): String = candidate
    fun escapeFieldName(candidate: String, suffix: String): String =
        if (candidate in reservedFieldNames) candidate + suffix else candidate
    fun escapeLiteralCharacter(character: Char): String? = null
    fun resolveEnumValues(members: List<Pair<String, String?>>): List<TypeEnumValue>? = null
    fun normalizeDeclaration(declaration: TypeDeclaration): TypeDeclaration = declaration
}
