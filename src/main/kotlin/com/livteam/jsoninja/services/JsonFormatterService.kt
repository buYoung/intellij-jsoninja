package com.livteam.jsoninja.services

import com.intellij.openapi.components.Service
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.intellij.openapi.diagnostic.logger
import com.livteam.jsoninja.model.JsonFormatState

/**
 * JSON 포맷팅 서비스
 * JSON 문자열의 포맷팅(prettify, uglify)을 처리하는 서비스
 * 다양한 JSON 포맷팅 옵션을 설정할 수 있는 기능 제공
 */
@Service(Service.Level.PROJECT)
class JsonFormatterService {
    private val LOG = logger<JsonFormatterService>()

    companion object {
        // 성능 향상을 위한 싱글톤 ObjectMapper 인스턴스
        private val DEFAULT_MAPPER = ObjectMapper().apply {
            // 직렬화 설정
            configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            // 역직렬화 설정
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        }
        
        // 기본 들여쓰기 공백 수
        private const val DEFAULT_INDENT_SIZE = 2
    }
    
    // 현재 설정된 들여쓰기 공백 수
    private var indentSize = DEFAULT_INDENT_SIZE
    
    // 정렬 옵션 (키 기준 알파벳 순서로 정렬)
    private var sortKeys = false
    
    /**
     * 들여쓰기 공백 수 설정
     * 
     * @param size 들여쓰기 공백 수 (기본값: 2)
     * @return this (메서드 체이닝 지원)
     */
    fun setIndentSize(size: Int): JsonFormatterService {
        require(size >= 0) { "들여쓰기 공백 수는 0 이상이어야 합니다." }
        this.indentSize = size
        return this
    }
    
    /**
     * 키 정렬 옵션 설정
     * 
     * @param sort true면 키를 알파벳 순서로 정렬 (기본값: false)
     * @return this (메서드 체이닝 지원)
     */
    fun setSortKeys(sort: Boolean): JsonFormatterService {
        this.sortKeys = sort
        return this
    }
    
    /**
     * 모든 설정을 기본값으로 초기화
     * 
     * @return this (메서드 체이닝 지원)
     */
    fun resetSettings(): JsonFormatterService {
        indentSize = DEFAULT_INDENT_SIZE
        sortKeys = false
        return this
    }
    
    /**
     * 현재 설정에 맞는 ObjectMapper 가져오기
     * 
     * @param usesSorting 키 정렬 여부
     * @return 설정된 ObjectMapper
     */
    private fun getConfiguredMapper(usesSorting: Boolean): ObjectMapper {
        return DEFAULT_MAPPER.copy().apply {
            if (usesSorting) {
                configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            }
        }
    }
    
    /**
     * 현재 설정에 맞는 PrettyPrinter 생성
     * 
     * @param formatState 포맷 상태
     * @return 설정된 CustomPrettyPrinter
     */
    private fun createConfiguredPrettyPrinter(formatState: JsonFormatState): DefaultPrettyPrinter {
        return CustomPrettyPrinter(
            indentSize = indentSize,
            useCompactArrays = formatState.usesCompactArrays()
        )
    }
    
    /**
     * JSON 문자열을 지정된 포맷 상태에 따라 포맷팅
     * 
     * @param json 포맷팅할 JSON 문자열
     * @param formatState 포맷 상태
     * @return 포맷팅된 JSON 문자열, 포맷팅 실패 시 원본 반환
     */
    fun formatJson(json: String, formatState: JsonFormatState): String {
        if (json.isBlank()) return json
        
        // 유효하지 않은 JSON이면 원본 반환
        if (!isValidJson(json)) {
            LOG.warn("유효하지 않은 JSON 포맷을 감지했습니다.")
            return json
        }
        
        return try {
            // 포맷 상태에 따라 설정 조정
            val usesSorting = sortKeys || formatState == JsonFormatState.PRETTIFY_SORTED
            
            // 매퍼 설정
            val mapper = getConfiguredMapper(usesSorting)
            val jsonNode = mapper.readTree(json)
            
            when (formatState) {
                JsonFormatState.PRETTIFY,
                JsonFormatState.PRETTIFY_SORTED,
                JsonFormatState.PRETTIFY_COMPACT -> {
                    val prettyPrinter = createConfiguredPrettyPrinter(formatState)
                    mapper.writer(prettyPrinter).writeValueAsString(jsonNode)
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
        if (json.isBlank()) return false
        
        return try {
            DEFAULT_MAPPER.readTree(json)
            true
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
        if (json.isBlank()) return json

        try {
            if (isBeautifiedJson(json)) {
                return escapeBeautifiedJson(json)
            }

            // Use Jackson to properly escape the JSON string
            val escaped = DEFAULT_MAPPER.writeValueAsString(json)
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
            // Use Jackson to properly unescape the JSON string
            return DEFAULT_MAPPER.readValue(quoted, String::class.java)
        } catch (e: Exception) {
            LOG.warn("JSON 언이스케이프 처리 실패: ${e.message}")
            return json
        }
    }

    /**
     * Escapes a beautified JSON string while preserving formatting
     *
     * @param beautifiedJson The beautified JSON to escape
     * @return Escaped beautified JSON
     */
    private fun escapeBeautifiedJson(beautifiedJson: String): String {
        // Process line by line to preserve formatting
        return beautifiedJson.lines().joinToString("\n") { line ->
            // First, escape all backslashes (\ to \\)
            var processedLine = line.replace("\\", "\\\\")

            // Then, escape all quotes (" to \")
            val parts = processedLine.split("\"")
            if (parts.size <= 1) {
                // No quotes on this line, return as is
                processedLine
            } else {
                val result = StringBuilder()
                for (i in parts.indices) {
                    if (i > 0) {
                        // Add escaped quote
                        result.append("\\\"")
                    }
                    // Add part (which already has escaped backslashes)
                    result.append(parts[i])
                }
                result.toString()
            }
        }
    }

    /**
     * Unescapes a beautified JSON string while preserving formatting
     *
     * @param escapedBeautifiedJson The escaped beautified JSON to unescape
     * @return Unescaped beautified JSON
     */
    private fun unescapeBeautifiedJson(escapedBeautifiedJson: String): String {
        // Process line by line to preserve formatting
        return escapedBeautifiedJson.lines().joinToString("\n") { line ->
            // First, unescape quotes
            var processedLine = line.replace("\\\"", "\"")

            // Then, unescape backslashes
            processedLine = processedLine.replace("\\\\", "\\")

            processedLine
        }
    }

    /**
     * Detects if a JSON string is beautified (has indentation and line breaks)
     *
     * @param jsonString The JSON string to check
     * @return true if the JSON is beautified, false otherwise
     */
    fun isBeautifiedJson(jsonString: String): Boolean {
        // Check multiple criteria to determine if JSON is beautified
        val hasMultipleLines = jsonString.contains("\n")
        val hasIndentation = jsonString.lines().any { it.startsWith(" ") || it.startsWith("\t") }
        val hasSpaceAfterColon = jsonString.contains(": ")

        // Consider it beautified if it has line breaks and either indentation or spaced colons
        return hasMultipleLines && (hasIndentation || hasSpaceAfterColon)
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