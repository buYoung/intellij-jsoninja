package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.lang.Language
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Colors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

internal class TypeCodeToken(name: String, key: TextAttributesKey) : IElementType(name, Language.ANY) {
    val keys: Array<TextAttributesKey> = arrayOf(key)

    companion object {
        val KEYWORD = TypeCodeToken("TYPE_KEYWORD", Colors.KEYWORD)
        val IDENTIFIER = TypeCodeToken("TYPE_IDENTIFIER", Colors.IDENTIFIER)
        val TYPE_NAME = TypeCodeToken("TYPE_NAME", Colors.CLASS_NAME)
        val PREDEFINED_TYPE = TypeCodeToken("TYPE_PREDEFINED", Colors.PREDEFINED_SYMBOL)
        val FIELD = TypeCodeToken("TYPE_FIELD", Colors.INSTANCE_FIELD)
        val FUNCTION_DECLARATION = TypeCodeToken("TYPE_FUNCTION_DECLARATION", Colors.FUNCTION_DECLARATION)
        val FUNCTION_CALL = TypeCodeToken("TYPE_FUNCTION_CALL", Colors.FUNCTION_CALL)
        val STRING = TypeCodeToken("TYPE_STRING", Colors.STRING)
        val NUMBER = TypeCodeToken("TYPE_NUMBER", Colors.NUMBER)
        val LINE_COMMENT = TypeCodeToken("TYPE_LINE_COMMENT", Colors.LINE_COMMENT)
        val BLOCK_COMMENT = TypeCodeToken("TYPE_BLOCK_COMMENT", Colors.BLOCK_COMMENT)
        val DOC_COMMENT = TypeCodeToken("TYPE_DOC_COMMENT", Colors.DOC_COMMENT)
        val DOC_TAG = TypeCodeToken("TYPE_DOC_TAG", Colors.DOC_COMMENT_TAG)
        val DOC_VALUE = TypeCodeToken("TYPE_DOC_VALUE", Colors.DOC_COMMENT_TAG_VALUE)
        val METADATA = TypeCodeToken("TYPE_METADATA", Colors.METADATA)
        val OPERATOR = TypeCodeToken("TYPE_OPERATOR", Colors.OPERATION_SIGN)
        val BRACES = TypeCodeToken("TYPE_BRACES", Colors.BRACES)
        val BRACKETS = TypeCodeToken("TYPE_BRACKETS", Colors.BRACKETS)
        val PARENTHESES = TypeCodeToken("TYPE_PARENTHESES", Colors.PARENTHESES)
        val COMMA = TypeCodeToken("TYPE_COMMA", Colors.COMMA)
        val SEMICOLON = TypeCodeToken("TYPE_SEMICOLON", Colors.SEMICOLON)
        val DOT = TypeCodeToken("TYPE_DOT", Colors.DOT)
    }
}

internal class TypeCodeSyntaxHighlighter(private val profile: TypeCodeProfile) : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = TypeCodeRoleLexer(profile)
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        (tokenType as? TypeCodeToken)?.keys ?: TextAttributesKey.EMPTY_ARRAY
}
