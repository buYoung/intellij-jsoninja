package com.livteam.jsoninja.services

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import java.math.BigInteger

/** Normalizes JSON5 tokens before Jackson parsing, without rewriting string contents as syntax. */
internal class Json5InputNormalizer(private val source: String) {
    private var offset = 0
    private val output = StringBuilder(source.length)

    fun normalize(): String {
        while (offset < source.length) {
            when (val character = source[offset]) {
                '"', '\'' -> appendString()
                '/', '\uFEFF' -> appendTrivia()
                '{', '}', '[', ']', ':', ',' -> {
                    output.append(character)
                    offset++
                }
                else -> if (isWhitespace(character)) appendTrivia() else appendToken()
            }
        }
        return output.toString()
    }

    private fun appendTrivia() {
        val startOffset = offset
        skipTrivia()
        if (offset == startOffset) fail("Unexpected token")
        output.append(' ')
    }

    private fun skipTrivia() {
        while (offset < source.length) {
            when {
                isWhitespace(source[offset]) -> offset++
                source.startsWith("//", offset) -> {
                    offset += 2
                    while (offset < source.length && source[offset] !in "\n\r\u2028\u2029") offset++
                }
                source.startsWith("/*", offset) -> {
                    val endOffset = source.indexOf("*/", offset + 2)
                    if (endOffset < 0) fail("Unterminated comment")
                    offset = endOffset + 2
                }
                else -> return
            }
        }
    }

    private fun appendString() {
        val quote = source[offset++]
        output.append('"')
        while (offset < source.length) {
            val character = source[offset++]
            when (character) {
                quote -> {
                    output.append('"')
                    return
                }
                '\n', '\r' -> fail("Unescaped line break")
                '\\' -> {
                    if (offset == source.length) fail("Unterminated escape")
                    when (val escaped = source[offset++]) {
                        '\n', '\u2028', '\u2029' -> Unit
                        '\r' -> if (source.getOrNull(offset) == '\n') offset++
                        'b' -> appendStringCharacter('\b')
                        'f' -> appendStringCharacter('\u000C')
                        'n' -> appendStringCharacter('\n')
                        'r' -> appendStringCharacter('\r')
                        't' -> appendStringCharacter('\t')
                        'v' -> appendStringCharacter('\u000B')
                        '0' -> {
                            if (source.getOrNull(offset) in '0'..'9') fail("Invalid zero escape")
                            appendStringCharacter('\u0000')
                        }
                        'x' -> appendStringCharacter(readHexCharacter(2))
                        'u' -> appendStringCharacter(readHexCharacter(4))
                        in '1'..'9' -> fail("Invalid numeric escape")
                        else -> appendStringCharacter(escaped)
                    }
                }
                else -> appendStringCharacter(character)
            }
        }
        fail("Unterminated string")
    }

    private fun appendStringCharacter(character: Char) {
        when (character) {
            '"' -> output.append("\\\"")
            '\\' -> output.append("\\\\")
            else -> if (character < ' ' || character == '\u2028' || character == '\u2029') {
                output.append("\\u").append(character.code.toString(16).padStart(4, '0'))
            } else {
                output.append(character)
            }
        }
    }

    private fun readHexCharacter(digitCount: Int): Char {
        if (offset + digitCount > source.length) fail("Incomplete character escape")
        val digits = source.substring(offset, offset + digitCount)
        if (digits.any { it.digitToIntOrNull(16) == null }) fail("Invalid character escape")
        offset += digitCount
        return digits.toInt(16).toChar()
    }

    private fun appendToken() {
        val startOffset = offset
        while (offset < source.length && !isWhitespace(source[offset]) && source[offset] !in "{}[]:,/\"'") offset++
        if (offset == startOffset) fail("Unexpected token")
        val token = source.substring(startOffset, offset)
        skipTrivia()
        if (source.getOrNull(offset) == ':') {
            appendIdentifier(token)
            return
        }
        when {
            token == "true" || token == "false" || token == "null" -> output.append(token)
            token.removePrefix("+").removePrefix("-") in setOf("Infinity", "NaN") ->
                fail("Non-finite numbers cannot be converted to JSON")
            HEX_NUMBER.matches(token) -> {
                val unsignedToken = token.removePrefix("+").removePrefix("-")
                val number = BigInteger(unsignedToken.substring(2), 16)
                output.append(if (token.startsWith('-')) number.negate() else number)
            }
            DECIMAL_NUMBER.matches(token) -> {
                var number = token.removePrefix("+")
                if (number.startsWith('.')) number = "0$number"
                if (number.startsWith("-.")) number = "-0${number.substring(1)}"
                val exponentOffset = number.indexOfFirst { it == 'e' || it == 'E' }.let {
                    if (it < 0) number.length else it
                }
                if (number[exponentOffset - 1] == '.') {
                    number = number.substring(0, exponentOffset) + "0" + number.substring(exponentOffset)
                }
                output.append(number)
            }
            else -> output.append(token)
        }
        // Retain a token boundary; Jackson still rejects multiple root values and missing commas.
        output.append(' ')
    }

    private fun appendIdentifier(token: String) {
        val identifier = StringBuilder()
        var tokenOffset = 0
        while (tokenOffset < token.length) {
            if (token[tokenOffset] == '\\') {
                if (token.getOrNull(tokenOffset + 1) != 'u' || tokenOffset + 6 > token.length) {
                    fail("Invalid identifier escape")
                }
                val digits = token.substring(tokenOffset + 2, tokenOffset + 6)
                if (digits.any { it.digitToIntOrNull(16) == null }) fail("Invalid identifier escape")
                identifier.append(digits.toInt(16).toChar())
                tokenOffset += 6
            } else {
                identifier.append(token[tokenOffset++])
            }
        }
        val codePoints = identifier.toString().codePoints().toArray()
        if (codePoints.isEmpty() || !isIdentifierStart(codePoints.first()) ||
            codePoints.drop(1).any { !isIdentifierPart(it) }
        ) fail("Invalid identifier")
        output.append('"').append(identifier).append('"')
    }

    private fun fail(message: String): Nothing =
        throw JsonParseException(null as JsonParser?, "$message at offset $offset")

    companion object {
        private val HEX_NUMBER = Regex("[+-]?0[xX][0-9a-fA-F]+")
        private val DECIMAL_NUMBER = Regex("[+-]?(?:(?:0|[1-9][0-9]*)(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")

        private fun isWhitespace(character: Char): Boolean =
            character in " \t\n\r\u000B\u000C\u00A0\uFEFF\u2028\u2029" ||
                Character.getType(character) == Character.SPACE_SEPARATOR.toInt()

        private fun isIdentifierStart(codePoint: Int): Boolean =
            codePoint == '_'.code || codePoint == '$'.code || Character.isLetter(codePoint) ||
                Character.getType(codePoint) == Character.LETTER_NUMBER.toInt()

        private fun isIdentifierPart(codePoint: Int): Boolean = isIdentifierStart(codePoint) ||
            codePoint == 0x200C || codePoint == 0x200D || Character.getType(codePoint) in setOf(
                Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
                Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.CONNECTOR_PUNCTUATION.toInt(),
            )
    }
}
