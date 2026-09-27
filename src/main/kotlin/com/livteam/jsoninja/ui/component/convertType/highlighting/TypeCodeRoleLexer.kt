package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.lexer.LexerBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/** Adds declaration roles to lexical tokens, without PSI, symbol resolution, or document rescans. */
internal class TypeCodeRoleLexer(
    private val profile: TypeCodeProfile,
    private val checkCancellation: () -> Unit = ProgressManager::checkCanceled,
) : LexerBase() {
    private val lexer = TypeCodeLexer(profile, checkCancellation)
    private var expectation = NONE
    private var typeDepth = 0
    private var hasLineToken = false
    private var hasTypePrefix = false
    private var isTypedef = false
    private var typedefBodyDepth = 0
    private var isAttribute = false
    private var isAttributeName = false
    private var state = 0
    private var token: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        expectation = (initialState ushr 3) and 7
        typeDepth = (initialState ushr 6) and MAX_DEPTH
        hasLineToken = initialState and LINE_TOKEN != 0
        hasTypePrefix = initialState and TYPE_PREFIX != 0
        isTypedef = initialState and TYPEDEF != 0
        typedefBodyDepth = (initialState ushr 19) and MAX_TYPEDEF_DEPTH
        isAttribute = initialState and ATTRIBUTE != 0
        isAttributeName = initialState and ATTRIBUTE_NAME != 0
        lexer.start(buffer, startOffset, endOffset, initialState and 7)
        classify()
    }

    override fun getState(): Int = state
    override fun getTokenType(): IElementType? = token
    override fun getTokenStart(): Int = lexer.tokenStart
    override fun getTokenEnd(): Int = lexer.tokenEnd
    override fun getBufferSequence(): CharSequence = lexer.bufferSequence
    override fun getBufferEnd(): Int = lexer.bufferEnd
    override fun advance() { lexer.advance(); classify() }

    private fun classify() {
        checkCancellation()
        state = lexer.state or (expectation shl 3) or (typeDepth shl 6) or
            (if (hasLineToken) LINE_TOKEN else 0) or (if (hasTypePrefix) TYPE_PREFIX else 0) or
            (if (isTypedef) TYPEDEF else 0) or (typedefBodyDepth shl 19) or
            (if (isAttribute) ATTRIBUTE else 0) or (if (isAttributeName) ATTRIBUTE_NAME else 0)
        val raw = lexer.tokenType ?: run { token = null; return }
        token = raw
        val rules = profile.identifiers ?: return
        if (raw == TokenType.WHITE_SPACE || raw == TypeCodeToken.DOC_COMMENT) {
            if (containsNewline()) {
                hasLineToken = false
                if (typeDepth == 0 && expectation != TYPE_DECLARATION && expectation != FUNCTION_DECLARATION) {
                    expectation = NONE
                    hasTypePrefix = false
                }
            }
            return
        }
        if (raw == TypeCodeToken.LINE_COMMENT || raw == TypeCodeToken.BLOCK_COMMENT) return
        if (raw == TypeCodeToken.STRING || raw == TypeCodeToken.NUMBER) {
            if (typeDepth == 0) expectation = NONE
            hasLineToken = true
            return
        }
        val text = bufferSequence.subSequence(tokenStart, tokenEnd).toString()
        if (raw == TypeCodeToken.DOC_TAG) {
            expectation = when (text) {
                "@typedef", "@callback" -> TYPE_DECLARATION
                "@property", "@prop", "@param", "@arg", "@argument" -> FIELD_DECLARATION
                else -> NONE
            }
            typeDepth = 0
            return
        }
        if (raw == TypeCodeToken.DOC_VALUE) {
            token = classifyDocumentation(text, rules)
            return
        }
        val isKeywordName = raw == TypeCodeToken.KEYWORD &&
            ((text in profile.contextualKeywords && (expectation == FIELD_DECLARATION || expectation == FUNCTION_DECLARATION ||
                expectation == TYPE_DECLARATION || expectation == AFTER_TYPE || charAt(nextSignificantOffset(tokenEnd)) == ':')) ||
                rules.allowsKeywordFieldNames && charAt(nextSignificantOffset(tokenEnd)) == ':')
        if (raw == TypeCodeToken.KEYWORD && !isKeywordName) {
            if (text == "typedef") isTypedef = true
            expectation = when {
                text in rules.typeDeclarations -> TYPE_DECLARATION
                text in rules.functionDeclarations -> FUNCTION_DECLARATION
                text in rules.fieldDeclarations -> FIELD_DECLARATION
                text in rules.primitiveKeywords -> {
                    hasTypePrefix = true
                    if (expectation == TYPE_EXPRESSION || typeDepth > 0) TYPE_EXPRESSION else AFTER_TYPE
                }
                text in IMPORT_KEYWORDS -> IMPORT
                text in EXPRESSION_KEYWORDS -> NONE
                else -> expectation
            }
        } else if (raw == TypeCodeToken.IDENTIFIER || isKeywordName) {
            token = classifyIdentifier(text, rules)
            when (token) {
                TypeCodeToken.METADATA -> {
                    isAttributeName = charAt(nextSignificantOffset(tokenEnd)) in ".:"
                    expectation = NONE
                }
                TypeCodeToken.TYPE_NAME, TypeCodeToken.PREDEFINED_TYPE -> {
                    hasTypePrefix = true
                    if (expectation != IMPORT) expectation = if (expectation == TYPE_EXPRESSION || typeDepth > 0) TYPE_EXPRESSION else AFTER_TYPE
                }
                TypeCodeToken.FIELD -> expectation = if (rules.style == TypeCodeDeclarationStyle.GO) TYPE_EXPRESSION else NONE
                TypeCodeToken.FUNCTION_DECLARATION, TypeCodeToken.FUNCTION_CALL -> { expectation = NONE; hasTypePrefix = false }
                else -> if (expectation != IMPORT) expectation = NONE
            }
        } else if (text.length == 1) {
            updatePunctuation(text[0], rules)
        }
        hasLineToken = text != "{" && text != ";" && !(rules.hasBracketAttributes && text == "]" && !isAttribute && typeDepth == 0)
    }

    private fun classifyIdentifier(text: String, rules: TypeCodeIdentifierRules): IElementType {
        if (text.startsWith("'")) return TypeCodeToken.IDENTIFIER // Rust lifetime, not a declaration.
        val name = text.removeSurrounding("`").removePrefix("@").removePrefix("r#")
        val next = nextSignificantOffset(tokenEnd)
        val nextCharacter = charAt(next)
        if (isAttributeName) return TypeCodeToken.METADATA
        if (expectation == TYPE_DECLARATION) return TypeCodeToken.TYPE_NAME
        if (expectation == FUNCTION_DECLARATION) return TypeCodeToken.FUNCTION_DECLARATION
        if (expectation == FIELD_DECLARATION) return TypeCodeToken.FIELD
        if (expectation == IMPORT) {
            if (nextCharacter == '=') return TypeCodeToken.TYPE_NAME
            return if (name in rules.predefinedTypes) TypeCodeToken.PREDEFINED_TYPE else TypeCodeToken.IDENTIFIER
        }
        if (nextCharacter == ':' && charAt(next + 1) != ':' ||
            nextCharacter == '?' && charAt(nextSignificantOffset(next + 1)) == ':') return TypeCodeToken.FIELD
        if (expectation == AFTER_TYPE && typeDepth == 0 && rules.style == TypeCodeDeclarationStyle.TYPE_FIRST) {
            if (isTypedef && typedefBodyDepth == 0) return TypeCodeToken.TYPE_NAME
            if (nextCharacter == '(') return TypeCodeToken.FUNCTION_DECLARATION
            if (nextCharacter in ";=,[){") return TypeCodeToken.FIELD
        }
        if (rules.style == TypeCodeDeclarationStyle.GO && !hasLineToken &&
            (nextCharacter.isLetter() || nextCharacter in "[*")) return TypeCodeToken.FIELD
        if (name in rules.predefinedTypes) return TypeCodeToken.PREDEFINED_TYPE
        if (isTypedef && typedefBodyDepth == 0 && nextCharacter in ";,") return TypeCodeToken.TYPE_NAME
        if (nextCharacter == '.' || nextCharacter == ':' && charAt(next + 1) == ':') return TypeCodeToken.IDENTIFIER
        if (expectation == TYPE_EXPRESSION || name.firstOrNull()?.isUpperCase() == true) return TypeCodeToken.TYPE_NAME
        if (nextCharacter == '(') return TypeCodeToken.FUNCTION_CALL
        if (expectation == AFTER_DOT) return TypeCodeToken.FIELD
        if (rules.style == TypeCodeDeclarationStyle.TYPE_FIRST && nextCharacter.isLetter()) return TypeCodeToken.TYPE_NAME
        return TypeCodeToken.IDENTIFIER
    }

    private fun updatePunctuation(character: Char, rules: TypeCodeIdentifierRules) {
        if (rules.hasBracketAttributes && character == '[' && !hasLineToken && expectation == NONE) {
            isAttribute = true
            isAttributeName = true
            typeDepth = 0
            return
        }
        if (isAttribute) {
            when (character) {
                '(', '[' -> { typeDepth = (typeDepth + 1).coerceAtMost(MAX_DEPTH); isAttributeName = false }
                ')' -> typeDepth = (typeDepth - 1).coerceAtLeast(0)
                ']' -> if (typeDepth > 0) typeDepth-- else { isAttribute = false; isAttributeName = false }
                ',' -> if (typeDepth == 0) isAttributeName = true
            }
            return
        }
        if (isTypedef) {
            when (character) {
                '{' -> typedefBodyDepth = (typedefBodyDepth + 1).coerceAtMost(MAX_TYPEDEF_DEPTH)
                '}' -> typedefBodyDepth = (typedefBodyDepth - 1).coerceAtLeast(0)
                ';' -> if (typedefBodyDepth == 0) isTypedef = false
            }
        }
        when (character) {
            ':' -> expectation = if (charAt(tokenStart - 1) == ':' || charAt(tokenEnd) == ':') AFTER_DOT else TYPE_EXPRESSION
            '.' -> if (expectation != IMPORT) expectation = AFTER_DOT
            '<', '[' -> if (expectation == AFTER_TYPE || expectation == TYPE_EXPRESSION) {
                typeDepth = (typeDepth + 1).coerceAtMost(MAX_DEPTH)
                expectation = TYPE_EXPRESSION
            }
            '>', ']' -> if (typeDepth > 0) {
                typeDepth--
                expectation = if (typeDepth == 0 && rules.style == TypeCodeDeclarationStyle.TYPE_FIRST) AFTER_TYPE else TYPE_EXPRESSION
            } else if (character == '>' && charAt(tokenStart - 1) == '-') expectation = TYPE_EXPRESSION
            ',' -> if (typeDepth == 0 && expectation != IMPORT) {
                expectation = if (hasTypePrefix && rules.style == TypeCodeDeclarationStyle.TYPE_FIRST) AFTER_TYPE else NONE
            }
            ';', '{', '}', '=' -> { expectation = NONE; typeDepth = 0; hasTypePrefix = false }
            '(', ')' -> if (expectation != IMPORT) { expectation = NONE; hasTypePrefix = false }
        }
    }

    private fun classifyDocumentation(text: String, rules: TypeCodeIdentifierRules): IElementType {
        if (text == "{") { typeDepth = (typeDepth + 1).coerceAtMost(MAX_DEPTH); return TypeCodeToken.DOC_VALUE }
        if (text == "}") { typeDepth = (typeDepth - 1).coerceAtLeast(0); return TypeCodeToken.DOC_VALUE }
        if (text.firstOrNull()?.let { it.isLetter() || it == '_' } != true) return TypeCodeToken.DOC_VALUE
        if (typeDepth > 0) return if (text in rules.predefinedTypes) TypeCodeToken.PREDEFINED_TYPE else TypeCodeToken.TYPE_NAME
        val result = when (expectation) {
            TYPE_DECLARATION -> TypeCodeToken.TYPE_NAME
            FIELD_DECLARATION -> TypeCodeToken.FIELD
            else -> TypeCodeToken.DOC_VALUE
        }
        expectation = NONE
        return result
    }

    private fun containsNewline(): Boolean {
        for (offset in tokenStart until tokenEnd) {
            if (offset and 1023 == 0) checkCancellation()
            if (bufferSequence[offset] in "\r\n") return true
        }
        return false
    }

    private fun nextSignificantOffset(start: Int): Int {
        var offset = start
        while (offset < bufferEnd && bufferSequence[offset] in " \t") {
            if (offset and 1023 == 0) checkCancellation()
            offset++
        }
        return offset
    }

    private fun charAt(offset: Int): Char = if (offset in 0 until bufferEnd) bufferSequence[offset] else '\u0000'

    private companion object {
        const val NONE = 0
        const val TYPE_DECLARATION = 1
        const val FUNCTION_DECLARATION = 2
        const val FIELD_DECLARATION = 3
        const val TYPE_EXPRESSION = 4
        const val AFTER_TYPE = 5
        const val IMPORT = 6
        const val AFTER_DOT = 7
        const val MAX_DEPTH = 1023
        // Nonzero states within a line make lookahead edits restart from its safe boundary.
        const val LINE_TOKEN = 1 shl 16
        const val TYPE_PREFIX = 1 shl 17
        const val TYPEDEF = 1 shl 18
        const val MAX_TYPEDEF_DEPTH = 255
        const val ATTRIBUTE = 1 shl 27
        const val ATTRIBUTE_NAME = 1 shl 28
        val IMPORT_KEYWORDS = setOf("import", "from", "package", "namespace", "using", "use", "mod")
        val EXPRESSION_KEYWORDS = setOf("return", "throw", "yield", "new")
    }
}
