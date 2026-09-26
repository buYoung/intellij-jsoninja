package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.SpecificationVersion
import com.livteam.jsoninja.LocalizationBundle

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

    fun dialect(
        schemaNode: JsonNode,
        inherited: SpecificationVersion = SpecificationVersion.DRAFT_2020_12
    ): SpecificationVersion {
        val schemaId = schemaNode.get("\$schema") ?: return inherited
        // Standard dialects accept both HTTP/HTTPS and an optional empty fragment.
        // Inspect only the identifier: converting the subtree here would make traversal quadratic.
        val normalizedId = schemaId.asText().removeSuffix("#")
        return SpecificationVersion.entries.firstOrNull {
            val standardPath = it.dialectId.removeSuffix("#").substringAfter("://")
            schemaId.isTextual && (normalizedId == "http://$standardPath" || normalizedId == "https://$standardPath")
        } ?: throw JsonSchemaGenerationException(
            LocalizationBundle.message("validation.error.schema.dialect.unsupported", schemaId.asText()),
            "#/\$schema"
        )
    }

    fun mapChildren(
        schemaNode: JsonNode,
        dialect: SpecificationVersion,
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

    fun forEachChild(schemaNode: JsonNode, dialect: SpecificationVersion, action: (JsonNode, List<String>) -> Unit) {
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
                (keyword in schemaArrayKeywords || keyword == "items" && dialect != SpecificationVersion.DRAFT_2020_12) && value is ArrayNode -> {
                    value.forEachIndexed { index, child ->
                        if (isSchema(child)) action(child, listOf(keyword, index.toString()))
                    }
                }
            }
        }
    }

    private fun supports(keyword: String, dialect: SpecificationVersion): Boolean = when (keyword) {
        "prefixItems" -> dialect == SpecificationVersion.DRAFT_2020_12
        "additionalItems" -> dialect != SpecificationVersion.DRAFT_2020_12
        "\$defs", "dependentSchemas", "unevaluatedItems", "unevaluatedProperties", "contentSchema" ->
            dialect == SpecificationVersion.DRAFT_2019_09 || dialect == SpecificationVersion.DRAFT_2020_12
        "contains", "propertyNames" -> dialect != SpecificationVersion.DRAFT_4
        "if", "then", "else" -> dialect != SpecificationVersion.DRAFT_4 && dialect != SpecificationVersion.DRAFT_6
        else -> true
    }

    private fun isSchema(node: JsonNode): Boolean = node.isObject || node.isBoolean
}
