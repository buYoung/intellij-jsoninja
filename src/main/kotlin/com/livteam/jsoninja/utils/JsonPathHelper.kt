package com.livteam.jsoninja.utils

import com.intellij.json.JsonFileType
import com.intellij.json.psi.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.util.LocalTimeCounter
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.services.PlaceholderMapping
import com.livteam.jsoninja.services.TemplatePlaceholderSupport

data class TemplatePathResult(
    val path: String,
    val isInsidePlaceholder: Boolean
)

sealed interface JsonPathSegment {
    data class Property(val name: String) : JsonPathSegment
    data class ArrayIndex(val index: Int) : JsonPathSegment
}

object JsonPathHelper {

    private val IDENTIFIER_REGEX = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

    fun getJsonPath(element: PsiElement): String? {
        return getPath(element, JsonQueryType.JAYWAY_JSONPATH)
    }

    fun getJmesPath(element: PsiElement): String? {
        return getPath(element, JsonQueryType.JMESPATH)
    }

    fun getJqPath(element: PsiElement): String? {
        return getPath(element, JsonQueryType.JACKSON_JQ)
    }

    fun getPath(element: PsiElement, queryType: JsonQueryType): String? {
        val segments = collectPathSegments(element) ?: return null
        return formatPathSegments(segments, queryType, wildcardArrays = false)
    }

    fun getArrayWildcardPath(element: PsiElement, queryType: JsonQueryType): String? {
        val segments = collectPathSegments(element) ?: return null
        if (!hasArrayIndexSegment(segments)) return null
        return formatPathSegments(segments, queryType, wildcardArrays = true)
    }

    fun hasArrayIndexSegment(segments: List<JsonPathSegment>): Boolean {
        return segments.any { it is JsonPathSegment.ArrayIndex }
    }

    fun formatPathSegments(
        segments: List<JsonPathSegment>,
        queryType: JsonQueryType,
        wildcardArrays: Boolean = false,
    ): String {
        if (segments.isEmpty()) return rootPath(queryType)

        val parts = StringBuilder()
        for (segment in segments) {
            when (segment) {
                is JsonPathSegment.Property -> appendPropertyPath(parts, segment.name, queryType)
                is JsonPathSegment.ArrayIndex -> {
                    if (wildcardArrays) {
                        parts.append(wildcardArrayToken(queryType))
                    } else {
                        parts.append('[').append(segment.index).append(']')
                    }
                }
            }
        }

        val path = parts.toString()
        return when (queryType) {
            JsonQueryType.JMESPATH -> path.removePrefix(".")
            JsonQueryType.JACKSON_JQ -> "." + path.removePrefix(".")
            JsonQueryType.JAYWAY_JSONPATH -> "$" + path
        }
    }

    private fun wildcardArrayToken(queryType: JsonQueryType): String = when (queryType) {
        JsonQueryType.JAYWAY_JSONPATH,
        JsonQueryType.JMESPATH -> "[*]"
        JsonQueryType.JACKSON_JQ -> "[]"
    }

    private fun collectPathSegments(element: PsiElement): List<JsonPathSegment>? {
        var current: PsiElement? = element

        // Navigate up from leaf tokens to a structural JSON element
        while (current != null && current !is JsonValue && current !is JsonProperty && current !is JsonFile) {
            current = current.parent
        }

        if (current == null) return null
        if (current is JsonFile) return emptyList()

        val reversedSegments = mutableListOf<JsonPathSegment>()

        while (current != null) {
            if (current is JsonFile) break

            val parent = current.parent

            if (current is JsonProperty) {
                // We are at a property. This edge (Parent -> Property) defines the key.
                reversedSegments.add(JsonPathSegment.Property(current.name))
            } else if (parent is JsonArray && current is JsonValue) {
                // We are a value in an array. This edge (Array -> Value) defines the index.
                val index = parent.valueList.indexOf(current)
                if (index >= 0) {
                    reversedSegments.add(JsonPathSegment.ArrayIndex(index))
                }
            }

            current = parent
        }

        return reversedSegments.asReversed()
    }

    private fun rootPath(queryType: JsonQueryType): String = when (queryType) {
        JsonQueryType.JAYWAY_JSONPATH -> "$"
        JsonQueryType.JMESPATH -> "@"
        JsonQueryType.JACKSON_JQ -> "."
    }

    private fun appendPropertyPath(parts: StringBuilder, name: String, queryType: JsonQueryType) {
        if (needsQuotes(name)) {
            val quotedName = "\"${StringUtil.escapeStringCharacters(name)}\""
            if (queryType == JsonQueryType.JAYWAY_JSONPATH) {
                parts.append('[').append(quotedName).append(']')
            } else {
                parts.append('.').append(quotedName)
            }
        } else {
            parts.append('.').append(name)
        }
    }

    fun getPathFromTemplateText(
        documentText: String,
        offset: Int,
        project: Project,
        queryType: JsonQueryType
    ): TemplatePathResult? {
        val (element, isInsidePlaceholder) = resolveTemplateElement(documentText, offset, project) ?: return null
        val path = getPath(element, queryType) ?: return null
        return TemplatePathResult(path, isInsidePlaceholder)
    }

    fun getArrayWildcardPathFromTemplateText(
        documentText: String,
        offset: Int,
        project: Project,
        queryType: JsonQueryType
    ): TemplatePathResult? {
        val (element, isInsidePlaceholder) = resolveTemplateElement(documentText, offset, project) ?: return null
        val path = getArrayWildcardPath(element, queryType) ?: return null
        return TemplatePathResult(path, isInsidePlaceholder)
    }

    private fun resolveTemplateElement(
        documentText: String,
        offset: Int,
        project: Project,
    ): Pair<PsiElement, Boolean>? {
        val result = TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders(documentText)
        if (!result.isSuccessful || result.mappings.isEmpty()) return null

        val mapping = result.mappings.find { offset in it.originalStartIndex until it.originalEndIndex }
        val isInsidePlaceholder = mapping != null

        val targetOffset = if (mapping != null) {
            val sentinelIndex = result.replacedText.indexOf(mapping.sentinelToken)
            if (sentinelIndex == -1) return null
            sentinelIndex
        } else {
            mapOriginalOffsetToReplaced(offset, result.mappings) ?: return null
        }

        if (targetOffset < 0 || targetOffset >= result.replacedText.length) return null

        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "template_tooltip.json",
            JsonFileType.INSTANCE,
            result.replacedText,
            LocalTimeCounter.currentTime(),
            false
        )

        val element = psiFile.findElementAt(targetOffset) ?: return null
        return element to isInsidePlaceholder
    }

    private fun mapOriginalOffsetToReplaced(offset: Int, mappings: List<PlaceholderMapping>): Int? {
        var adjustedOffset = offset
        for (mapping in mappings.sortedBy { it.originalStartIndex }) {
            if (offset < mapping.originalStartIndex) break
            if (offset in mapping.originalStartIndex until mapping.originalEndIndex) return null
            val originalLength = mapping.originalEndIndex - mapping.originalStartIndex
            val replacementLength = mapping.sentinelToken.length + 2 // +2 for quotes
            adjustedOffset += (replacementLength - originalLength)
        }
        return adjustedOffset
    }

    private fun needsQuotes(name: String): Boolean {
        // Simple check: if it contains anything other than alphanumeric or underscore, or starts with digit
        // This is a simplification but covers most cases.
        return !IDENTIFIER_REGEX.matches(name)
    }
}
