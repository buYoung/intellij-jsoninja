package com.livteam.jsoninja.ui.component.editor

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.components.service
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonObjectMapperService
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
            val jsonNode = objectMapper.readTree(jsonText.trim())
            appendJsonNode(parentNode = treeRootNode, label = null, jsonNode = jsonNode)
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
        jsonNode: JsonNode
    ) {
        when {
            jsonNode.isObject -> appendObjectNode(parentNode, label, jsonNode)
            jsonNode.isArray -> appendArrayNode(parentNode, label, jsonNode)
            else -> appendValueNode(parentNode, label ?: TREE_VALUE_PREFIX, jsonNode)
        }
    }

    private fun appendObjectNode(
        parentNode: DefaultMutableTreeNode,
        label: String?,
        objectNode: JsonNode
    ) {
        if (label == null) {
            val fieldNamesIterator = objectNode.fieldNames()
            while (fieldNamesIterator.hasNext()) {
                val fieldName = fieldNamesIterator.next()
                appendJsonNode(parentNode, fieldName, objectNode.get(fieldName))
            }
            return
        }

        val objectLabelNode = DefaultMutableTreeNode(label)
        parentNode.add(objectLabelNode)

        val fieldNamesIterator = objectNode.fieldNames()
        while (fieldNamesIterator.hasNext()) {
            val fieldName = fieldNamesIterator.next()
            appendJsonNode(objectLabelNode, fieldName, objectNode.get(fieldName))
        }
    }

    private fun appendArrayNode(
        parentNode: DefaultMutableTreeNode,
        label: String?,
        arrayNode: JsonNode
    ) {
        for (index in 0 until arrayNode.size()) {
            val arrayItemNode = arrayNode.get(index)
            val arrayItemLabel = if (label.isNullOrEmpty()) {
                "[$index]"
            } else {
                "$label[$index]"
            }

            when {
                arrayItemNode.isValueNode -> appendValueNode(parentNode, arrayItemLabel, arrayItemNode)
                arrayItemNode.isObject -> {
                    val arrayObjectNode = DefaultMutableTreeNode(arrayItemLabel)
                    parentNode.add(arrayObjectNode)
                    val fieldNamesIterator = arrayItemNode.fieldNames()
                    while (fieldNamesIterator.hasNext()) {
                        val fieldName = fieldNamesIterator.next()
                        appendJsonNode(arrayObjectNode, fieldName, arrayItemNode.get(fieldName))
                    }
                }

                arrayItemNode.isArray -> {
                    val nestedArrayNode = DefaultMutableTreeNode(arrayItemLabel)
                    parentNode.add(nestedArrayNode)
                    appendArrayNode(nestedArrayNode, null, arrayItemNode)
                }
            }
        }
    }

    private fun appendValueNode(
        parentNode: DefaultMutableTreeNode,
        label: String,
        valueNode: JsonNode
    ) {
        val valueText = when {
            valueNode.isNull -> "null"
            valueNode.isTextual -> valueNode.textValue()
            else -> valueNode.toString()
        }
        parentNode.add(DefaultMutableTreeNode("$label : $valueText"))
    }
}
