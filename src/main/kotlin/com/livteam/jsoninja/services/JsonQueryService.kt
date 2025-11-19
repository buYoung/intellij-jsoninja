package com.livteam.jsoninja.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import io.burt.jmespath.jackson.JacksonRuntime

/**
 * 다양한 쿼리 언어(Jayway JsonPath, JMESPath)를 사용하여 JSON 데이터를 쿼리하는 서비스입니다.
 */
@Service(Service.Level.PROJECT)
class JsonQueryService(private val project: Project) {
    private val LOG = logger<JsonQueryService>()
    private val objectMapper = ObjectMapper()
    
    // Jayway JsonPath 설정 (기본 JsonSmartProvider 사용)
    private val jsonPathConfiguration = Configuration.builder()
        .options(Option.SUPPRESS_EXCEPTIONS)
        .build()

    // JMESPath 런타임
    private val jmesPathRuntime = JacksonRuntime()

    /**
     * 설정된 쿼리 언어를 사용하여 주어진 JSON 문자열에 쿼리를 적용합니다.
     *
     * @param jsonString JSON 문자열
     * @param expression 쿼리 표현식
     * @return 쿼리 결과를 JSON 문자열로 반환, 오류 발생 시 null 반환
     */
    fun query(jsonString: String, expression: String): String? {
        val settings = JsoninjaSettingsState.getInstance(project)
        val type = JsonQueryType.fromString(settings.jsonQueryType)
        
        return try {
            when (type) {
                JsonQueryType.JAYWAY_JSONPATH -> queryJsonPath(jsonString, expression)
                JsonQueryType.JMESPATH -> queryJmesPath(jsonString, expression)
            }
        } catch (e: Exception) {
            LOG.warn("쿼리 실행 중 오류 발생 ($type): $expression", e)
            null
        }
    }

    private fun queryJsonPath(jsonString: String, expression: String): String? {
        // 컨텍스트 파싱
        val context = JsonPath.using(jsonPathConfiguration).parse(jsonString)
        
        // 쿼리 실행
        val result = context.read<Any>(expression)
        
        if (result == null) {
            LOG.warn("JsonPath 쿼리 결과가 null입니다: $expression")
            return null
        }
        
        // 결과를 JSON 문자열로 변환
        return objectMapper.writeValueAsString(result)
    }

    private fun queryJmesPath(jsonString: String, expression: String): String? {
        // JSON 파싱
        val jsonNode = objectMapper.readTree(jsonString)
        
        // 표현식 컴파일
        val jmesPathExpression = jmesPathRuntime.compile(expression)
        
        // 검색 실행
        val result = jmesPathExpression.search(jsonNode)
        
        if (result == null || result.isNull) {
            LOG.warn("JMESPath 쿼리 결과가 null입니다: $expression")
            return null
        }
        
        // 결과를 JSON 문자열로 변환
        return objectMapper.writeValueAsString(result)
    }

    /**
     * 설정된 쿼리 언어에 대해 표현식이 유효한지 검증합니다.
     *
     * @param expression 검증할 표현식
     * @return 유효성 여부
     */
    fun isValidExpression(expression: String): Boolean {
        val settings = JsoninjaSettingsState.getInstance(project)
        val type = JsonQueryType.fromString(settings.jsonQueryType)
        
        return try {
            when (type) {
                JsonQueryType.JAYWAY_JSONPATH -> {
                    JsonPath.compile(expression)
                    true
                }
                JsonQueryType.JMESPATH -> {
                    jmesPathRuntime.compile(expression)
                    true
                }
            }
        } catch (e: Exception) {
            LOG.debug("유효하지 않은 표현식 ($type): $expression", e)
            false
        }
    }
}
