package com.livteam.jsoninja.ui.component.editor

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.TemplatePlaceholderSupport
import com.livteam.jsoninja.utils.JsonPathHelper
import com.livteam.jsoninja.utils.JsonPathSegment
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
                val buildResult = withContext(Dispatchers.Default) {
                    buildTreeModel(jsonText)
                }

                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (project.isDisposed || isDisposed || sequence != refreshSequence.get() || !isCurrentDocument()) {
                        return@withContext
                    }
                    when (buildResult) {
                        is TreeBuildResult.Success -> view.setTreeModel(buildResult.model)
                        TreeBuildResult.Empty -> view.setEmptyState()
                        is TreeBuildResult.InvalidJson -> view.setInvalidJsonState(buildResult.line, buildResult.column)
                    }
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

    private fun buildTreeModel(jsonText: String): TreeBuildResult {
        if (jsonText.isBlank()) {
            return TreeBuildResult.Empty
        }

        return try {
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
            val rootNode = createRootTreeNode(jsonNode, placeholderLookup)
            TreeBuildResult.Success(DefaultTreeModel(rootNode))
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: JsonProcessingException) {
            val location = exception.location
            TreeBuildResult.InvalidJson(
                line = location?.lineNr?.takeIf { it > 0 },
                column = location?.columnNr?.takeIf { it > 0 },
            )
        } catch (_: Exception) {
            TreeBuildResult.InvalidJson(line = null, column = null)
        }
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
                keyText = TREE_ROOT_LABEL,
                rawKeyName = null,
                pathSegment = null,
                objectNode = jsonNode,
                placeholderLookup = placeholderLookup,
            )
            jsonNode.isArray -> ArrayContainerJsonTreeNode(
                parentNode = null,
                indexInParent = 0,
                depth = 0,
                keyText = TREE_ROOT_LABEL,
                rawKeyName = null,
                pathSegment = null,
                arrayNode = jsonNode,
                placeholderLookup = placeholderLookup,
            )
            else -> SingleChildJsonTreeNode(
                displayText = TREE_ROOT_LABEL,
                childKeyText = TREE_VALUE_PREFIX,
                childJsonNode = jsonNode,
                placeholderLookup = placeholderLookup,
            )
        }
    }

    private sealed interface TreeBuildResult {
        data class Success(val model: DefaultTreeModel) : TreeBuildResult
        data object Empty : TreeBuildResult
        data class InvalidJson(val line: Int?, val column: Int?) : TreeBuildResult
    }
}

internal enum class JsonTreeValueKind {
    OBJECT,
    ARRAY,
    EMPTY_OBJECT,
    EMPTY_ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    PLACEHOLDER,
}

internal sealed interface JsonTreePathSegment {
    data class Property(val name: String) : JsonTreePathSegment
    data class ArrayIndex(val index: Int) : JsonTreePathSegment
    data class PropertyArrayIndex(val name: String, val index: Int) : JsonTreePathSegment
}

internal sealed class LazyJsonTreeNode(
    val parentNode: LazyJsonTreeNode?,
    val indexInParent: Int,
    val depth: Int,
    val keyText: String,
    val rawKeyName: String?,
    val valueText: String?,
    val rawCopyValue: String?,
    val valueKind: JsonTreeValueKind,
    val containerCount: Int,
    val jsonNode: JsonNode?,
    val pathSegment: JsonTreePathSegment?,
    val displayText: String,
) : TreeNode {
    override fun getParent(): TreeNode? = parentNode

    override fun toString(): String = displayText

    fun isLastSibling(): Boolean {
        val parent = parentNode ?: return true
        return indexInParent == parent.childCount - 1
    }

    fun buildQueryPath(queryType: JsonQueryType): String {
        return JsonPathHelper.formatPathSegments(
            segments = collectJsonPathSegments(),
            queryType = queryType,
            wildcardArrays = false,
        )
    }

    fun buildArrayWildcardQueryPath(queryType: JsonQueryType): String? {
        val segments = collectJsonPathSegments()
        if (!JsonPathHelper.hasArrayIndexSegment(segments)) {
            return null
        }
        return JsonPathHelper.formatPathSegments(
            segments = segments,
            queryType = queryType,
            wildcardArrays = true,
        )
    }

    private fun collectJsonPathSegments(): List<JsonPathSegment> {
        val treeSegments = ArrayList<JsonTreePathSegment>()
        var current: LazyJsonTreeNode? = this
        while (current != null) {
            current.pathSegment?.let(treeSegments::add)
            current = current.parentNode
        }

        if (treeSegments.isEmpty()) return emptyList()

        val result = ArrayList<JsonPathSegment>(treeSegments.size)
        for (i in treeSegments.lastIndex downTo 0) {
            when (val segment = treeSegments[i]) {
                is JsonTreePathSegment.Property -> result.add(JsonPathSegment.Property(segment.name))
                is JsonTreePathSegment.ArrayIndex -> result.add(JsonPathSegment.ArrayIndex(segment.index))
                is JsonTreePathSegment.PropertyArrayIndex -> {
                    result.add(JsonPathSegment.Property(segment.name))
                    result.add(JsonPathSegment.ArrayIndex(segment.index))
                }
            }
        }
        return result
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
    keyText: String,
    rawKeyName: String?,
    valueText: String,
    rawCopyValue: String,
    valueKind: JsonTreeValueKind,
    jsonNode: JsonNode?,
    pathSegment: JsonTreePathSegment?,
) : LazyJsonTreeNode(
    parentNode = parentNode,
    indexInParent = indexInParent,
    depth = depth,
    keyText = keyText,
    rawKeyName = rawKeyName,
    valueText = valueText,
    rawCopyValue = rawCopyValue,
    valueKind = valueKind,
    containerCount = 0,
    jsonNode = jsonNode,
    pathSegment = pathSegment,
    displayText = "$keyText : $valueText",
) {
    override fun getChildCount(): Int = 0

    override fun getAllowsChildren(): Boolean = false

    override fun isLeaf(): Boolean = true

    override fun getChildAt(childIndex: Int): TreeNode {
        throw IndexOutOfBoundsException("Leaf node has no children: index=$childIndex")
    }
}

private class SingleChildJsonTreeNode(
    displayText: String,
    private val childKeyText: String,
    private val childJsonNode: JsonNode,
    private val placeholderLookup: Map<String, String>,
) : LazyJsonTreeNode(
    parentNode = null,
    indexInParent = 0,
    depth = 0,
    keyText = displayText,
    rawKeyName = null,
    valueText = null,
    rawCopyValue = null,
    valueKind = JsonTreeValueKind.OBJECT,
    containerCount = 1,
    jsonNode = childJsonNode,
    pathSegment = null,
    displayText = displayText,
) {
    private var childNode: LeafJsonTreeNode? = null

    override fun getChildCount(): Int = 1

    override fun getAllowsChildren(): Boolean = true

    override fun isLeaf(): Boolean = false

    override fun getChildAt(childIndex: Int): TreeNode {
        if (childIndex != 0) {
            throw IndexOutOfBoundsException("Index $childIndex out of bounds for length 1")
        }
        return childNode ?: createScalarLeafNode(
            parentNode = this,
            indexInParent = 0,
            depth = depth + 1,
            keyText = childKeyText,
            rawKeyName = null,
            valueNode = childJsonNode,
            pathSegment = null,
            placeholderLookup = placeholderLookup,
        ).also { childNode = it }
    }
}

private class ArrayContainerJsonTreeNode(
    parentNode: LazyJsonTreeNode?,
    indexInParent: Int,
    depth: Int,
    keyText: String,
    rawKeyName: String?,
    pathSegment: JsonTreePathSegment?,
    private val arrayNode: JsonNode,
    private val placeholderLookup: Map<String, String>,
) : LazyJsonTreeNode(
    parentNode = parentNode,
    indexInParent = indexInParent,
    depth = depth,
    keyText = keyText,
    rawKeyName = rawKeyName,
    valueText = null,
    rawCopyValue = if (arrayNode.isEmpty) "[]" else null,
    valueKind = if (arrayNode.isEmpty) JsonTreeValueKind.EMPTY_ARRAY else JsonTreeValueKind.ARRAY,
    containerCount = arrayNode.size(),
    jsonNode = arrayNode,
    pathSegment = pathSegment,
    displayText = if (arrayNode.isEmpty) "$keyText : []" else "$keyText [${arrayNode.size()}]",
) {
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
                    keyText = LAZY_TREE_VALUE_PREFIX,
                    rawKeyName = null,
                    valueText = "[]",
                    rawCopyValue = "[]",
                    valueKind = JsonTreeValueKind.EMPTY_ARRAY,
                    jsonNode = arrayNode,
                    pathSegment = null,
                )
            } else {
                createArrayElementNode(
                    parentNode = this,
                    indexInParent = childIndex,
                    depth = depth + 1,
                    itemLabel = "[$childIndex]",
                    rawKeyName = "[$childIndex]",
                    pathSegment = JsonTreePathSegment.ArrayIndex(childIndex),
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
    keyText: String,
    rawKeyName: String?,
    pathSegment: JsonTreePathSegment?,
    private val objectNode: JsonNode,
    private val placeholderLookup: Map<String, String>,
) : LazyJsonTreeNode(
    parentNode = parentNode,
    indexInParent = indexInParent,
    depth = depth,
    keyText = keyText,
    rawKeyName = rawKeyName,
    valueText = if (objectNode.isEmpty) "{}" else null,
    rawCopyValue = if (objectNode.isEmpty) "{}" else null,
    valueKind = if (objectNode.isEmpty) JsonTreeValueKind.EMPTY_OBJECT else JsonTreeValueKind.OBJECT,
    containerCount = objectNode.size(),
    jsonNode = objectNode,
    pathSegment = pathSegment,
    displayText = if (objectNode.isEmpty) "$keyText : {}" else "$keyText {${objectNode.size()}}",
) {
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
                    keyText = fieldName,
                    rawKeyName = fieldName,
                    pathSegment = JsonTreePathSegment.Property(fieldName),
                    objectNode = fieldNode,
                    placeholderLookup = placeholderLookup,
                )
                fieldNode.isArray -> {
                    if (fieldNode.isEmpty) {
                        LeafJsonTreeNode(
                            parentNode = this,
                            indexInParent = childIndex,
                            depth = depth + 1,
                            keyText = fieldName,
                            rawKeyName = fieldName,
                            valueText = "[]",
                            rawCopyValue = "[]",
                            valueKind = JsonTreeValueKind.EMPTY_ARRAY,
                            jsonNode = fieldNode,
                            pathSegment = JsonTreePathSegment.Property(fieldName),
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
                            rawKeyName = fieldName.ifEmpty { "[$arrayIndex]" },
                            pathSegment = JsonTreePathSegment.PropertyArrayIndex(fieldName, arrayIndex),
                            itemNode = fieldNode.get(arrayIndex),
                            placeholderLookup = placeholderLookup,
                        )
                    }
                }
                else -> createScalarLeafNode(
                    parentNode = this,
                    indexInParent = childIndex,
                    depth = depth + 1,
                    keyText = fieldName,
                    rawKeyName = fieldName,
                    valueNode = fieldNode,
                    pathSegment = JsonTreePathSegment.Property(fieldName),
                    placeholderLookup = placeholderLookup,
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
    rawKeyName: String?,
    pathSegment: JsonTreePathSegment?,
    itemNode: JsonNode,
    placeholderLookup: Map<String, String>,
): LazyJsonTreeNode {
    return when {
        itemNode.isObject -> ObjectContainerJsonTreeNode(
            parentNode = parentNode,
            indexInParent = indexInParent,
            depth = depth,
            keyText = itemLabel,
            rawKeyName = rawKeyName,
            pathSegment = pathSegment,
            objectNode = itemNode,
            placeholderLookup = placeholderLookup,
        )
        itemNode.isArray -> ArrayContainerJsonTreeNode(
            parentNode = parentNode,
            indexInParent = indexInParent,
            depth = depth,
            keyText = itemLabel,
            rawKeyName = rawKeyName,
            pathSegment = pathSegment,
            arrayNode = itemNode,
            placeholderLookup = placeholderLookup,
        )
        else -> createScalarLeafNode(
            parentNode = parentNode,
            indexInParent = indexInParent,
            depth = depth,
            keyText = itemLabel,
            rawKeyName = rawKeyName,
            valueNode = itemNode,
            pathSegment = pathSegment,
            placeholderLookup = placeholderLookup,
        )
    }
}

private fun createScalarLeafNode(
    parentNode: LazyJsonTreeNode,
    indexInParent: Int,
    depth: Int,
    keyText: String,
    rawKeyName: String?,
    valueNode: JsonNode,
    pathSegment: JsonTreePathSegment?,
    placeholderLookup: Map<String, String>,
): LeafJsonTreeNode {
    val (valueText, rawCopyValue, kind) = when {
        valueNode.isNull -> Triple("null", "null", JsonTreeValueKind.NULL)
        valueNode.isBoolean -> {
            val boolText = valueNode.booleanValue().toString()
            Triple(boolText, boolText, JsonTreeValueKind.BOOLEAN)
        }
        valueNode.isNumber -> {
            val numberText = valueNode.asText()
            Triple(numberText, numberText, JsonTreeValueKind.NUMBER)
        }
        valueNode.isTextual -> {
            val rawText = valueNode.textValue()
            val placeholder = placeholderLookup[rawText]
            if (placeholder != null) {
                Triple(placeholder, placeholder, JsonTreeValueKind.PLACEHOLDER)
            } else {
                Triple("\"$rawText\"", rawText, JsonTreeValueKind.STRING)
            }
        }
        else -> {
            val text = valueNode.toString()
            Triple(text, text, JsonTreeValueKind.STRING)
        }
    }

    return LeafJsonTreeNode(
        parentNode = parentNode,
        indexInParent = indexInParent,
        depth = depth,
        keyText = keyText,
        rawKeyName = rawKeyName,
        valueText = valueText,
        rawCopyValue = rawCopyValue,
        valueKind = kind,
        jsonNode = valueNode,
        pathSegment = pathSegment,
    )
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
