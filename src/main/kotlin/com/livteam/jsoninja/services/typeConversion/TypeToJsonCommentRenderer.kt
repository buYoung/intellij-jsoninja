package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.livteam.jsoninja.model.JsonFormatState

internal class TypeToJsonCommentRenderer(private val objectMapper: ObjectMapper) {
    fun render(
        document: JsonNode,
        optionalFields: Map<ObjectNode, Set<String>>,
        formatState: JsonFormatState,
    ): String = renderNode(document, optionalFields, formatState, 0)

    private fun renderNode(
        node: JsonNode,
        optionalFields: Map<ObjectNode, Set<String>>,
        formatState: JsonFormatState,
        depth: Int,
    ): String {
        val isMinified = formatState == JsonFormatState.UGLIFY
        if (node is ObjectNode) {
            val fields = node.properties().toList().let { entries ->
                if (formatState.usesSorting()) entries.sortedBy { it.key } else entries
            }
            if (fields.isEmpty()) return "{}"
            val indentation = "  ".repeat(depth + 1)
            val lines = fields.mapIndexed { index, field ->
                val isOptional = field.key in optionalFields[node].orEmpty()
                val value = if (isOptional && isMinified) {
                    encodeJson(field.value)
                } else renderNode(field.value, optionalFields, formatState, depth + 1)
                val separator = if (isMinified) ":" else ": "
                val suffix = if (index < fields.lastIndex) "," else ""
                val property = encodeJson(field.key) + separator + value + suffix
                when {
                    isOptional && isMinified -> "/*" + property.replace("*/", "*\\/") + "*/"
                    isOptional -> (indentation + property).lines().joinToString("\n") { line ->
                        indentation + "// " + line.removePrefix(indentation)
                    }
                    isMinified -> property
                    else -> indentation + property
                }
            }
            return if (isMinified) "{" + lines.joinToString("") + "}" else {
                "{\n" + lines.joinToString("\n") + "\n" + "  ".repeat(depth) + "}"
            }
        }
        if (node.isArray) {
            if (node.isEmpty) return "[]"
            val elements = node.map { renderNode(it, optionalFields, formatState, depth + 1) }
            if (isMinified || formatState.usesCompactArrays() && elements.none { it.contains('\n') }) {
                return elements.joinToString(if (isMinified) "," else ", ", "[", "]")
            }
            val indentation = "  ".repeat(depth + 1)
            return "[\n" + elements.joinToString(",\n") { indentation + it } + "\n" + "  ".repeat(depth) + "]"
        }
        return encodeJson(node)
    }
    private fun encodeJson(value: Any): String = objectMapper.writeValueAsString(value)
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")

}

