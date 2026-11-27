package com.livteam.jsoninja.services

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import java.io.Writer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JSON 포맷팅 서비스
 * JSON 문자열의 포맷팅(prettify, uglify)을 처리하는 서비스
 * 다양한 JSON 포맷팅 옵션을 설정할 수 있는 기능 제공
 */
@Service(Service.Level.PROJECT)
class JsonFormatterService(private val project: Project) {
    private val LOG = logger<JsonFormatterService>()
    private val settings: JsoninjaSettingsState
        get() = JsoninjaSettingsState.getInstance(project)

    private val objectMapperService: JsonObjectMapperService by lazy {
        service<JsonObjectMapperService>()
    }

    private val standardJsonFactory: JsonFactory
        get() = objectMapperService.standardJsonFactory

    private val standardMapper: ObjectMapper
        get() = objectMapperService.standardObjectMapper

    private val defaultMapper: ObjectMapper
        get() = objectMapperService.objectMapper

    private val prettyPrinterCache = ConcurrentHashMap<Pair<Int, Boolean>, CustomPrettyPrinter>()

    companion object {
        // 기본 들여쓰기 공백 수
        private const val DEFAULT_INDENT_SIZE = 2
        private val OBJECT_FIELD_SEPARATORS: Separators =
            Separators.createDefaultInstance()
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
    }


    /**
     * 들여쓰기 공백 수 설정
     *
     * @param size 들여쓰기 공백 수 (기본값: 2)
     * @return this (메서드 체이닝 지원)
     */
    fun setIndentSize(size: Int): JsonFormatterService {
        require(size >= 0) { "들여쓰기 공백 수는 0 이상이어야 합니다." }
        settings.indentSize = size
        return this
    }

    /**
     * 키 정렬 옵션 설정
     *
     * @param sort true면 키를 알파벳 순서로 정렬 (기본값: false)
     * @return this (메서드 체이닝 지원)
     */
    fun setSortKeys(sort: Boolean): JsonFormatterService {
        settings.sortKeys = sort
        return this
    }

    /**
     * 모든 설정을 기본값으로 초기화
     *
     * @return this (메서드 체이닝 지원)
     */
    fun resetSettings(): JsonFormatterService {
        settings.indentSize = DEFAULT_INDENT_SIZE
        settings.sortKeys = false
        // Consider if JsonFormatState also needs reset here or if it's handled elsewhere.
        // For now, only reset what's directly managed by JsonFormatterService's original responsibilities.
        return this
    }

    /**
     * 현재 설정에 맞는 PrettyPrinter 템플릿 가져오기
     *
     * @param formatState 포맷 상태
     * @return 설정된 CustomPrettyPrinter
     */
    private fun createConfiguredPrettyPrinter(formatState: JsonFormatState): CustomPrettyPrinter {
        val currentIndentSize = settings.indentSize
        val useEffectiveCompactArrays = formatState.usesCompactArrays()
        val cacheKey = Pair(currentIndentSize, useEffectiveCompactArrays)

        // 캐시에서 PrettyPrinter를 찾거나, 없으면 새로 생성하여 캐시에 저장 후 반환
        return prettyPrinterCache.computeIfAbsent(cacheKey) {
            LOG.debug("Creating new CustomPrettyPrinter for indent: $currentIndentSize, compactArrays: $useEffectiveCompactArrays")
            CustomPrettyPrinter(
                indentSize = currentIndentSize,
                useCompactArrays = useEffectiveCompactArrays
            )
        }
    }

    /**
     * JSON 문자열을 지정된 포맷 상태에 따라 포맷팅
     *
     * @param json 포맷팅할 JSON 문자열
     * @param formatState 포맷 상태
     * @return 포맷팅된 JSON 문자열, 포맷팅 실패 시 원본 반환
     */
    fun formatJson(json: String, formatState: JsonFormatState, sortOverride: Boolean? = null): String {
        if (json.isBlank()) return json

        // Fast path: most inputs are already valid unescaped JSON; validate and format in a single pass.
        tryFormatJson(json, formatState, sortOverride)?.let { return it }

        if (!containsEscapeCharacters(json)) {
            return json
        }

        val unescapedJson = fullyUnescapeJson(json)
        if (unescapedJson == json) {
            return json
        }
        return tryFormatJson(unescapedJson, formatState, sortOverride) ?: json
    }

    /**
     * Validates and formats raw JSON/JSON5 in a single parse pass without auto-unescaping.
     * Returns [json] unchanged if invalid, empty, or tokenless (e.g., BOM-only or comment-only).
     */
    internal fun formatJsonIfValid(
        json: String,
        formatState: JsonFormatState,
        sortOverride: Boolean? = null
    ): String {
        if (json.isBlank()) return json
        return tryFormatJson(json, formatState, sortOverride) ?: json
    }

    /**
     * Validates and formats raw JSON/JSON5 in a single parse pass without auto-unescaping.
     * Returns null if [json] is blank, invalid, tokenless, or contains invalid placeholder syntax.
     */
    internal fun tryFormatValidJson(
        json: String,
        formatState: JsonFormatState,
        sortOverride: Boolean? = null
    ): String? {
        if (json.isBlank()) return null
        return tryFormatJson(json, formatState, sortOverride)
    }

    /**
     * Formats an already-parsed [JsonNode] directly without an intermediate serialize/re-parse round-trip.
     */
    internal fun formatJsonNode(
        node: JsonNode,
        formatState: JsonFormatState,
        sortOverride: Boolean? = null
    ): String {
        val usesSorting = if (formatState == JsonFormatState.UGLIFY) {
            false
        } else if (sortOverride != null) {
            sortOverride
        } else {
            formatState.usesSorting() || settings.sortKeys
        }
        val writer = FastStringWriter(if (formatState.usesPrettyPrinting()) 256 else 128)
        standardMapper.createGenerator(writer).use { generator ->
            if (formatState.usesPrettyPrinting()) {
                generator.prettyPrinter = createConfiguredPrettyPrinter(formatState).createInstance()
            }
            if (usesSorting) {
                writeSortedJsonNode(node, generator)
            } else {
                writeJsonNode(node, generator)
            }
        }
        return writer.toString()
    }

    private fun tryFormatJson(
        json: String,
        formatState: JsonFormatState,
        sortOverride: Boolean?
    ): String? {
        val replacementResult = TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders(json)
        if (!replacementResult.isSuccessful) {
            LOG.debug("Invalid placeholder syntax detected, returning original input")
            return null
        }

        return formatParsedJson(
            originalJson = json,
            replacementResult = replacementResult,
            formatState = formatState,
            sortOverride = sortOverride
        )
    }

    private fun formatParsedJson(
        originalJson: String,
        replacementResult: ReplacementResult,
        formatState: JsonFormatState,
        sortOverride: Boolean?
    ): String? {
        val jsonForParsing = replacementResult.replacedText

        return try {
            // 포맷 상태에 따라 설정 조정
            // UGLIFY 상태일 때는 설정에 관계없이 항상 UGLIFY 유지
            val usesSorting = if (formatState == JsonFormatState.UGLIFY) {
                false // UGLIFY 상태에서는 정렬하지 않음
            } else if (sortOverride != null) {
                sortOverride
            } else {
                formatState.usesSorting() || settings.sortKeys
            }

            val formattedJson = if (usesSorting) {
                formatSortedJson(jsonForParsing, formatState)
            } else {
                streamFormatJson(jsonForParsing, formatState)
            } ?: return null

            if (replacementResult.mappings.isEmpty()) {
                formattedJson
            } else {
                val restoredJson = TemplatePlaceholderSupport.restorePlaceholders(
                    formatted = formattedJson,
                    mappings = replacementResult.mappings
                )
                if (replacementResult.sentinelPrefix.isNotEmpty() &&
                    restoredJson.contains(replacementResult.sentinelPrefix)
                ) {
                    LOG.warn("Placeholder restoration failed after formatting, returning original JSON")
                    originalJson
                } else {
                    restoredJson
                }
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (processCanceledException: ProcessCanceledException) {
            throw processCanceledException
        } catch (e: JsonProcessingException) {
            LOG.debug("Invalid JSON detected, returning original input (${e.javaClass.simpleName})")
            null
        } catch (e: Exception) {
            // 포맷팅 실패 시 원본 반환
            LOG.warn("JSON 포맷팅 실패 (${e.javaClass.simpleName})")
            null
        }
    }

    private fun streamFormatJson(jsonForParsing: String, formatState: JsonFormatState): String? {
        return try {
            streamFormatWithFactory(standardJsonFactory, jsonForParsing, formatState)
        } catch (_: JsonProcessingException) {
            streamFormatWithFactory(defaultMapper.factory, jsonForParsing, formatState)
        }
    }

    private fun streamFormatWithFactory(
        factory: JsonFactory,
        jsonForParsing: String,
        formatState: JsonFormatState
    ): String? {
        val estimatedCapacity = estimateFormattedCapacity(jsonForParsing.length, formatState)
        val writer = FastStringWriter(estimatedCapacity)
        factory.createParser(jsonForParsing).use { parser ->
            val firstToken = parser.nextToken() ?: return null
            standardJsonFactory.createGenerator(writer).use { generator ->
                if (formatState.usesPrettyPrinting()) {
                    // DefaultPrettyPrinter carries nesting state; createInstance() ensures thread safety while reusing indenter.
                    generator.prettyPrinter = createConfiguredPrettyPrinter(formatState).createInstance()
                }
                if (!copyJsonValueTokens(parser, firstToken, generator)) {
                    return null
                }
            }
            if (parser.nextToken() != null) {
                return null
            }
        }
        return writer.toString()
    }

    private fun copyJsonValueTokens(
        parser: JsonParser,
        firstToken: JsonToken,
        generator: JsonGenerator
    ): Boolean {
        var depth = 0
        var currentToken: JsonToken? = firstToken
        while (currentToken != null) {
            when (currentToken) {
                JsonToken.START_OBJECT -> {
                    generator.writeStartObject()
                    depth++
                }
                JsonToken.END_OBJECT -> {
                    generator.writeEndObject()
                    depth--
                }
                JsonToken.START_ARRAY -> {
                    generator.writeStartArray()
                    depth++
                }
                JsonToken.END_ARRAY -> {
                    generator.writeEndArray()
                    depth--
                }
                JsonToken.FIELD_NAME -> generator.writeFieldName(parser.currentName())
                JsonToken.VALUE_STRING -> {
                    if (parser.hasTextCharacters()) {
                        generator.writeString(parser.textCharacters, parser.textOffset, parser.textLength)
                    } else {
                        generator.writeString(parser.text)
                    }
                }
                JsonToken.VALUE_NUMBER_INT,
                JsonToken.VALUE_NUMBER_FLOAT -> generator.writeNumber(parser.text)
                JsonToken.VALUE_TRUE -> generator.writeBoolean(true)
                JsonToken.VALUE_FALSE -> generator.writeBoolean(false)
                JsonToken.VALUE_NULL -> generator.writeNull()
                else -> throw JsonParseException(parser, "Unexpected JSON token: $currentToken")
            }
            if (depth == 0) {
                return true
            }
            currentToken = parser.nextToken()
        }
        return false
    }

    private fun formatSortedJson(jsonForParsing: String, formatState: JsonFormatState): String? {
        val jsonNode = parseSingleNodeFast(jsonForParsing) ?: return null
        val estimatedCapacity = estimateFormattedCapacity(jsonForParsing.length, formatState)
        val writer = FastStringWriter(estimatedCapacity)
        standardMapper.createGenerator(writer).use { generator ->
            if (formatState.usesPrettyPrinting()) {
                generator.prettyPrinter = createConfiguredPrettyPrinter(formatState).createInstance()
            }
            writeSortedJsonNode(jsonNode, generator)
        }
        return writer.toString()
    }

    private fun parseSingleNodeFast(jsonForParsing: String): JsonNode? {
        return try {
            parseSingleNode(standardMapper, jsonForParsing)
        } catch (_: JsonProcessingException) {
            parseSingleNode(defaultMapper, jsonForParsing)
        }
    }

    private fun parseSingleNode(mapper: ObjectMapper, jsonForParsing: String): JsonNode? {
        return mapper.factory.createParser(jsonForParsing).use { parser ->
            if (parser.nextToken() == null) return null
            val parsedNode = mapper.readTree<JsonNode>(parser) ?: return null
            if (parser.nextToken() != null) return null
            parsedNode
        }
    }

    private fun writeJsonNode(node: JsonNode, generator: JsonGenerator) {
        when {
            node.isObject -> {
                generator.writeStartObject()
                val fieldsIterator = node.properties().iterator()
                while (fieldsIterator.hasNext()) {
                    val entry = fieldsIterator.next()
                    generator.writeFieldName(entry.key)
                    writeJsonNode(entry.value, generator)
                }
                generator.writeEndObject()
            }
            node.isArray -> {
                generator.writeStartArray()
                for (index in 0 until node.size()) {
                    writeJsonNode(node.get(index), generator)
                }
                generator.writeEndArray()
            }
            node.isTextual -> generator.writeString(node.textValue())
            node.isBoolean -> generator.writeBoolean(node.booleanValue())
            node.isNull || node.isMissingNode -> generator.writeNull()
            node.isInt -> generator.writeNumber(node.intValue())
            node.isLong -> generator.writeNumber(node.longValue())
            node.isBigInteger -> generator.writeNumber(node.bigIntegerValue())
            node.isBigDecimal -> generator.writeNumber(node.decimalValue())
            node.isDouble -> generator.writeNumber(node.doubleValue())
            node.isFloat -> generator.writeNumber(node.floatValue())
            else -> generator.writeTree(node)
        }
    }

    private fun writeSortedJsonNode(node: JsonNode, generator: JsonGenerator) {
        when {
            node.isObject -> {
                generator.writeStartObject()
                val entryCount = node.size()
                if (entryCount == 1) {
                    val entry = node.properties().iterator().next()
                    generator.writeFieldName(entry.key)
                    writeSortedJsonNode(entry.value, generator)
                } else if (entryCount > 1) {
                    val entries = ArrayList<Map.Entry<String, JsonNode>>(entryCount)
                    val fieldsIterator = node.properties().iterator()
                    while (fieldsIterator.hasNext()) {
                        entries.add(fieldsIterator.next())
                    }
                    entries.sortBy { it.key }
                    for (entry in entries) {
                        generator.writeFieldName(entry.key)
                        writeSortedJsonNode(entry.value, generator)
                    }
                }
                generator.writeEndObject()
            }
            node.isArray -> {
                generator.writeStartArray()
                for (index in 0 until node.size()) {
                    writeSortedJsonNode(node.get(index), generator)
                }
                generator.writeEndArray()
            }
            node.isTextual -> generator.writeString(node.textValue())
            node.isBoolean -> generator.writeBoolean(node.booleanValue())
            node.isNull || node.isMissingNode -> generator.writeNull()
            node.isInt -> generator.writeNumber(node.intValue())
            node.isLong -> generator.writeNumber(node.longValue())
            node.isBigInteger -> generator.writeNumber(node.bigIntegerValue())
            node.isBigDecimal -> generator.writeNumber(node.decimalValue())
            node.isDouble -> generator.writeNumber(node.doubleValue())
            node.isFloat -> generator.writeNumber(node.floatValue())
            else -> generator.writeTree(node)
        }
    }

    private fun estimateFormattedCapacity(inputLength: Int, formatState: JsonFormatState): Int {
        if (!formatState.usesPrettyPrinting()) {
            return maxOf(16, inputLength)
        }
        val extra = inputLength / 2 + 64
        return if (inputLength > Int.MAX_VALUE - extra) Int.MAX_VALUE else maxOf(32, inputLength + extra)
    }

    suspend fun formatJsonOnDefault(
        json: String,
        formatState: JsonFormatState,
        sortOverride: Boolean? = null
    ): String = withContext(Dispatchers.Default) {
        formatJson(json, formatState, sortOverride)
    }

    /**
     * JSON 문자열이 유효한지 검사
     *
     * @param json 검사할 JSON 문자열
     * @return 유효한 JSON이면 true, 아니면 false
     */
    fun isValidJson(json: String): Boolean {
        if (json.isBlank()) return false

        val replacementResult = TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders(json)
        return if (!replacementResult.isSuccessful) {
            LOG.debug("유효하지 않은 placeholder syntax")
            false
        } else {
            isValidJsonText(replacementResult.replacedText)
        }
    }

    private fun isValidJsonText(jsonText: String): Boolean {
        return try {
            objectMapperService.isValidJsonTokenStream(jsonText)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (processCanceledException: ProcessCanceledException) {
            throw processCanceledException
        } catch (e: Exception) {
            LOG.debug("유효하지 않은 JSON (${e.javaClass.simpleName})")
            false
        }
    }

    /**
     * Unsynchronized StringBuilder-backed Writer to avoid StringWriter/StringBuffer monitor lock overhead
     * and leverage Compact Strings on JDK 17+.
     */
    private class FastStringWriter(initialCapacity: Int) : Writer() {
        private val builder = StringBuilder(initialCapacity)

        override fun write(c: Int) {
            builder.append(c.toChar())
        }

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            builder.append(cbuf, off, len)
        }

        override fun write(str: String) {
            builder.append(str)
        }

        override fun write(str: String, off: Int, len: Int) {
            builder.append(str, off, off + len)
        }

        override fun append(csq: CharSequence?): Writer {
            builder.append(csq)
            return this
        }

        override fun append(csq: CharSequence?, start: Int, end: Int): Writer {
            builder.append(csq, start, end)
            return this
        }

        override fun append(c: Char): Writer {
            builder.append(c)
            return this
        }

        override fun flush() = Unit

        override fun close() = Unit

        override fun toString(): String = builder.toString()
    }

    /**
     * 커스텀 PrettyPrinter 클래스
     * JSON 포맷팅 시 사용되는 들여쓰기 및 출력 형식 정의
     *
     * @param indentSize 들여쓰기 공백 수
     * @param useCompactArrays 배열을 압축 형식으로 출력할지 여부
     */
    private class CustomPrettyPrinter(
        private val indentSize: Int = 2,
        private val useCompactArrays: Boolean = false,
        private val sharedIndenter: DefaultIndenter = DefaultIndenter(" ".repeat(indentSize), "\n")
    ) : DefaultPrettyPrinter(OBJECT_FIELD_SEPARATORS) {

        init {
            // 객체 들여쓰기 설정
            indentObjectsWith(sharedIndenter)

            // 배열 들여쓰기 설정 (압축 모드가 아닐 경우)
            if (!useCompactArrays) {
                indentArraysWith(sharedIndenter)
            }
        }

        /**
         * 배열 값 사이에 구분자 추가
         */
        override fun writeArrayValueSeparator(g: JsonGenerator) {
            if (useCompactArrays) {
                g.writeRaw(',')
                g.writeRaw(' ')
            } else {
                super.writeArrayValueSeparator(g)
            }
        }

        /**
         * 배열 시작 처리
         */
        override fun writeStartArray(g: JsonGenerator) {
            super.writeStartArray(g)
            if (useCompactArrays) {
                g.writeRaw(' ')
            }
        }

        /**
         * 배열 종료 처리
         */
        override fun writeEndArray(g: JsonGenerator, nrOfValues: Int) {
            if (useCompactArrays && nrOfValues > 0) {
                g.writeRaw(' ')
            }
            super.writeEndArray(g, nrOfValues)
        }

        /**
         * 복제 메서드 (Jackson 내부에서 사용)
         */
        override fun createInstance(): DefaultPrettyPrinter {
            return CustomPrettyPrinter(indentSize, useCompactArrays, sharedIndenter)
        }
    }

    /**
     * JSON 문자열을 이스케이프 처리합니다.
     * 이스케이프 단계별로 백슬래시 수가 2배씩 증가하는 패턴으로 처리합니다.
     *
     * @param json 이스케이프 처리할 JSON 문자열
     * @return 이스케이프 처리된 JSON 문자열, 실패 시 원본 반환
     */
    fun escapeJson(json: String): String {
        if (json.isBlank()) return json

        try {
            if (isBeautifiedJson(json)) {
                return escapeBeautifiedJson(json)
            }

            // Use Jackson to properly escape the JSON string
            val escaped = standardMapper.writeValueAsString(json)
            // Remove the enclosing quotes that Jackson adds
            return escaped.substring(1, escaped.length - 1)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (processCanceledException: ProcessCanceledException) {
            throw processCanceledException
        } catch (e: Exception) {
            LOG.warn("JSON 이스케이프 처리 실패: ${e.message}")
            return json
        }
    }

    /**
     * 이스케이프 처리된 JSON 문자열을 한 단계씩 원래대로 되돌립니다.
     * 다중 이스케이프된 경우 한 단계만 언이스케이프합니다.
     *
     * @param json 언이스케이프 처리할 JSON 문자열
     * @return 한 단계 언이스케이프 처리된 JSON 문자열, 실패 시 원본 반환
     */
    fun unescapeJson(json: String): String {
        if (json.isBlank()) return json

        // 이스케이프 문자가 포함되어 있는지 확인
        if (!containsEscapeCharacters(json)) {
            return json // 이스케이프 문자가 없으면 원본 반환
        }

        try {
            if (isBeautifiedJson(json)) {
                return unescapeBeautifiedJson(json)
            }

            // Add quotes to make it a valid JSON string
            val quoted = "\"$json\""
            // Try standard Jackson first (zero JSON5 preprocessing), falling back to JSON5 on error.
            return try {
                standardMapper.readValue(quoted, String::class.java)
            } catch (_: JsonProcessingException) {
                defaultMapper.readValue(quoted, String::class.java)
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (processCanceledException: ProcessCanceledException) {
            throw processCanceledException
        } catch (e: Exception) {
            LOG.warn("JSON 언이스케이프 처리 실패: ${e.message}")
            return json
        }
    }

    /**
     * 포맷팅(들여쓰기, 줄바꿈)이 유지된 JSON 문자열을 이스케이프 처리합니다.
     * 이 최적화된 버전은 단일 패스 접근 방식을 사용합니다.
     * 백슬래시와 큰따옴표만 이스케이프 처리하며, 줄바꿈 문자 및 기타 문자들은 그대로 유지합니다.
     *
     * @param beautifiedJson 이스케이프 처리할, 포맷팅된 JSON 문자열 (JSON 리터럴이 아닌 원시 문자열 형태).
     * @return 다른 JSON 내부에 문자열 리터럴로 포함될 수 있도록 이스케이프 처리된 beautified JSON 문자열.
     */
    private fun escapeBeautifiedJson(beautifiedJson: String): String {
        // Process line by line to preserve formatting
        val result = StringBuilder(beautifiedJson.length + beautifiedJson.length / 10 + 16)
        for (char in beautifiedJson) {
            when (char) {
                '\\' -> result.append("\\\\") // 백슬래시를 먼저 이스케이프
                '"' -> result.append("\\\"")  // 큰따옴표를 이스케이프
                // \n, \r 등 다른 문자들은 포맷팅 유지를 위해 그대로 추가
                else -> result.append(char)
            }
        }
        return result.toString()
    }

    /**
     * 포맷팅(들여쓰기, 줄바꿈)이 유지된 이스케이프된 JSON 문자열을 언이스케이프 처리합니다.
     * 이 최적화된 버전은 단일 패스 접근 방식을 사용합니다.
     * 이스케이프된 백슬래시(\\)와 이스케이프된 큰따옴표(\")만 언이스케이프 처리하며,
     * 줄바꿈 문자 및 기타 문자들은 그대로 유지합니다.
     *
     * @param escapedBeautifiedJson 언이스케이프 처리할, 이스케이프된 beautified JSON 문자열.
     * @return 언이스케이프 처리된 beautified JSON 문자열.
     */
    private fun unescapeBeautifiedJson(escapedBeautifiedJson: String): String {
        val result = StringBuilder(escapedBeautifiedJson.length)
        var i = 0
        while (i < escapedBeautifiedJson.length) {
            val char = escapedBeautifiedJson[i]
            if (char == '\\') {
                // 다음 문자가 있는지 확인 (이스케이프 시퀀스의 일부)
                if (i + 1 < escapedBeautifiedJson.length) {
                    val nextChar = escapedBeautifiedJson[i + 1]
                    when (nextChar) {
                        '\"' -> { // \" -> "
                            result.append('\"')
                            i++ // 다음 문자까지 처리했으므로 인덱스 증가
                        }

                        '\\' -> { // \\ -> \
                            result.append('\\')
                            i++ // 다음 문자까지 처리했으므로 인덱스 증가
                        }
                        // 이 "beautified" unescape는 \n, \t 등을 실제 문자로 변환하지 않음.
                        // 오직 \" 와 \\ 만 처리하고, 그 외 \x는 \x 그대로 둠.
                        // (예: \n은 문자열 "\n"으로 유지, 실제 개행 문자로 바뀌지 않음)
                        else -> {
                            result.append('\\') // 백슬래시 자체를 추가 (예: \n의 경우 \를 추가)
                            // 다음 루프에서 nextChar (예: n)가 처리됨
                        }
                    }
                } else {
                    // 문자열 끝에 백슬래시만 있는 경우 (일반적으로 유효하지 않은 이스케이프)
                    result.append('\\')
                }
            } else {
                // 일반 문자는 그대로 추가
                result.append(char)
            }
            i++
        }
        return result.toString()
    }

    /**
     * Detects if a JSON string is beautified (has indentation and line breaks)
     *
     * @param jsonString The JSON string to check
     * @return true if the JSON is beautified, false otherwise
     */
    fun isBeautifiedJson(jsonString: String): Boolean {
        if (jsonString.isBlank()) return false

        if (jsonString.indexOf('\n') < 0) return false
        if (jsonString.indexOf(": ") < 0) return false

        var atLineStart = true
        for (char in jsonString) {
            if (atLineStart) {
                if (char == ' ' || char == '\t') {
                    return true
                }

                if (char != '\n' && char != '\r') {
                    atLineStart = false
                }
            }

            if (char == '\n') {
                atLineStart = true
            }
        }

        // 루프를 모두 통과했지만 들여쓰기를 찾지 못함
        return false
    }

    /**
     * 다중 이스케이프된 JSON 문자열을 완전히 언이스케이프 처리합니다.
     * 유효한 문서를 만나면 중단하여 문자열 값의 이스케이프를 보존합니다.
     *
     * @param json 다중 이스케이프 처리된 JSON 문자열
     * @return 완전히 언이스케이프된 JSON 문자열
     */
    fun fullyUnescapeJson(json: String): String {
        var result = json
        while (!isValidJson(result)) {
            val decoded = unescapeJson(result)
            if (decoded == result) return json
            result = decoded
        }
        return result
    }

    /**
     * 문자열에 이스케이프 문자가 포함되어 있는지 확인합니다.
     *
     * @param text 확인할 문자열
     * @return 이스케이프 문자가 포함되어 있으면 true, 아니면 false
     */
    fun containsEscapeCharacters(text: String): Boolean {
        var index = text.indexOf('\\')
        while (index >= 0 && index + 1 < text.length) {
            when (text[index + 1]) {
                '"', '\\', 'b', 'f', 'n', 'r', 't', 'u' -> return true
            }
            index = text.indexOf('\\', index + 1)
        }
        return false
    }
}
