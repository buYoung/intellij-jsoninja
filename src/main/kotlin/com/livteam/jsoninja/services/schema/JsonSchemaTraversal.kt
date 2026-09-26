package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.SpecVersion.VersionFlag
import com.networknt.schema.SpecVersionDetector

/** Schema positions shared by reference, anchor and constraint traversal. Instance payloads stay opaque. */
internal object JsonSchemaTraversal {
    private val schemaKeywords = setOf(
        "additionalProperties", "unevaluatedProperties", "propertyNames", "contains",
        "additionalItems", "unevaluatedItems", "not", "if", "then", "else", "contentSchema"
    )
    private val schemaMapKeywords = setOf(
        "\$defs", "definitions", "properties", "patternProperties", "dependentSchemas", "dependencies"
    )
    private val schemaArrayKeywords = setOf("allOf", "anyOf", "oneOf", "prefixItems")

    fun dialect(schemaNode: JsonNode, inherited: VersionFlag = VersionFlag.V202012): VersionFlag =
        SpecVersionDetector.detectOptionalVersion(schemaNode, true).orElse(inherited)

    fun mapChildren(
        schemaNode: JsonNode,
        dialect: VersionFlag,
        transform: (JsonNode, List<String>) -> JsonNode
    ): JsonNode {
        if (schemaNode !is ObjectNode) return schemaNode.deepCopy()
        val result = schemaNode.deepCopy()
        forEachChild(schemaNode, dialect) { child, path ->
            val replacement = transform(child, path)
            if (path.size == 1) result.set<JsonNode>(path[0], replacement) else {
                when (val container = result.path(path[0])) {
                    is ObjectNode -> container.set<JsonNode>(path[1], replacement)
                    is ArrayNode -> container.set(path[1].toInt(), replacement)
                }
            }
        }
        return result
    }

    fun forEachChild(schemaNode: JsonNode, dialect: VersionFlag, action: (JsonNode, List<String>) -> Unit) {
        if (!schemaNode.isObject) return
        for ((keyword, value) in schemaNode.properties()) {
            if (!supports(keyword, dialect)) continue
            when {
                keyword in schemaKeywords || keyword == "items" && !value.isArray -> {
                    if (isSchema(value)) action(value, listOf(keyword))
                }
                keyword in schemaMapKeywords && value is ObjectNode -> {
                    for ((name, child) in value.properties()) {
                        if (isSchema(child)) action(child, listOf(keyword, name))
                    }
                }
                (keyword in schemaArrayKeywords || keyword == "items" && dialect != VersionFlag.V202012) && value is ArrayNode -> {
                    value.forEachIndexed { index, child ->
                        if (isSchema(child)) action(child, listOf(keyword, index.toString()))
                    }
                }
            }
        }
    }

    private fun supports(keyword: String, dialect: VersionFlag): Boolean = when (keyword) {
        "prefixItems" -> dialect == VersionFlag.V202012
        "additionalItems" -> dialect != VersionFlag.V202012
        "\$defs", "dependentSchemas", "unevaluatedItems", "unevaluatedProperties", "contentSchema" ->
            dialect == VersionFlag.V201909 || dialect == VersionFlag.V202012
        "contains", "propertyNames" -> dialect != VersionFlag.V4
        "if", "then", "else" -> dialect != VersionFlag.V4 && dialect != VersionFlag.V6
        else -> true
    }

    private fun isSchema(node: JsonNode): Boolean = node.isObject || node.isBoolean
}
