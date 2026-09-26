package com.livteam.jsoninja.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.JsonDiffAlignmentOptions
import com.livteam.jsoninja.model.JsonDiffAlignmentResult
import com.livteam.jsoninja.model.JsonDiffAlignmentStatus
import com.livteam.jsoninja.model.JsonDiffSortMode
import com.livteam.jsoninja.model.JsonFormatState
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * JSON Diff 양쪽 텍스트를 함께 보고 키와 배열 순서를 맞추는 서비스
 * Document나 diff 세션을 변경하지 않고 결과 텍스트만 계산하며, 호출자가 Dispatchers.Default에서 실행합니다.
 */
@Service(Service.Level.PROJECT)
class JsonDiffAlignmentService(private val project: Project) {
    private val LOG = logger<JsonDiffAlignmentService>()
    private val formatterService: JsonFormatterService
        get() = project.service<JsonFormatterService>()
    private val objectMapper = service<JsonObjectMapperService>().objectMapper

    suspend fun align(
        leftText: String,
        rightText: String,
        options: JsonDiffAlignmentOptions
    ): JsonDiffAlignmentResult {
        val coroutineContext = currentCoroutineContext()
        return try {
            if (options.sortMode == JsonDiffSortMode.KEY_ASCENDING && !options.shouldIgnoreArrayOrder) {
                sortIndependently(leftText, rightText, coroutineContext)
            } else {
                alignPair(leftText, rightText, options, coroutineContext)
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: Exception) {
            LOG.warn("JSON diff alignment failed (${exception.javaClass.simpleName})")
            createUnavailableResult(leftText, rightText)
        }
    }

    /**
     * 기존 사전순 정렬과 같이 각 쪽을 독립적으로 포맷합니다. 유효하지 않은 쪽은 원본을 유지합니다.
     */
    private fun sortIndependently(
        leftText: String,
        rightText: String,
        coroutineContext: CoroutineContext
    ): JsonDiffAlignmentResult {
        val isLeftValid = isValidInput(leftText)
        val isRightValid = isValidInput(rightText)
        if (!isLeftValid && !isRightValid) {
            return createUnavailableResult(leftText, rightText)
        }

        coroutineContext.ensureActive()
        val sortedLeftText = if (isLeftValid) formatSorted(leftText) else leftText
        coroutineContext.ensureActive()
        val sortedRightText = if (isRightValid) formatSorted(rightText) else rightText
        return createResult(leftText, rightText, sortedLeftText, sortedRightText)
    }

    /**
     * 왼쪽을 기준으로 오른쪽 구조를 맞춥니다. 한쪽이라도 유효하지 않으면 두 텍스트를 그대로 반환합니다.
     */
    private fun alignPair(
        leftText: String,
        rightText: String,
        options: JsonDiffAlignmentOptions,
        coroutineContext: CoroutineContext
    ): JsonDiffAlignmentResult {
        val placeholderTokens = PlaceholderTokenTable()
        val leftNode = parseInput(leftText, placeholderTokens) ?: return createUnavailableResult(leftText, rightText)
        val rightNode = parseInput(rightText, placeholderTokens) ?: return createUnavailableResult(leftText, rightText)

        coroutineContext.ensureActive()
        val alignedRightNode = alignNode(leftNode, rightNode, options, coroutineContext)
        val usesKeySorting = options.sortMode == JsonDiffSortMode.KEY_ASCENDING
        val alignedRightText = writeFormatted(alignedRightNode, usesKeySorting, placeholderTokens)
            ?: return createUnavailableResult(leftText, rightText)

        coroutineContext.ensureActive()
        val alignedLeftText = if (usesKeySorting) formatSorted(leftText) else leftText
        return createResult(
            originalLeftText = leftText,
            originalRightText = rightText,
            alignedLeftText = alignedLeftText,
            alignedRightText = keepOriginalWhenEquivalent(rightText, alignedRightText)
        )
    }

    private fun alignNode(
        referenceNode: JsonNode?,
        targetNode: JsonNode,
        options: JsonDiffAlignmentOptions,
        coroutineContext: CoroutineContext
    ): JsonNode {
        coroutineContext.ensureActive()
        return when {
            referenceNode is ObjectNode && targetNode is ObjectNode ->
                alignObject(referenceNode, targetNode, options, coroutineContext)

            referenceNode is ArrayNode && targetNode is ArrayNode ->
                alignArray(referenceNode, targetNode, options, coroutineContext)

            else -> targetNode
        }
    }

    private fun alignObject(
        referenceNode: ObjectNode,
        targetNode: ObjectNode,
        options: JsonDiffAlignmentOptions,
        coroutineContext: CoroutineContext
    ): ObjectNode {
        val targetFieldNames = targetNode.fieldNames().asSequence().toList()
        val orderedFieldNames = if (options.sortMode == JsonDiffSortMode.LEFT_ORDER) {
            // 공통 키는 왼쪽 순서로, 오른쪽에만 있는 키는 원래 상대 순서로 뒤에 둔다.
            val sharedFieldNames = referenceNode.fieldNames().asSequence().filter { targetNode.has(it) }.toList()
            sharedFieldNames + targetFieldNames.filterNot { referenceNode.has(it) }
        } else {
            targetFieldNames
        }

        val alignedNode = objectMapper.nodeFactory.objectNode()
        orderedFieldNames.forEach { fieldName ->
            alignedNode.set<JsonNode>(
                fieldName,
                alignNode(referenceNode.get(fieldName), targetNode.get(fieldName), options, coroutineContext)
            )
        }
        return alignedNode
    }

    private fun alignArray(
        referenceNode: ArrayNode,
        targetNode: ArrayNode,
        options: JsonDiffAlignmentOptions,
        coroutineContext: CoroutineContext
    ): ArrayNode {
        val alignedNode = objectMapper.nodeFactory.arrayNode(targetNode.size())
        if (!options.shouldIgnoreArrayOrder) {
            targetNode.forEachIndexed { index, element ->
                alignedNode.add(alignNode(referenceNode.get(index), element, options, coroutineContext))
            }
            return alignedNode
        }

        // JsonNode 동등성은 객체 필드 순서를 무시하고 중첩 배열 순서와 값 타입은 유지한다.
        val unmatchedIndexesByElement = HashMap<JsonNode, ArrayDeque<Int>>()
        targetNode.forEachIndexed { index, element ->
            coroutineContext.ensureActive()
            unmatchedIndexesByElement.getOrPut(element) { ArrayDeque() }.addLast(index)
        }

        val isMatchedByIndex = BooleanArray(targetNode.size())
        referenceNode.forEach { referenceElement ->
            coroutineContext.ensureActive()
            val matchedIndex = unmatchedIndexesByElement[referenceElement]?.removeFirstOrNull() ?: return@forEach
            isMatchedByIndex[matchedIndex] = true
            alignedNode.add(alignNode(referenceElement, targetNode.get(matchedIndex), options, coroutineContext))
        }

        // 일치하지 않은 요소는 식별자를 추정하지 않고 원래 상대 순서로 뒤에 붙인다.
        targetNode.forEachIndexed { index, element ->
            if (!isMatchedByIndex[index]) alignedNode.add(element)
        }
        return alignedNode
    }

    /**
     * JsonFormatterService.formatJson과 같은 unescape/placeholder/검증 경로로 입력을 해석합니다.
     * 양쪽의 같은 placeholder가 같은 값으로 비교되도록 sentinel을 공통 token으로 바꿉니다.
     */
    private fun parseInput(text: String, placeholderTokens: PlaceholderTokenTable): JsonNode? {
        if (text.isBlank()) return null

        val unescapedText = formatterService.fullyUnescapeJson(text)
        val replacementResult = TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders(unescapedText)
        if (!replacementResult.isSuccessful) return null
        if (!formatterService.isValidJson(replacementResult.replacedText)) return null

        val parsedNode = objectMapper.readTree(replacementResult.replacedText) ?: return null
        if (replacementResult.mappings.isEmpty()) return parsedNode

        val sharedTokensBySentinel = replacementResult.mappings.associate { mapping ->
            mapping.sentinelToken to placeholderTokens.getToken(mapping.originalPlaceholder)
        }
        return replaceSentinelTokens(parsedNode, sharedTokensBySentinel)
    }

    private fun replaceSentinelTokens(node: JsonNode, sharedTokensBySentinel: Map<String, String>): JsonNode {
        return when {
            node.isTextual -> sharedTokensBySentinel[node.textValue()]
                ?.let { objectMapper.nodeFactory.textNode(it) }
                ?: node

            node is ObjectNode -> {
                val fieldNames = node.fieldNames().asSequence().toList()
                fieldNames.forEach { fieldName ->
                    node.set<JsonNode>(fieldName, replaceSentinelTokens(node.get(fieldName), sharedTokensBySentinel))
                }
                node
            }

            node is ArrayNode -> {
                for (index in 0 until node.size()) {
                    node.set(index, replaceSentinelTokens(node.get(index), sharedTokensBySentinel))
                }
                node
            }

            else -> node
        }
    }

    private fun writeFormatted(
        node: JsonNode,
        usesKeySorting: Boolean,
        placeholderTokens: PlaceholderTokenTable
    ): String? {
        val compactText = objectMapper.writeValueAsString(node)
        val formattedText = formatterService.formatJson(compactText, JsonFormatState.PRETTIFY, usesKeySorting)
        if (placeholderTokens.isEmpty()) return formattedText

        val restoredText = TemplatePlaceholderSupport.restorePlaceholders(formattedText, placeholderTokens.mappings)
        return restoredText.takeUnless { it.contains(placeholderTokens.tokenPrefix) }
    }

    private fun formatSorted(text: String): String {
        return keepOriginalWhenEquivalent(text, formatterService.formatJson(text, JsonFormatState.PRETTIFY, true))
    }

    private fun isValidInput(text: String): Boolean {
        return formatterService.isValidJson(formatterService.fullyUnescapeJson(text))
    }

    /**
     * 앞뒤 공백만 다른 결과는 변경으로 보지 않고 원본을 유지합니다.
     */
    private fun keepOriginalWhenEquivalent(originalText: String, formattedText: String): String {
        return if (formattedText == originalText.trim()) originalText else formattedText
    }

    private fun createResult(
        originalLeftText: String,
        originalRightText: String,
        alignedLeftText: String,
        alignedRightText: String
    ): JsonDiffAlignmentResult {
        val isChanged = alignedLeftText != originalLeftText || alignedRightText != originalRightText
        return JsonDiffAlignmentResult(
            leftText = alignedLeftText,
            rightText = alignedRightText,
            status = if (isChanged) JsonDiffAlignmentStatus.APPLIED else JsonDiffAlignmentStatus.NO_CHANGE
        )
    }

    private fun createUnavailableResult(leftText: String, rightText: String): JsonDiffAlignmentResult {
        return JsonDiffAlignmentResult(leftText, rightText, JsonDiffAlignmentStatus.INVALID_INPUT)
    }

    /**
     * 원본 placeholder 텍스트별로 양쪽이 공유하는 sentinel token
     */
    private class PlaceholderTokenTable {
        val tokenPrefix = "__JSONINJA_DIFF_PLACEHOLDER_${UUID.randomUUID().toString().replace("-", "")}_"
        private val tokensByPlaceholder = LinkedHashMap<String, String>()

        val mappings: List<PlaceholderMapping>
            get() = tokensByPlaceholder.map { (originalPlaceholder, token) ->
                PlaceholderMapping(
                    originalPlaceholder = originalPlaceholder,
                    sentinelToken = token,
                    originalStartIndex = -1,
                    originalEndIndex = -1
                )
            }

        fun getToken(originalPlaceholder: String): String {
            return tokensByPlaceholder.getOrPut(originalPlaceholder) {
                tokenPrefix + tokensByPlaceholder.size + "__"
            }
        }

        fun isEmpty(): Boolean = tokensByPlaceholder.isEmpty()
    }
}
