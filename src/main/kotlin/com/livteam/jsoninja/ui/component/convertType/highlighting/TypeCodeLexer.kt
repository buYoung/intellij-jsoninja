package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.lexer.LexerBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/** Multiline literals/comments are single tokens, so restarts begin at their opening delimiter. */
internal class TypeCodeLexer(
    private val profile: TypeCodeProfile,
    private val checkCancellation: () -> Unit = ProgressManager::checkCanceled,
) : LexerBase() {
    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var position = 0
    private var tokenStartOffset = 0
    private var token: IElementType? = null
    private var tokenState = 0
    private var nextState = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        require(startOffset in 0..endOffset && endOffset <= buffer.length)
        this.buffer = buffer
        this.endOffset = endOffset
        position = startOffset
        nextState = initialState
        advance()
    }

    override fun getState(): Int = tokenState
    override fun getTokenType(): IElementType? = token
    override fun getTokenStart(): Int = tokenStartOffset
    override fun getTokenEnd(): Int = position
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        checkCancellation()
        tokenStartOffset = position
        tokenState = nextState
        if (position >= endOffset) { token = null; return }
        token = scanToken()
        check(position > tokenStartOffset) { "Lexer must advance at UTF-16 offset $position" }
    }

    private fun scanToken(): IElementType {
        if (profile.hasDocumentationTags && nextState >= DOC_TEXT) return scanDocumentation()
        val character = buffer[position]
        if (profile.hasCLineSplicing && (matches("\\\n") || matches("\\\r\n"))) {
            val length = if (matches("\\\r\n")) 3 else 2
            repeat(length) { step() }
            return TokenType.WHITE_SPACE
        }
        if (character.isWhitespace()) {
            while (position < endOffset && buffer[position].isWhitespace()) {
                if (buffer[position] == '\n' || buffer[position] == '\r') nextState = 0
                step()
            }
            return TokenType.WHITE_SPACE
        }
        if (nextState == INCLUDE_HEADER && character == '<') {
            step()
            while (position < endOffset && buffer[position] != '>' && buffer[position] !in "\r\n") step()
            if (position < endOffset && buffer[position] == '>') step()
            nextState = 0
            return TypeCodeToken.STRING
        }
        nextState = 0
        profile.specialTokenScanner?.scan(buffer, position, endOffset, checkCancellation)?.let { result ->
            require(result.endOffset in (position + 1)..endOffset)
            position = result.endOffset
            return result.tokenType
        }
        if (profile.hasSlashComments && matches("//")) {
            repeat(2) { step() }
            while (position < endOffset && buffer[position] !in "\r\n") {
                if (profile.hasCLineSplicing && matches("\\\r\n")) repeat(3) { step() }
                else if (profile.hasCLineSplicing && matches("\\\n")) repeat(2) { step() }
                else step()
            }
            return TypeCodeToken.LINE_COMMENT
        }
        if (profile.hasSlashComments && matches("/*")) {
            if (profile.hasDocumentationTags && matches("/**")) {
                repeat(2) { step() }
                nextState = DOC_TEXT
                return TypeCodeToken.DOC_COMMENT
            }
            val isDocumentation = matches("/**") || matches("/*!")
            repeat(2) { step() }
            var depth = 1
            while (position < endOffset && depth > 0) {
                if (profile.hasNestedBlockComments && matches("/*")) { repeat(2) { step() }; depth++ }
                else if (matches("*/")) { repeat(2) { step() }; depth-- }
                else step()
            }
            return if (isDocumentation) TypeCodeToken.DOC_COMMENT else TypeCodeToken.BLOCK_COMMENT
        }
        if (profile.hasPreprocessor && character == '#' && isDirectiveStart()) {
            step()
            while (position < endOffset && buffer[position] in " \t") step()
            val directiveStart = position
            while (position < endOffset && isIdentifierPart(buffer[position])) step()
            if (buffer.subSequence(directiveStart, position).toString() == "include") nextState = INCLUDE_HEADER
            return TypeCodeToken.METADATA
        }
        if (scanRawString()) return TypeCodeToken.STRING
        val prefix = profile.stringPrefixes.firstOrNull {
            matches(it) && position + it.length < endOffset && buffer[position + it.length] in "\"'"
        }
        if (character in "\"'" || prefix != null) {
            position += prefix?.length ?: 0
            scanQuoted(buffer[position])
            return TypeCodeToken.STRING
        }
        if (character.isDigit() || character == '.' && position + 1 < endOffset && buffer[position + 1].isDigit()) {
            step()
            while (position < endOffset) {
                val current = buffer[position]
                if (current.isLetterOrDigit() || current == '.' || current == '_' ||
                    current == '\'' && profile.hasQuotedDigitSeparators && position + 1 < endOffset && buffer[position + 1].isLetterOrDigit() ||
                    current in "+-" && buffer[position - 1] in "eEpP") step() else break
            }
            return TypeCodeToken.NUMBER
        }
        if (character.isLetter() || character == '_') {
            step()
            while (position < endOffset && isIdentifierPart(buffer[position])) step()
            return if (buffer.subSequence(tokenStartOffset, position).toString() in profile.keywords) TypeCodeToken.KEYWORD else TypeCodeToken.IDENTIFIER
        }
        step()
        return when (character) {
            '{', '}' -> TypeCodeToken.BRACES
            '[', ']' -> TypeCodeToken.BRACKETS
            '(', ')' -> TypeCodeToken.PARENTHESES
            ',' -> TypeCodeToken.COMMA
            ';' -> TypeCodeToken.SEMICOLON
            '.' -> TypeCodeToken.DOT
            else -> TypeCodeToken.OPERATOR
        }
    }

    private fun scanQuoted(quote: Char) {
        step()
        while (position < endOffset) {
            when (buffer[position]) {
                quote -> { step(); return }
                '\r', '\n' -> if (profile.allowsMultilineQuotedStrings) step() else return
                '\\' -> {
                    step()
                    if (!profile.allowsEscapedNewlines && position < endOffset && buffer[position] in "\r\n") return
                    if (matches("\r\n")) repeat(2) { step() } else if (position < endOffset) step()
                }
                else -> step()
            }
        }
    }

    private fun scanDocumentation(): IElementType {
        if (matches("*/")) { repeat(2) { step() }; nextState = 0; return TypeCodeToken.DOC_COMMENT }
        val character = buffer[position]
        if (character.isWhitespace()) {
            while (position < endOffset && buffer[position].isWhitespace()) {
                if (buffer[position] in "\r\n") nextState = DOC_TEXT
                step()
            }
            return TypeCodeToken.DOC_COMMENT
        }
        if (character == '@' && position + 1 < endOffset && buffer[position + 1].isLetter()) {
            step()
            while (position < endOffset && buffer[position].isLetter()) step()
            nextState = DOC_VALUE
            return TypeCodeToken.DOC_TAG
        }
        if (nextState == DOC_VALUE) {
            if (character in "{}[]()<>.|?!,=:") {
                step()
                return TypeCodeToken.DOC_VALUE
            }
            if (character in "\"'") {
                val quote = character
                step()
                while (position < endOffset && !matches("*/") && buffer[position] !in "\r\n") {
                    if (buffer[position] == quote) { step(); break }
                    if (buffer[position] == '\\') { step(); if (position < endOffset && !matches("*/") && buffer[position] !in "\r\n") step() }
                    else step()
                }
                return TypeCodeToken.STRING
            }
            if (character.isDigit()) {
                do { step() } while (position < endOffset && (buffer[position].isLetterOrDigit() || buffer[position] in "._"))
                return TypeCodeToken.NUMBER
            }
            step()
            while (position < endOffset && !buffer[position].isWhitespace() && !matches("*/") && buffer[position] !in "@\"'{}[]()<>.|?!,=:") step()
            return TypeCodeToken.DOC_VALUE
        }
        step()
        while (position < endOffset && !buffer[position].isWhitespace() && buffer[position] != '@' && !matches("*/")) step()
        return TypeCodeToken.DOC_COMMENT
    }

    private fun scanRawString(): Boolean {
        val prefix = profile.rawStringPrefixes.firstOrNull { matches("$it\"") } ?: return false
        var delimiterEnd = position + prefix.length + 1
        val delimiterStart = delimiterEnd
        while (delimiterEnd < endOffset && buffer[delimiterEnd] != '(') {
            if (delimiterEnd - delimiterStart >= 16 || buffer[delimiterEnd].isWhitespace() || buffer[delimiterEnd] in ")\\") return false
            delimiterEnd++
        }
        val delimiter = buffer.subSequence(delimiterStart, delimiterEnd).toString()
        if (delimiterEnd == endOffset) { while (position < endOffset) step(); return true }
        position = delimiterEnd + 1
        val closing = ")$delimiter\""
        while (position < endOffset && !matches(closing)) step()
        if (matches(closing)) repeat(closing.length) { step() }
        return true
    }

    private fun isDirectiveStart(): Boolean {
        var offset = position - 1
        while (offset >= 0 && buffer[offset] in " \t") offset--
        if (offset < 0) return true
        if (buffer[offset] !in "\r\n") return false
        if (buffer[offset] == '\n' && offset > 0 && buffer[offset - 1] == '\r') offset--
        return offset == 0 || buffer[offset - 1] != '\\'
    }

    private fun isIdentifierPart(character: Char): Boolean = character.isLetterOrDigit() || character == '_'
    private fun matches(text: String): Boolean = position + text.length <= endOffset && text.indices.all { buffer[position + it] == text[it] }
    private fun step() {
        position++
        if (position and 1023 == 0) checkCancellation()
    }

    private companion object {
        const val INCLUDE_HEADER = 1
        const val DOC_TEXT = 2
        const val DOC_VALUE = 3
    }
}
