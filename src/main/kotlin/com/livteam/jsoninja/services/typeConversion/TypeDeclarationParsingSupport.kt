package com.livteam.jsoninja.services.typeConversion

object TypeDeclarationParsingSupport {
    private val lineCommentPattern = Regex("//.*?$", setOf(RegexOption.MULTILINE))
    private val blockCommentPattern = Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL))

    fun stripComments(sourceCode: String): String {
        return sourceCode
            .replace(blockCommentPattern, "")
            .replace(lineCommentPattern, "")
    }

    fun normalizeWhitespace(sourceCode: String): String {
        return stripComments(sourceCode)
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n")
    }

    fun extractGenericTypeArguments(typeText: String): List<String> {
        val startIndex = typeText.indexOf('<')
        val endIndex = typeText.lastIndexOf('>')
        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex + 1) {
            return emptyList()
        }
        return typeText.substring(startIndex + 1, endIndex)
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
    }
}
