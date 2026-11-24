package com.livteam.jsoninja.services

import com.intellij.util.text.CharArrayUtil
import java.util.UUID

data class PlaceholderMapping(
    val originalPlaceholder: String,
    val sentinelToken: String,
    val originalStartIndex: Int,
    val originalEndIndex: Int
)

data class ReplacementResult(
    val replacedText: String,
    val mappings: List<PlaceholderMapping>,
    val sentinelPrefix: String,
    val isSuccessful: Boolean
)

object TemplatePlaceholderSupport {
    private const val PLACEHOLDER_OPEN = "{{"
    private const val PLACEHOLDER_CLOSE = "}}"
    private const val SCAN_CHUNK_SIZE = 8192

    fun containsPlaceholderOpen(input: CharSequence): Boolean {
        if (input.length < PLACEHOLDER_OPEN.length) return false
        if (input is String) {
            return input.indexOf(PLACEHOLDER_OPEN) >= 0
        }
        return indexOfRepeatedChar(input, '{', 0) >= 0
    }

    fun containsPlaceholderTokens(input: CharSequence): Boolean {
        if (input.length < PLACEHOLDER_OPEN.length + PLACEHOLDER_CLOSE.length) return false
        if (input is String) {
            val openIndex = input.indexOf(PLACEHOLDER_OPEN)
            return openIndex >= 0 && input.indexOf(PLACEHOLDER_CLOSE, startIndex = openIndex + PLACEHOLDER_OPEN.length) >= 0
        }
        val openIndex = indexOfRepeatedChar(input, '{', 0)
        return openIndex >= 0 && indexOfRepeatedChar(input, '}', openIndex + PLACEHOLDER_OPEN.length) >= 0
    }

    /**
     * Bulk-copies 8KB chunks via [CharArrayUtil.getChars] to avoid per-character rope traversal
     * on IntelliJ's `ImmutableText` (`Document.immutableCharSequence`).
     */
    private fun indexOfRepeatedChar(input: CharSequence, target: Char, startIndex: Int): Int {
        val length = input.length
        if (startIndex < 0 || startIndex + 2 > length) return -1

        val chunkSize = minOf(SCAN_CHUNK_SIZE, length - startIndex)
        val buffer = CharArray(chunkSize)
        var offset = startIndex
        var previousMatched = false

        while (offset < length) {
            val count = minOf(chunkSize, length - offset)
            CharArrayUtil.getChars(input, buffer, offset, 0, count)
            if (previousMatched) {
                if (buffer[0] == target) {
                    return offset - 1
                }
                previousMatched = false
            }

            var index = 0
            while (index < count) {
                if (buffer[index] == target) {
                    if (index + 1 < count) {
                        if (buffer[index + 1] == target) {
                            return offset + index
                        }
                        // buffer[index + 1] != target, so it cannot start a target-target pair.
                        index += 2
                        continue
                    } else {
                        previousMatched = true
                    }
                }
                index++
            }
            offset += count
        }
        return -1
    }

    fun normalizePlaceholderLayout(input: String): String {
        return normalizePlaceholderLayoutIfChanged(input) ?: input
    }

    fun normalizePlaceholderLayoutIfChanged(input: CharSequence): String? {
        if (!containsPlaceholderTokens(input)) {
            return null
        }

        val normalizedBuilder = StringBuilder(input.length)
        var currentIndex = 0
        var hasPlaceholderLayoutChanged = false

        while (currentIndex < input.length) {
            val currentCharacter = input[currentIndex]

            if (currentCharacter == '"' || currentCharacter == '\'') {
                val stringEndIndex = findStringEndIndex(input, currentIndex, currentCharacter)
                if (stringEndIndex == -1) {
                    normalizedBuilder.append(input, currentIndex, input.length)
                    break
                }

                normalizedBuilder.append(input, currentIndex, stringEndIndex + 1)
                currentIndex = stringEndIndex + 1
                continue
            }

            if (startsWithLineComment(input, currentIndex)) {
                val lineCommentEndIndex = findLineCommentEndIndex(input, currentIndex)
                normalizedBuilder.append(input, currentIndex, lineCommentEndIndex)
                currentIndex = lineCommentEndIndex
                continue
            }

            if (startsWithBlockComment(input, currentIndex)) {
                val blockCommentEndIndex = findBlockCommentEndIndex(input, currentIndex)
                if (blockCommentEndIndex == -1) {
                    normalizedBuilder.append(input, currentIndex, input.length)
                    break
                }

                normalizedBuilder.append(input, currentIndex, blockCommentEndIndex + 2)
                currentIndex = blockCommentEndIndex + 2
                continue
            }

            if (startsWithPlaceholderOpen(input, currentIndex)) {
                val placeholderEndIndex = findPlaceholderEndIndex(input, currentIndex + PLACEHOLDER_OPEN.length)
                if (placeholderEndIndex == -1) {
                    normalizedBuilder.append(input, currentIndex, input.length)
                    break
                }

                val placeholderExclusiveEnd = placeholderEndIndex + PLACEHOLDER_CLOSE.length
                val placeholderText = input.subSequence(currentIndex, placeholderExclusiveEnd).toString()
                val normalizedPlaceholderText = normalizePlaceholderText(placeholderText)
                if (normalizedPlaceholderText != placeholderText) {
                    hasPlaceholderLayoutChanged = true
                }

                normalizedBuilder.append(normalizedPlaceholderText)
                currentIndex = placeholderExclusiveEnd
                continue
            }

            normalizedBuilder.append(currentCharacter)
            currentIndex++
        }

        return if (hasPlaceholderLayoutChanged) normalizedBuilder.toString() else null
    }

    fun extractAndReplaceValuePlaceholders(input: String): ReplacementResult {
        if (input.isEmpty() || !containsPlaceholderOpen(input)) {
            return ReplacementResult(
                replacedText = input,
                mappings = emptyList(),
                sentinelPrefix = "",
                isSuccessful = true
            )
        }

        val sentinelPrefix = createSentinelPrefix()
        val placeholderMappings = mutableListOf<PlaceholderMapping>()
        val replacedBuilder = StringBuilder(input.length + 32)

        var currentIndex = 0
        var placeholderCount = 0
        var previousSignificantCharacter: Char? = null

        while (currentIndex < input.length) {
            val currentCharacter = input[currentIndex]

            if (currentCharacter == '"' || currentCharacter == '\'') {
                val stringEndIndex = findStringEndIndex(input, currentIndex, currentCharacter)
                if (stringEndIndex == -1) {
                    replacedBuilder.append(input, currentIndex, input.length)
                    break
                }

                replacedBuilder.append(input, currentIndex, stringEndIndex + 1)
                previousSignificantCharacter = currentCharacter
                currentIndex = stringEndIndex + 1
                continue
            }

            if (startsWithLineComment(input, currentIndex)) {
                val lineCommentEndIndex = findLineCommentEndIndex(input, currentIndex)
                replacedBuilder.append(input, currentIndex, lineCommentEndIndex)
                currentIndex = lineCommentEndIndex
                continue
            }

            if (startsWithBlockComment(input, currentIndex)) {
                val blockCommentEndIndex = findBlockCommentEndIndex(input, currentIndex)
                if (blockCommentEndIndex == -1) {
                    replacedBuilder.append(input, currentIndex, input.length)
                    break
                }

                replacedBuilder.append(input, currentIndex, blockCommentEndIndex + 2)
                currentIndex = blockCommentEndIndex + 2
                continue
            }

            if (startsWithPlaceholderOpen(input, currentIndex)) {
                val placeholderEndIndex = findPlaceholderEndIndex(input, currentIndex + PLACEHOLDER_OPEN.length)
                if (placeholderEndIndex == -1) {
                    return ReplacementResult(
                        replacedText = input,
                        mappings = emptyList(),
                        sentinelPrefix = sentinelPrefix,
                        isSuccessful = false
                    )
                }

                val placeholderExclusiveEnd = placeholderEndIndex + PLACEHOLDER_CLOSE.length
                val placeholderText = input.substring(currentIndex, placeholderExclusiveEnd)
                val nextSignificantCharacter = findNextSignificantCharacter(
                    input = input,
                    startIndex = placeholderExclusiveEnd
                )

                val isKeyPlaceholder = nextSignificantCharacter == ':'
                val isValuePosition = previousSignificantCharacter == null ||
                    previousSignificantCharacter == ':' ||
                    previousSignificantCharacter == '[' ||
                    previousSignificantCharacter == ','

                if (!isKeyPlaceholder && isValuePosition) {
                    val sentinelToken = sentinelPrefix + placeholderCount + "__"
                    placeholderMappings.add(
                        PlaceholderMapping(
                            originalPlaceholder = placeholderText,
                            sentinelToken = sentinelToken,
                            originalStartIndex = currentIndex,
                            originalEndIndex = placeholderExclusiveEnd
                        )
                    )
                    replacedBuilder.append('"')
                    replacedBuilder.append(sentinelToken)
                    replacedBuilder.append('"')

                    placeholderCount++
                    previousSignificantCharacter = '"'
                } else {
                    replacedBuilder.append(placeholderText)
                    previousSignificantCharacter = '}'
                }

                currentIndex = placeholderExclusiveEnd
                continue
            }

            replacedBuilder.append(currentCharacter)
            if (!currentCharacter.isWhitespace()) {
                previousSignificantCharacter = currentCharacter
            }
            currentIndex++
        }

        return ReplacementResult(
            replacedText = replacedBuilder.toString(),
            mappings = placeholderMappings,
            sentinelPrefix = sentinelPrefix,
            isSuccessful = true
        )
    }

    fun restorePlaceholders(formatted: String, mappings: List<PlaceholderMapping>): String {
        if (mappings.isEmpty()) return formatted

        if (mappings.size == 1) {
            val mapping = mappings[0]
            val quotedSentinelToken = "\"${mapping.sentinelToken}\""
            val firstMatch = formatted.indexOf(quotedSentinelToken)
            if (firstMatch < 0) return formatted
            val result = StringBuilder(formatted.length)
            var cursor = 0
            var matchIndex = firstMatch
            while (matchIndex >= 0) {
                result.append(formatted, cursor, matchIndex)
                result.append(mapping.originalPlaceholder)
                cursor = matchIndex + quotedSentinelToken.length
                matchIndex = formatted.indexOf(quotedSentinelToken, cursor)
            }
            result.append(formatted, cursor, formatted.length)
            return result.toString()
        }

        val commonPrefix = findCommonSentinelPrefix(mappings)
        if (commonPrefix.isEmpty()) {
            var restoredResult = formatted
            for (placeholderMapping in mappings) {
                val quotedSentinelToken = "\"${placeholderMapping.sentinelToken}\""
                restoredResult = restoredResult.replace(quotedSentinelToken, placeholderMapping.originalPlaceholder)
            }
            return restoredResult
        }

        val quotedPrefix = "\"$commonPrefix"
        val firstMatch = formatted.indexOf(quotedPrefix)
        if (firstMatch < 0) return formatted

        val lookup = HashMap<String, String>(mappings.size * 2)
        var maxTokenLength = 0
        for (mapping in mappings) {
            lookup[mapping.sentinelToken] = mapping.originalPlaceholder
            if (mapping.sentinelToken.length > maxTokenLength) {
                maxTokenLength = mapping.sentinelToken.length
            }
        }

        val result = StringBuilder(formatted.length)
        var cursor = 0
        var searchIndex = firstMatch
        while (searchIndex >= 0) {
            val tokenStart = searchIndex + 1
            val maxClosingQuoteIndex = minOf(tokenStart + maxTokenLength, formatted.length - 1)
            val closingQuoteIndex = findClosingQuoteWithin(formatted, tokenStart + commonPrefix.length, maxClosingQuoteIndex)
            if (closingQuoteIndex > tokenStart) {
                val candidateToken = formatted.substring(tokenStart, closingQuoteIndex)
                val replacement = lookup[candidateToken]
                if (replacement != null) {
                    result.append(formatted, cursor, searchIndex)
                    result.append(replacement)
                    cursor = closingQuoteIndex + 1
                    searchIndex = formatted.indexOf(quotedPrefix, cursor)
                    continue
                }
            }
            searchIndex = formatted.indexOf(quotedPrefix, searchIndex + 1)
        }

        if (cursor == 0) return formatted
        result.append(formatted, cursor, formatted.length)
        return result.toString()
    }

    private fun findCommonSentinelPrefix(mappings: List<PlaceholderMapping>): String {
        val firstToken = mappings[0].sentinelToken
        var prefixLength = firstToken.length
        for (index in 1 until mappings.size) {
            val token = mappings[index].sentinelToken
            val limit = minOf(prefixLength, token.length)
            var matched = 0
            while (matched < limit && firstToken[matched] == token[matched]) {
                matched++
            }
            prefixLength = matched
            if (prefixLength == 0) return ""
        }
        return firstToken.substring(0, prefixLength)
    }

    private fun findClosingQuoteWithin(text: String, startIndex: Int, maxIndexInclusive: Int): Int {
        var index = startIndex
        while (index <= maxIndexInclusive) {
            if (text[index] == '"') return index
            index++
        }
        return -1
    }

    private fun createSentinelPrefix(): String {
        val uniqueIdentifier = UUID.randomUUID().toString().replace("-", "")
        return "__JSONINJA_PLACEHOLDER_${uniqueIdentifier}_"
    }

    private fun normalizePlaceholderText(placeholderText: String): String {
        if (!placeholderText.startsWith(PLACEHOLDER_OPEN) || !placeholderText.endsWith(PLACEHOLDER_CLOSE)) {
            return placeholderText
        }

        val innerText = placeholderText.substring(
            PLACEHOLDER_OPEN.length,
            placeholderText.length - PLACEHOLDER_CLOSE.length
        )
        return PLACEHOLDER_OPEN + innerText.trim() + PLACEHOLDER_CLOSE
    }

    private fun startsWithPlaceholderOpen(input: CharSequence, index: Int): Boolean {
        return index + 1 < input.length && input[index] == '{' && input[index + 1] == '{'
    }

    private fun findPlaceholderEndIndex(input: CharSequence, startIndex: Int): Int {
        var currentIndex = startIndex
        while (currentIndex < input.length - 1) {
            if (input[currentIndex] == '}' && input[currentIndex + 1] == '}') {
                return currentIndex
            }
            currentIndex++
        }
        return -1
    }

    private fun startsWithLineComment(input: CharSequence, index: Int): Boolean {
        return index + 1 < input.length && input[index] == '/' && input[index + 1] == '/'
    }

    private fun startsWithBlockComment(input: CharSequence, index: Int): Boolean {
        return index + 1 < input.length && input[index] == '/' && input[index + 1] == '*'
    }

    private fun findLineCommentEndIndex(input: CharSequence, startIndex: Int): Int {
        var currentIndex = startIndex
        while (currentIndex < input.length) {
            if (input[currentIndex] == '\n') {
                return currentIndex
            }
            currentIndex++
        }
        return input.length
    }

    private fun findBlockCommentEndIndex(input: CharSequence, startIndex: Int): Int {
        var currentIndex = startIndex + 2
        while (currentIndex < input.length - 1) {
            if (input[currentIndex] == '*' && input[currentIndex + 1] == '/') {
                return currentIndex
            }
            currentIndex++
        }
        return -1
    }

    private fun findStringEndIndex(input: CharSequence, startIndex: Int, quoteCharacter: Char): Int {
        var currentIndex = startIndex + 1
        var isEscaped = false

        while (currentIndex < input.length) {
            val currentCharacter = input[currentIndex]
            if (currentCharacter == '\\' && !isEscaped) {
                isEscaped = true
                currentIndex++
                continue
            }

            if (currentCharacter == quoteCharacter && !isEscaped) {
                return currentIndex
            }

            isEscaped = false
            currentIndex++
        }

        return -1
    }

    private fun findNextSignificantCharacter(input: CharSequence, startIndex: Int): Char? {
        var currentIndex = startIndex

        while (currentIndex < input.length) {
            val currentCharacter = input[currentIndex]

            if (currentCharacter.isWhitespace()) {
                currentIndex++
                continue
            }

            if (startsWithLineComment(input, currentIndex)) {
                currentIndex = findLineCommentEndIndex(input, currentIndex)
                continue
            }

            if (startsWithBlockComment(input, currentIndex)) {
                val blockCommentEndIndex = findBlockCommentEndIndex(input, currentIndex)
                if (blockCommentEndIndex == -1) {
                    return null
                }
                currentIndex = blockCommentEndIndex + 2
                continue
            }

            return currentCharacter
        }

        return null
    }
}
