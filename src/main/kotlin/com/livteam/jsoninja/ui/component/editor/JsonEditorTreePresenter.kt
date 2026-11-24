package com.livteam.jsoninja.ui.component.editor

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.TemplatePlaceholderSupport
import java.util.Collections
import java.util.Enumeration
import java.util.NoSuchElementException
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JsonEditorTreePresenter(
    private val project: Project,
    private val view: JsonEditorTreeView
) {
    companion object {
        private const val TREE_ROOT_LABEL = "ROOT"
        private const val TREE_VALUE_PREFIX = "value"
        private const val PLACEHOLDER_OPEN = "{{"
    }

    private val objectMapperService = service<JsonObjectMapperService>()
    private val coroutineScope: CoroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    private var refreshTreeJob: Job? = null
    private val refreshSequence = AtomicInteger()
    private var isDisposed = false

    fun refreshTreeFromJson(jsonText: String) {
        refreshTreeFromJson(jsonText) { true }
    }

    fun refreshTreeFromJson(jsonText: String, isCurrentDocument: () -> Boolean) {
        if (isDisposed) return
        val sequence = refreshSequence.incrementAndGet()
        refreshTreeJob?.cancel()
        refreshTreeJob = coroutineScope.launch {
            try {
                val treeModel = withContext(Dispatchers.Default) {
                    buildTreeModel(jsonText)
                }

                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (project.isDisposed || isDisposed || sequence != refreshSequence.get() || !isCurrentDocument()) {
                        return@withContext
                    }
                    view.setTreeModel(treeModel)
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            }
        }
    }

    fun cancelRefresh() {
        refreshSequence.incrementAndGet()
        refreshTreeJob?.cancel()
        refreshTreeJob = null
    }

    fun dispose() {
        isDisposed = true
        cancelRefresh()
        coroutineScope.cancel()
    }

    private fun buildTreeModel(jsonText: String): DefaultTreeModel {
        val rootNode: LazyJsonTreeNode = try {
            val textToParse: String
            val placeholderLookup: Map<String, String>
            if (jsonText.contains(PLACEHOLDER_OPEN)) {
                val replacementResult = TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders(jsonText)
                if (!replacementResult.isSuccessful) {
                    throw IllegalArgumentException("Invalid placeholder syntax")
                }
                textToParse = replacementResult.replacedText
                placeholderLookup = if (replacementResult.mappings.isEmpty()) {
                    emptyMap()
                } else {
                    replacementResult.mappings.associate { placeholderMapping ->
                        placeholderMapping.sentinelToken to placeholderMapping.originalPlaceholder
                    }
                }
            } else {
                textToParse = jsonText
                placeholderLookup = emptyMap()
            }

            val jsonNode = objectMapperService.readTreeFast(textToParse)
            createRootTreeNode(jsonNode, placeholderLookup)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (_: Exception) {
            SingleChildJsonTreeNode(
                displayText = TREE_ROOT_LABEL,
                childDisplayText = LocalizationBundle.message("dialog.json.diff.invalid.json.format"),
            )
        }

        return DefaultTreeModel(rootNode)
    }

    private fun createRootTreeNode(
        jsonNode: JsonNode,
        placeholderLookup: Map<String, String>,
    ): LazyJsonTreeNode {
        return when {
            jsonNode.isObject -> ObjectContainerJsonTreeNode(
                parentNode = null,
                indexInParent = 0,
                depth = 0,
                displayText = TREE_ROOT_LABEL,
                objectNode = jsonNode,
                placeholderLookup = placeholderLookup,
            )
            jsonNode.isArray -> ArrayContainerJsonTreeNode(
                parentNode = null,
                indexInParent = 0,
                depth = 0,
                displayText = TREE_ROOT_LABEL,
                arrayNode = jsonNode,
                placeholderLookup = placeholderLookup,
            )
            else -> SingleChildJsonTreeNode(
                displayText = TREE_ROOT_LABEL,
                childDisplayText = formatValueLabel(TREE_VALUE_PREFIX, jsonNode, placeholderLookup),
            )
        }
    }
}

internal sealed class LazyJsonTreeNode(
    val parentNode: LazyJsonTreeNode?,
    val indexInParent: Int,
    val depth: Int,
    val displayText: String,
) : TreeNode {
    override fun getParent(): TreeNode? = parentNode

    override fun toString(): String = displayText

    fun isLastSibling(): Boolean {
        val parent = parentNode ?: return true
        return indexInParent == parent.childCount - 1
    }

    override fun getIndex(node: TreeNode?): Int {
        val child = node as? LazyJsonTreeNode ?: return -1
        return if (child.parentNode === this) child.indexInParent else -1
    }

    override fun children(): Enumeration<out TreeNode> {
        val count = childCount
        if (count == 0) {
            return Collections.emptyEnumeration()
        }
        return object : Enumeration<TreeNode> {
            private var currentIndex = 0

            override fun hasMoreElements(): Boolean = currentIndex < count

            override fun nextElement(): TreeNode {
                if (currentIndex >= count) {
                    throw NoSuchElementException()
                }
                return getChildAt(currentIndex++)
            }
        }
    }
}

private class LazyChildCache(private val size: Int) {
    private var dense: Array<LazyJsonTreeNode?>? = null
    private var sparse: HashMap<Int, LazyJsonTreeNode>? = null

    fun getOrCreate(index: Int, factory: () -> LazyJsonTreeNode): LazyJsonTreeNode {
        if (size <= DENSE_CHILD_CACHE_LIMIT) {
            val array = dense ?: arrayOfNulls<LazyJsonTreeNode>(size).also { dense = it }
            return array[index] ?: factory().also { array[index] = it }
        }
        val map = sparse ?: HashMap<Int, LazyJsonTreeNode>().also { sparse = it }
        return map.getOrPut(index, factory)
    }

    companion object {
        private const val DENSE_CHILD_CACHE_LIMIT = 256
    }
}

private class LeafJsonTreeNode(
    parentNode: LazyJsonTreeNode?,
    indexInParent: Int,
    depth: Int,
    displayText: String,
) : LazyJsonTreeNode(parentNode, indexInParent, depth, displayText) {
    override fun getChildCount(): Int = 0

    override fun getAllowsChildren(): Boolean = false

    override fun isLeaf(): Boolean = true

    override fun getChildAt(childIndex: Int): TreeNode {
        throw IndexOutOfBoundsException("Leaf node has no children: index=$childIndex")
    }
}

private class SingleChildJsonTreeNode(
    displayText: String,
    private val childDisplayText: String,
) : LazyJsonTreeNode(parentNode = null, indexInParent = 0, depth = 0, displayText = displayText) {
    private var childNode: LeafJsonTreeNode? = null

    override fun getChildCount(): Int = 1

    override fun getAllowsChildren(): Boolean = true

    override fun isLeaf(): Boolean = false

    override fun getChildAt(childIndex: Int): TreeNode {
        if (childIndex != 0) {
            throw IndexOutOfBoundsException("Index $childIndex out of bounds for length 1")
        }
        return childNode ?: LeafJsonTreeNode(
            parentNode = this,
            indexInParent = 0,
            depth = depth + 1,
            displayText = childDisplayText,
        ).also { childNode = it }
    }
}

private class ArrayContainerJsonTreeNode(
    parentNode: LazyJsonTreeNode?,
    indexInParent: Int,
    depth: Int,
    displayText: String,
    private val arrayNode: JsonNode,
    private val placeholderLookup: Map<String, String>,
) : LazyJsonTreeNode(parentNode, indexInParent, depth, displayText) {
    private val totalChildCount: Int = if (arrayNode.isEmpty) 1 else arrayNode.size()
    private var childCache: LazyChildCache? = null

    override fun getChildCount(): Int = totalChildCount

    override fun getAllowsChildren(): Boolean = true

    override fun isLeaf(): Boolean = false

    override fun getChildAt(childIndex: Int): TreeNode {
        if (childIndex < 0 || childIndex >= totalChildCount) {
            throw IndexOutOfBoundsException("Index $childIndex out of bounds for length $totalChildCount")
        }
        val cache = childCache ?: LazyChildCache(totalChildCount).also { childCache = it }
        return cache.getOrCreate(childIndex) {
            if (arrayNode.isEmpty) {
                LeafJsonTreeNode(
                    parentNode = this,
                    indexInParent = 0,
                    depth = depth + 1,
                    displayText = "$LAZY_TREE_VALUE_PREFIX : []",
                )
            } else {
                createArrayElementNode(
                    parentNode = this,
                    indexInParent = childIndex,
                    depth = depth + 1,
                    itemLabel = "[$childIndex]",
                    itemNode = arrayNode.get(childIndex),
                    placeholderLookup = placeholderLookup,
                )
            }
        }
    }
}

private class ObjectContainerJsonTreeNode(
    parentNode: LazyJsonTreeNode?,
    indexInParent: Int,
    depth: Int,
    displayText: String,
    private val objectNode: JsonNode,
    private val placeholderLookup: Map<String, String>,
) : LazyJsonTreeNode(parentNode, indexInParent, depth, displayText) {
    private class FieldLayout(
        val fieldNames: Array<String>,
        val fieldNodes: Array<JsonNode>,
        val cumulativeEndIndices: IntArray,
        val totalChildCount: Int,
    )

    private var layout: FieldLayout? = null
    private var childCache: LazyChildCache? = null

    override fun getChildCount(): Int {
        if (objectNode.isEmpty) {
            return 0
        }
        return getOrBuildLayout().totalChildCount
    }

    override fun getAllowsChildren(): Boolean = !objectNode.isEmpty

    override fun isLeaf(): Boolean = objectNode.isEmpty

    override fun getChildAt(childIndex: Int): TreeNode {
        val fieldLayout = getOrBuildLayout()
        if (childIndex < 0 || childIndex >= fieldLayout.totalChildCount) {
            throw IndexOutOfBoundsException("Index $childIndex out of bounds for length ${fieldLayout.totalChildCount}")
        }
        val cache = childCache ?: LazyChildCache(fieldLayout.totalChildCount).also { childCache = it }
        return cache.getOrCreate(childIndex) {
            val fieldIndex = findFieldIndex(fieldLayout.cumulativeEndIndices, childIndex)
            val fieldName = fieldLayout.fieldNames[fieldIndex]
            val fieldNode = fieldLayout.fieldNodes[fieldIndex]
            val rangeStart = if (fieldIndex == 0) 0 else fieldLayout.cumulativeEndIndices[fieldIndex - 1]
            when {
                fieldNode.isObject -> ObjectContainerJsonTreeNode(
                    parentNode = this,
                    indexInParent = childIndex,
                    depth = depth + 1,
                    displayText = fieldName,
                    objectNode = fieldNode,
                    placeholderLookup = placeholderLookup,
                )
                fieldNode.isArray -> {
                    if (fieldNode.isEmpty) {
                        LeafJsonTreeNode(
                            parentNode = this,
                            indexInParent = childIndex,
                            depth = depth + 1,
                            displayText = "$fieldName : []",
                        )
                    } else {
                        val arrayIndex = childIndex - rangeStart
                        val arrayItemLabel = if (fieldName.isEmpty()) {
                            "[$arrayIndex]"
                        } else {
                            "$fieldName[$arrayIndex]"
                        }
                        createArrayElementNode(
                            parentNode = this,
                            indexInParent = childIndex,
                            depth = depth + 1,
                            itemLabel = arrayItemLabel,
                            itemNode = fieldNode.get(arrayIndex),
                            placeholderLookup = placeholderLookup,
                        )
                    }
                }
                else -> LeafJsonTreeNode(
                    parentNode = this,
                    indexInParent = childIndex,
                    depth = depth + 1,
                    displayText = formatValueLabel(fieldName, fieldNode, placeholderLookup),
                )
            }
        }
    }

    private fun getOrBuildLayout(): FieldLayout {
        layout?.let { return it }
        val fieldCount = objectNode.size()
        val fieldNames = Array(fieldCount) { "" }
        val fieldNodes = arrayOfNulls<JsonNode>(fieldCount)
        val cumulativeEndIndices = IntArray(fieldCount)
        var index = 0
        var totalChildren = 0
        val fieldNamesIterator = objectNode.fieldNames()
        while (fieldNamesIterator.hasNext() && index < fieldCount) {
            val fieldName = fieldNamesIterator.next()
            val fieldNode = objectNode.get(fieldName)
            fieldNames[index] = fieldName
            fieldNodes[index] = fieldNode
            val span = if (fieldNode.isArray && !fieldNode.isEmpty) fieldNode.size() else 1
            totalChildren += span
            cumulativeEndIndices[index] = totalChildren
            index += 1
        }
        @Suppress("UNCHECKED_CAST")
        return FieldLayout(
            fieldNames = fieldNames,
            fieldNodes = fieldNodes as Array<JsonNode>,
            cumulativeEndIndices = cumulativeEndIndices,
            totalChildCount = totalChildren,
        ).also { layout = it }
    }
}

private const val LAZY_TREE_VALUE_PREFIX = "value"

private fun createArrayElementNode(
    parentNode: LazyJsonTreeNode,
    indexInParent: Int,
    depth: Int,
    itemLabel: String,
    itemNode: JsonNode,
    placeholderLookup: Map<String, String>,
): LazyJsonTreeNode {
    return when {
        itemNode.isObject -> ObjectContainerJsonTreeNode(
            parentNode = parentNode,
            indexInParent = indexInParent,
            depth = depth,
            displayText = itemLabel,
            objectNode = itemNode,
            placeholderLookup = placeholderLookup,
        )
        itemNode.isArray -> ArrayContainerJsonTreeNode(
            parentNode = parentNode,
            indexInParent = indexInParent,
            depth = depth,
            displayText = itemLabel,
            arrayNode = itemNode,
            placeholderLookup = placeholderLookup,
        )
        else -> LeafJsonTreeNode(
            parentNode = parentNode,
            indexInParent = indexInParent,
            depth = depth,
            displayText = formatValueLabel(itemLabel, itemNode, placeholderLookup),
        )
    }
}

private fun formatValueLabel(
    label: String,
    valueNode: JsonNode,
    placeholderLookup: Map<String, String>,
): String {
    val valueText = when {
        valueNode.isNull -> "null"
        valueNode.isTextual -> {
            val rawText = valueNode.textValue()
            placeholderLookup[rawText] ?: rawText
        }
        else -> valueNode.toString()
    }
    return "$label : $valueText"
}

private fun findFieldIndex(cumulativeEndIndices: IntArray, childIndex: Int): Int {
    var low = 0
    var high = cumulativeEndIndices.lastIndex
    while (low < high) {
        val mid = (low + high) ushr 1
        if (cumulativeEndIndices[mid] <= childIndex) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    return low
}
