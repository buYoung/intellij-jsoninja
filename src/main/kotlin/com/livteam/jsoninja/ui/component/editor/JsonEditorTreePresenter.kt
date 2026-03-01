package com.livteam.jsoninja.ui.component.editor

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.components.service
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.TemplatePlaceholderSupport
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class JsonEditorTreePresenter(
    private val view: JsonEditorTreeView
) {
    companion object {
        private const val TREE_ROOT_LABEL = "ROOT"
        private const val TREE_VALUE_PREFIX = "value"
    }

    private val objectMapper = service<JsonObjectMapperService>().objectMapper

    fun refreshTreeFromJson(jsonText: String) {
        val treeRootNode = DefaultMutableTreeNode(TREE_ROOT_LABEL)

        try {
            val replacementResult = TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders(jsonText)
            if (!replacementResult.isSuccessful) {
                throw IllegalArgumentException("Invalid placeholder syntax")
            }

            val placeholderLookup = replacementResult.mappings.associate { placeholderMapping ->
                placeholderMapping.sentinelToken to placeholderMapping.originalPlaceholder
            }
            val jsonNode = objectMapper.readTree(replacementResult.replacedText.trim())
            appendJsonNode(
                parentNode = treeRootNode,
                label = null,
                jsonNode = jsonNode,
                placeholderLookup = placeholderLookup
            )
        } catch (_: Exception) {
            treeRootNode.add(
                DefaultMutableTreeNode(LocalizationBundle.message("dialog.json.diff.invalid.json.format"))
            )
        }

        view.setTreeModel(DefaultTreeModel(treeRootNode))
    }

    private fun appendJsonNode(
        parentNode: DefaultMutableTreeNode,
        label: String?,
        jsonNode: JsonNode,
        placeholderLookup: Map<String, String>
    ) {
        when {
            jsonNode.isObject -> appendObjectNode(parentNode, label, jsonNode, placeholderLookup)
            jsonNode.isArray -> appendArrayNode(parentNode, label, jsonNode, placeholderLookup)
            else -> appendValueNode(parentNode, label ?: TREE_VALUE_PREFIX, jsonNode, placeholderLookup)
        }
    }

    private fun appendObjectNode(
        parentNode: DefaultMutableTreeNode,
        label: String?,
        objectNode: JsonNode,
        placeholderLookup: Map<String, String>
    ) {
        if (label == null) {
            val fieldNamesIterator = objectNode.fieldNames()
            while (fieldNamesIterator.hasNext()) {
                val fieldName = fieldNamesIterator.next()
                appendJsonNode(parentNode, fieldName, objectNode.get(fieldName), placeholderLookup)
            }
            return
        }

        val objectLabelNode = DefaultMutableTreeNode(label)
        parentNode.add(objectLabelNode)

        val fieldNamesIterator = objectNode.fieldNames()
        while (fieldNamesIterator.hasNext()) {
            val fieldName = fieldNamesIterator.next()
            appendJsonNode(objectLabelNode, fieldName, objectNode.get(fieldName), placeholderLookup)
        }
    }

    private fun appendArrayNode(
        parentNode: DefaultMutableTreeNode,
        label: String?,
        arrayNode: JsonNode,
        placeholderLookup: Map<String, String>
    ) {
        for (index in 0 until arrayNode.size()) {
            val arrayItemNode = arrayNode.get(index)
            val arrayItemLabel = if (label.isNullOrEmpty()) {
                "[$index]"
            } else {
                "$label[$index]"
            }

            when {
                arrayItemNode.isValueNode -> appendValueNode(
                    parentNode,
                    arrayItemLabel,
                    arrayItemNode,
                    placeholderLookup
                )
                arrayItemNode.isObject -> {
                    val arrayObjectNode = DefaultMutableTreeNode(arrayItemLabel)
                    parentNode.add(arrayObjectNode)
                    val fieldNamesIterator = arrayItemNode.fieldNames()
                    while (fieldNamesIterator.hasNext()) {
                        val fieldName = fieldNamesIterator.next()
                        appendJsonNode(arrayObjectNode, fieldName, arrayItemNode.get(fieldName), placeholderLookup)
                    }
                }

                arrayItemNode.isArray -> {
                    val nestedArrayNode = DefaultMutableTreeNode(arrayItemLabel)
                    parentNode.add(nestedArrayNode)
                    appendArrayNode(nestedArrayNode, null, arrayItemNode, placeholderLookup)
                }
            }
        }
    }

    private fun appendValueNode(
        parentNode: DefaultMutableTreeNode,
        label: String,
        valueNode: JsonNode,
        placeholderLookup: Map<String, String>
    ) {
        val valueText = when {
            valueNode.isNull -> "null"
            valueNode.isTextual -> {
                val rawText = valueNode.textValue()
                placeholderLookup[rawText] ?: rawText
            }
            else -> valueNode.toString()
        }
        parentNode.add(DefaultMutableTreeNode("$label : $valueText"))
    }
}
