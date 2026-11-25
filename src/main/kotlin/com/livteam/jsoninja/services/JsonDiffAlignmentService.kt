package com.livteam.jsoninja.services

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val objectMapperService by lazy { service<JsonObjectMapperService>() }
    private val standardObjectMapper: ObjectMapper
        get() = objectMapperService.standardObjectMapper
    private val objectMapper: ObjectMapper
        get() = objectMapperService.objectMapper

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
        coroutineContext.ensureActive()
        val formattedLeft = tryFormatSortedSinglePass(leftText)
        coroutineContext.ensureActive()
        val formattedRight = tryFormatSortedSinglePass(rightText)
        if (formattedLeft == null && formattedRight == null) {
            return createUnavailableResult(leftText, rightText)
        }

        val sortedLeftText = if (formattedLeft != null) {
            keepOriginalWhenEquivalent(leftText, formattedLeft)
        } else {
            leftText
        }
        val sortedRightText = if (formattedRight != null) {
            keepOriginalWhenEquivalent(rightText, formattedRight)
        } else {
            rightText
        }
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
        coroutineContext.ensureActive()
        val rightNode = parseInput(rightText, placeholderTokens) ?: return createUnavailableResult(leftText, rightText)

        coroutineContext.ensureActive()
        val alignedRightNode = alignNode(leftNode, rightNode, options, coroutineContext)
        val usesKeySorting = options.sortMode == JsonDiffSortMode.KEY_ASCENDING
        val alignedRightText = writeFormatted(alignedRightNode, usesKeySorting, placeholderTokens)
            ?: return createUnavailableResult(leftText, rightText)

        coroutineContext.ensureActive()
        val alignedLeftText = if (usesKeySorting) {
            val formattedLeftText = writeFormatted(leftNode, true, placeholderTokens)
                ?: return createUnavailableResult(leftText, rightText)
            keepOriginalWhenEquivalent(leftText, formattedLeftText)
        } else {
            leftText
        }
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
        val alignedNode = standardObjectMapper.nodeFactory.objectNode()
        if (options.sortMode == JsonDiffSortMode.LEFT_ORDER) {
            // 공통 키는 왼쪽 순서로, 오른쪽에만 있는 키는 원래 상대 순서로 뒤에 둔다.
            val referenceIterator = referenceNode.fieldNames()
            while (referenceIterator.hasNext()) {
                val fieldName = referenceIterator.next()
                val targetValue = targetNode.get(fieldName)
                if (targetValue != null) {
                    alignedNode.set<JsonNode>(
                        fieldName,
                        alignNode(referenceNode.get(fieldName), targetValue, options, coroutineContext)
                    )
                }
            }
            val targetIterator = targetNode.properties().iterator()
            while (targetIterator.hasNext()) {
                val entry = targetIterator.next()
                if (!referenceNode.has(entry.key)) {
                    alignedNode.set<JsonNode>(
                        entry.key,
                        alignNode(null, entry.value, options, coroutineContext)
                    )
                }
            }
        } else {
            val targetIterator = targetNode.properties().iterator()
            while (targetIterator.hasNext()) {
                val entry = targetIterator.next()
                alignedNode.set<JsonNode>(
                    entry.key,
                    alignNode(referenceNode.get(entry.key), entry.value, options, coroutineContext)
                )
            }
        }
        return alignedNode
    }

    private fun alignArray(
        referenceNode: ArrayNode,
        targetNode: ArrayNode,
        options: JsonDiffAlignmentOptions,
        coroutineContext: CoroutineContext
    ): ArrayNode {
        val alignedNode = standardObjectMapper.nodeFactory.arrayNode(targetNode.size())
        if (!options.shouldIgnoreArrayOrder) {
            for (index in 0 until targetNode.size()) {
                alignedNode.add(alignNode(referenceNode.get(index), targetNode.get(index), options, coroutineContext))
            }
            return alignedNode
        }

        // JsonNode 동등성은 객체 필드 순서를 무시하고 중첩 배열 순서와 값 타입은 유지한다.
        val unmatchedIndexesByElement = HashMap<JsonNode, ArrayDeque<Int>>(targetNode.size() * 2 + 1)
        for (index in 0 until targetNode.size()) {
            coroutineContext.ensureActive()
            val element = targetNode.get(index)
            unmatchedIndexesByElement.getOrPut(element) { ArrayDeque() }.addLast(index)
        }

        val isMatchedByIndex = BooleanArray(targetNode.size())
        for (index in 0 until referenceNode.size()) {
            coroutineContext.ensureActive()
            val referenceElement = referenceNode.get(index)
            val matchedIndex = unmatchedIndexesByElement[referenceElement]?.removeFirstOrNull() ?: continue
            isMatchedByIndex[matchedIndex] = true
            alignedNode.add(alignNode(referenceElement, targetNode.get(matchedIndex), options, coroutineContext))
        }

        // 일치하지 않은 요소는 식별자를 추정하지 않고 원래 상대 순서로 뒤에 붙인다.
        for (index in 0 until targetNode.size()) {
            if (!isMatchedByIndex[index]) alignedNode.add(targetNode.get(index))
        }
        return alignedNode
    }

    /**
     * JsonFormatterService.formatJson과 같은 unescape/placeholder/검증 경로로 입력을 단일 패스로 해석합니다.
     * 양쪽의 같은 placeholder가 같은 값으로 비교되도록 sentinel을 공통 token으로 바꿉니다.
     */
    private fun parseInput(text: String, placeholderTokens: PlaceholderTokenTable): JsonNode? {
        if (text.isBlank()) return null

        parseSingleInput(text, placeholderTokens)?.let { return it }

        if (!formatterService.containsEscapeCharacters(text)) {
            return null
        }

        val unescapedText = formatterService.fullyUnescapeJson(text)
        if (unescapedText == text) {
            return null
        }
        return parseSingleInput(unescapedText, placeholderTokens)
    }

    private fun parseSingleInput(text: String, placeholderTokens: PlaceholderTokenTable): JsonNode? {
        val replacementResult = TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders(text)
        if (!replacementResult.isSuccessful) return null

        val parsedNode = parseSingleValidTreeFast(replacementResult.replacedText) ?: return null
        if (replacementResult.mappings.isEmpty()) return parsedNode

        val sharedTokensBySentinel = HashMap<String, String>(replacementResult.mappings.size * 2)
        for (mapping in replacementResult.mappings) {
            sharedTokensBySentinel[mapping.sentinelToken] = placeholderTokens.getToken(mapping.originalPlaceholder)
        }
        return replaceSentinelTokens(parsedNode, sharedTokensBySentinel)
    }

    private fun parseSingleValidTreeFast(jsonText: String): JsonNode? {
        if (jsonText.isBlank()) return null

        return try {
            parseSingleTree(standardObjectMapper, jsonText)
        } catch (_: JsonProcessingException) {
            try {
                parseSingleTree(objectMapper, jsonText)
            } catch (_: JsonProcessingException) {
                null
            }
        }
    }

    private fun parseSingleTree(mapper: ObjectMapper, jsonText: String): JsonNode? {
        return mapper.factory.createParser(jsonText).use { parser ->
            if (parser.nextToken() == null) return null
            val parsedNode = mapper.readTree<JsonNode>(parser) ?: return null
            if (parser.nextToken() != null) return null
            parsedNode
        }
    }

    private fun replaceSentinelTokens(node: JsonNode, sharedTokensBySentinel: Map<String, String>): JsonNode {
        return when {
            node.isTextual -> sharedTokensBySentinel[node.textValue()]
                ?.let { standardObjectMapper.nodeFactory.textNode(it) }
                ?: node

            node is ObjectNode -> {
                val iterator = node.properties().iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val replacement = replaceSentinelTokens(entry.value, sharedTokensBySentinel)
                    if (replacement !== entry.value) {
                        entry.setValue(replacement)
                    }
                }
                node
            }

            node is ArrayNode -> {
                for (index in 0 until node.size()) {
                    val existing = node.get(index)
                    val replacement = replaceSentinelTokens(existing, sharedTokensBySentinel)
                    if (replacement !== existing) {
                        node.set(index, replacement)
                    }
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
        val formattedText = formatterService.formatJsonNode(node, JsonFormatState.PRETTIFY, usesKeySorting)
        if (placeholderTokens.isEmpty()) return formattedText

        val restoredText = TemplatePlaceholderSupport.restorePlaceholders(formattedText, placeholderTokens.mappings)
        return restoredText.takeUnless { it.contains(placeholderTokens.tokenPrefix) }
    }

    private fun tryFormatSortedSinglePass(text: String): String? {
        if (text.isBlank()) return null

        formatterService.tryFormatValidJson(text, JsonFormatState.PRETTIFY, true)?.let { return it }

        if (!formatterService.containsEscapeCharacters(text)) {
            return null
        }

        val unescapedText = formatterService.fullyUnescapeJson(text)
        if (unescapedText == text) {
            return null
        }
        return formatterService.tryFormatValidJson(unescapedText, JsonFormatState.PRETTIFY, true)
    }

    /**
     * 앞뒤 공백만 다른 결과는 변경으로 보지 않고 원본을 유지합니다.
     */
    private fun keepOriginalWhenEquivalent(originalText: String, formattedText: String): String {
        return if (equalsTrimmed(originalText, formattedText)) originalText else formattedText
    }

    private fun equalsTrimmed(originalText: String, targetText: String): Boolean {
        var start = 0
        var end = originalText.length
        while (start < end && originalText[start] <= ' ') start++
        while (end > start && originalText[end - 1] <= ' ') end--
        val trimmedLength = end - start
        return trimmedLength == targetText.length &&
            originalText.regionMatches(start, targetText, 0, trimmedLength)
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
