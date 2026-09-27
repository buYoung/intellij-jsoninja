package com.livteam.jsoninja.services.typeConversion.languages.rust

import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object RustTypePolicy : TypeLanguagePolicy {
    override val objectDeclarationKind = TypeDeclarationKind.STRUCT
    override fun escapeDeclarationName(candidate: String): String = if (candidate in setOf("Self", "String", "Vec", "Option", "HashMap")) "${candidate}Model" else candidate
    override val reservedFieldNames = "as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while abstract become box do final macro override priv typeof unsized virtual yield try union".split(' ').toSet()
    override fun escapeFieldName(candidate: String, suffix: String): String = when {
        candidate in setOf("self", "Self", "super", "crate") -> candidate + suffix
        candidate in reservedFieldNames -> "r#$candidate"
        else -> candidate
    }
    override fun escapeLiteralCharacter(character: Char): String? = if (character.code < 32 || character.code == 127) "\\u{${character.code.toString(16)}}" else null
}
