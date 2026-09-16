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
        var current: PsiElement? = element
        
        // Navigate up from leaf tokens to a structural JSON element
        while (current != null && current !is JsonValue && current !is JsonProperty && current !is JsonFile) {
            current = current.parent
        }
        
        if (current == null) return null
        if (current is JsonFile) return rootPath(queryType)

        val parts = mutableListOf<String>()
        
        while (current != null) {
            if (current is JsonFile) break
            
            val parent = current.parent
            
            if (current is JsonProperty) {
                // We are at a property. This edge (Parent -> Property) defines the key.
                val name = current.name
                addPropertyPath(parts, name, queryType)
            } else if (parent is JsonArray && current is JsonValue) {
                // We are a value in an array. This edge (Array -> Value) defines the index.
                val index = parent.valueList.indexOf(current)
                if (index >= 0) {
                    parts.add("[$index]")
                }
            }
            
            current = parent
        }
        
        if (parts.isEmpty()) return rootPath(queryType)

        val path = parts.asReversed().joinToString("")
        
        return when (queryType) {
            JsonQueryType.JMESPATH -> path.removePrefix(".")
            JsonQueryType.JACKSON_JQ -> "." + path.removePrefix(".")
            JsonQueryType.JAYWAY_JSONPATH -> "$" + path
        }
    }

    private fun rootPath(queryType: JsonQueryType): String = when (queryType) {
        JsonQueryType.JAYWAY_JSONPATH -> "$"
        JsonQueryType.JMESPATH -> "@"
        JsonQueryType.JACKSON_JQ -> "."
    }

    private fun addPropertyPath(parts: MutableList<String>, name: String, queryType: JsonQueryType) {
        if (needsQuotes(name)) {
            val quotedName = "\"${StringUtil.escapeStringCharacters(name)}\""
            parts.add(if (queryType == JsonQueryType.JAYWAY_JSONPATH) "[$quotedName]" else ".$quotedName")
        } else {
            parts.add(".$name")
        }
    }

    fun getPathFromTemplateText(
        documentText: String,
        offset: Int,
        project: Project,
        queryType: JsonQueryType
    ): TemplatePathResult? {
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
        val path = getPath(element, queryType) ?: return null
        return TemplatePathResult(path, isInsidePlaceholder)
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
