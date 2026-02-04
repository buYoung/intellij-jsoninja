package com.livteam.jsoninja.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import java.util.concurrent.ConcurrentHashMap

/**
 * JSON 포맷팅 서비스
 * JSON 문자열의 포맷팅(prettify, uglify)을 처리하는 서비스
 * 다양한 JSON 포맷팅 옵션을 설정할 수 있는 기능 제공
 */
@Service(Service.Level.PROJECT)
class JsonFormatterService(private val project: Project) {
    private val LOG = logger<JsonFormatterService>()
    private val settings: JsoninjaSettingsState = JsoninjaSettingsState.getInstance(project)
    
    private val defaultMapper = service<JsonObjectMapperService>().objectMapper
    
    private val sortedMapper = defaultMapper.copy().apply {
        configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
    }

    private val nonSortedMapper = defaultMapper.copy().apply {
        configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false)
    }

    companion object {
        private val prettyPrinterCache = ConcurrentHashMap<Pair<Int, Boolean>, DefaultPrettyPrinter>()
        
        // 기본 들여쓰기 공백 수
        private const val DEFAULT_INDENT_SIZE = 2
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
     * 현재 설정에 맞는 ObjectMapper 가져오기
     *
     * @param usesSorting 키 정렬 여부
     * @return 설정된 ObjectMapper
     */
    private fun getConfiguredMapper(usesSorting: Boolean): ObjectMapper {
        if (usesSorting) {
            return sortedMapper
        } else {
            return nonSortedMapper
        }
    }

    /**
     * 현재 설정에 맞는 PrettyPrinter 생성
     *
     * @param formatState 포맷 상태
     * @return 설정된 CustomPrettyPrinter
     */
    private fun createConfiguredPrettyPrinter(formatState: JsonFormatState): DefaultPrettyPrinter {
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
        var formatState = formatState
        val trimedJson = json.trim()
        val isEmptyJson = trimedJson.isBlank() || trimedJson.isEmpty()

        if (isEmptyJson) return json

        // Check if JSON is valid before attempting to format
        if (!isValidJson(json)) {
            LOG.debug("Invalid JSON detected, returning original: $json")
            return json
        }

        return try {
            // 포맷 상태에 따라 설정 조정
            // UGLIFY 상태일 때는 설정에 관계없이 항상 UGLIFY 유지
            val usesSorting = if (formatState == JsonFormatState.PRETTIFY_SORTED) {
                true
            } else if (formatState == JsonFormatState.UGLIFY) {
                false // UGLIFY 상태에서는 정렬하지 않음
            } else if (sortOverride != null) {
                sortOverride
            } else {
                settings.sortKeys // 다른 상태에서는 설정값 사용
            }

            // UGLIFY가 아니고 정렬이 필요한 경우에만 PRETTIFY_SORTED로 변경
            if (formatState != JsonFormatState.UGLIFY && usesSorting) {
                formatState = JsonFormatState.PRETTIFY_SORTED
            }

            // 매퍼 설정
            val mapper = getConfiguredMapper(usesSorting)
            val jsonNode = mapper.readTree(json)


            when (formatState) {
                JsonFormatState.PRETTIFY,
                JsonFormatState.PRETTIFY_COMPACT -> {
                    val prettyPrinter = createConfiguredPrettyPrinter(formatState)
                    mapper.writer(prettyPrinter).writeValueAsString(jsonNode)
                }

                JsonFormatState.PRETTIFY_SORTED -> {
                    val prettyPrinter = createConfiguredPrettyPrinter(formatState)
                    mapper.writer(prettyPrinter).writeValueAsString(mapper.treeToValue(jsonNode, Object::class.java))
                }

                JsonFormatState.UGLIFY -> {
                    mapper.writeValueAsString(jsonNode)
                }
            }
        } catch (e: Exception) {
            // 포맷팅 실패 시 원본 반환
            LOG.warn("JSON 포맷팅 실패: ${e.message}")
            json
        }
    }

    /**
     * JSON 문자열이 유효한지 검사
     *
     * @param json 검사할 JSON 문자열
     * @return 유효한 JSON이면 true, 아니면 false
     */
    fun isValidJson(json: String): Boolean {
        val trimedJson = json.trim()
        val isEmptyJson = trimedJson.isBlank() || trimedJson.isEmpty()
        if (isEmptyJson) return false

        return try {
            // Use JsonParser to validate the entire input string including trailing tokens
            defaultMapper.factory.createParser(json).use { parser ->
                // Parse the JSON value completely
                parser.nextToken()
                parser.skipChildren()
                // Check if there are any trailing tokens after the JSON
                parser.nextToken() == null
            }
        } catch (e: Exception) {
            LOG.debug("유효하지 않은 JSON: ${e.message}")
            false
        }
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
        private val useCompactArrays: Boolean = false
    ) : DefaultPrettyPrinter() {

        init {
            // 들여쓰기 설정
            val spaces = " ".repeat(indentSize)
            val indenter = DefaultIndenter(spaces, "\n")

            // 객체 들여쓰기 설정
            indentObjectsWith(indenter)

            // 배열 들여쓰기 설정 (압축 모드가 아닐 경우)
            if (!useCompactArrays) {
                indentArraysWith(indenter)
            }

            // 객체 필드 출력 설정 (콜론 뒤에만 공백)
            _objectFieldValueSeparatorWithSpaces = ": "
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
            return CustomPrettyPrinter(indentSize, useCompactArrays)
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
        val trimedJson = json.trim()
        val isEmptyJson = trimedJson.isBlank() || trimedJson.isEmpty()
        if (isEmptyJson) return json

        try {
            if (isBeautifiedJson(json)) {
                return escapeBeautifiedJson(json)
            }

            // Use Jackson to properly escape the JSON string
            val escaped = defaultMapper.writeValueAsString(json)
            // Remove the enclosing quotes that Jackson adds
            return escaped.substring(1, escaped.length - 1)
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
        val trimedJson = json.trim()
        val isEmptyJson = trimedJson.isBlank() || trimedJson.isEmpty()
        if (isEmptyJson) return json


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
            // Use Jackson to properly unescape the JSON string
            return defaultMapper.readValue(quoted, String::class.java)
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
        val trimedJson = jsonString.trim()
        val isEmptyJson = trimedJson.isBlank() || trimedJson.isEmpty()

        if (isEmptyJson) return false

        val hasMultipleLines = jsonString.contains("\n")
        if (!hasMultipleLines) return false

        val hasSpaceAfterColon = jsonString.contains(": ")
        if (!hasSpaceAfterColon) return false

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
     * 더 이상 이스케이프 문자가 없을 때까지 반복적으로 언이스케이프를 수행합니다.
     *
     * @param json 다중 이스케이프 처리된 JSON 문자열
     * @return 완전히 언이스케이프된 JSON 문자열
     */
    fun fullyUnescapeJson(json: String): String {
        var result = json
        var previousResult: String

        // 이스케이프 문자가 더 이상 없을 때까지 반복
        do {
            previousResult = result
            result = unescapeJson(result)

            // 변화가 없으면 더 이상 언이스케이프할 것이 없는 것
            if (result == previousResult) {
                break
            }
        } while (containsEscapeCharacters(result))

        return result
    }

    /**
     * 문자열에 이스케이프 문자가 포함되어 있는지 확인합니다.
     *
     * @param text 확인할 문자열
     * @return 이스케이프 문자가 포함되어 있으면 true, 아니면 false
     */
    fun containsEscapeCharacters(text: String): Boolean {
        val escapeSequences = arrayOf("\\\"", "\\\\", "\\b", "\\f", "\\n", "\\r", "\\t", "\\u")
        return escapeSequences.any { text.contains(it) }
    }
}
