package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.psi.tree.IElementType

internal data class TypeCodeScanResult(val endOffset: Int, val tokenType: IElementType)

internal fun interface TypeCodeTokenScanner {
    fun scan(buffer: CharSequence, start: Int, end: Int, checkCancellation: () -> Unit): TypeCodeScanResult?
}

internal enum class TypeCodeDeclarationStyle { TYPE_FIRST, NAME_FIRST, GO }

internal data class TypeCodeIdentifierRules(
    val style: TypeCodeDeclarationStyle,
    val predefinedTypes: Set<String>,
    val primitiveKeywords: Set<String> = emptySet(),
    val typeDeclarations: Set<String> = setOf("class", "struct", "enum", "interface", "trait", "union", "record", "type", "typealias"),
    val functionDeclarations: Set<String> = setOf("def", "fun", "fn", "func", "function"),
    val fieldDeclarations: Set<String> = emptySet(),
    val allowsKeywordFieldNames: Boolean = false,
    val hasBracketAttributes: Boolean = false,
)

internal data class TypeCodeProfile(
    val keywords: Set<String>,
    val contextualKeywords: Set<String> = emptySet(),
    val stringPrefixes: Set<String> = emptySet(),
    val hasPreprocessor: Boolean = false,
    val hasSlashComments: Boolean = true,
    val hasNestedBlockComments: Boolean = false,
    val allowsMultilineQuotedStrings: Boolean = false,
    val hasDocumentationTags: Boolean = false,
    val hasCLineSplicing: Boolean = false,
    val allowsEscapedNewlines: Boolean = hasCLineSplicing,
    val rawStringPrefixes: Set<String> = emptySet(),
    val hasQuotedDigitSeparators: Boolean = false,
    val nativeProbe: String,
    val nativeStringProbe: String? = null,
    val specialTokenScanner: TypeCodeTokenScanner? = null,
    val identifiers: TypeCodeIdentifierRules? = null,
)
