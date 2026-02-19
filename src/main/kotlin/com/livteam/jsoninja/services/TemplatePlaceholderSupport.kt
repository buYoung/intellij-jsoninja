package com.livteam.jsoninja.services

import java.util.UUID

data class PlaceholderMapping(
    val originalPlaceholder: String,
    val sentinelToken: String,
    val originalStartIndex: Int = -1,
    val originalEndIndex: Int = -1
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

    fun hasValuePlaceholders(input: String): Boolean {
        val replacementResult = extractAndReplaceValuePlaceholders(input)
        return replacementResult.isSuccessful && replacementResult.mappings.isNotEmpty()
    }

    fun extractAndReplaceValuePlaceholders(input: String): ReplacementResult {
        if (input.isEmpty()) {
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
                    replacedBuilder.append(input.substring(currentIndex))
                    break
                }

                val stringLiteral = input.substring(currentIndex, stringEndIndex + 1)
                replacedBuilder.append(stringLiteral)
                previousSignificantCharacter = stringLiteral.lastOrNull()
                currentIndex = stringEndIndex + 1
                continue
            }

            if (startsWithLineComment(input, currentIndex)) {
                val lineCommentEndIndex = findLineCommentEndIndex(input, currentIndex)
                replacedBuilder.append(input.substring(currentIndex, lineCommentEndIndex))
                currentIndex = lineCommentEndIndex
                continue
            }

            if (startsWithBlockComment(input, currentIndex)) {
                val blockCommentEndIndex = findBlockCommentEndIndex(input, currentIndex)
                if (blockCommentEndIndex == -1) {
                    replacedBuilder.append(input.substring(currentIndex))
                    break
                }

                replacedBuilder.append(input.substring(currentIndex, blockCommentEndIndex + 2))
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

                val placeholderText = input.substring(currentIndex, placeholderEndIndex + PLACEHOLDER_CLOSE.length)
                val nextSignificantCharacter = findNextSignificantCharacter(
                    input = input,
                    startIndex = placeholderEndIndex + PLACEHOLDER_CLOSE.length
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
                            originalEndIndex = placeholderEndIndex + PLACEHOLDER_CLOSE.length
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

                currentIndex = placeholderEndIndex + PLACEHOLDER_CLOSE.length
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

        var restoredResult = formatted
        for (placeholderMapping in mappings) {
            val quotedSentinelToken = "\"${placeholderMapping.sentinelToken}\""
            restoredResult = restoredResult.replace(quotedSentinelToken, placeholderMapping.originalPlaceholder)
        }
        return restoredResult
    }

    private fun createSentinelPrefix(): String {
        val uniqueIdentifier = UUID.randomUUID().toString().replace("-", "")
        return "__JSONINJA_PLACEHOLDER_${uniqueIdentifier}_"
    }

    private fun startsWithPlaceholderOpen(input: String, index: Int): Boolean {
        if (index + PLACEHOLDER_OPEN.length > input.length) return false
        return input.regionMatches(index, PLACEHOLDER_OPEN, 0, PLACEHOLDER_OPEN.length)
    }

    private fun findPlaceholderEndIndex(input: String, startIndex: Int): Int {
        var currentIndex = startIndex
        while (currentIndex < input.length - 1) {
            if (input[currentIndex] == '}' && input[currentIndex + 1] == '}') {
                return currentIndex
            }
            currentIndex++
        }
        return -1
    }

    private fun startsWithLineComment(input: String, index: Int): Boolean {
        return index + 1 < input.length && input[index] == '/' && input[index + 1] == '/'
    }

    private fun startsWithBlockComment(input: String, index: Int): Boolean {
        return index + 1 < input.length && input[index] == '/' && input[index + 1] == '*'
    }

    private fun findLineCommentEndIndex(input: String, startIndex: Int): Int {
        var currentIndex = startIndex
        while (currentIndex < input.length) {
            if (input[currentIndex] == '\n') {
                return currentIndex
            }
            currentIndex++
        }
        return input.length
    }

    private fun findBlockCommentEndIndex(input: String, startIndex: Int): Int {
        var currentIndex = startIndex + 2
        while (currentIndex < input.length - 1) {
            if (input[currentIndex] == '*' && input[currentIndex + 1] == '/') {
                return currentIndex
            }
            currentIndex++
        }
        return -1
    }

    private fun findStringEndIndex(input: String, startIndex: Int, quoteCharacter: Char): Int {
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

    private fun findNextSignificantCharacter(input: String, startIndex: Int): Char? {
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
