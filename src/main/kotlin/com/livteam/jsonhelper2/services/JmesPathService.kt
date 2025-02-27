package com.livteam.jsonhelper2.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider

/**
 * JMESPath 서비스는 JsonPath를 사용하여 JSON 데이터를 쿼리하는 기능을 제공합니다.
 * JsonPath는 JSON 데이터를 쿼리하기 위한 표현식 언어입니다.
 */
@Service(Service.Level.PROJECT)
class JMESPathService {
    private val LOG = logger<JMESPathService>()
    private val objectMapper = ObjectMapper()
    
    // JsonPath 설정 - Jackson 제공자 사용 및 누락된 속성 처리 옵션 설정
    private val configuration = Configuration.builder()
        .jsonProvider(JacksonJsonProvider(objectMapper))
        .mappingProvider(JacksonMappingProvider(objectMapper))
        .options(Option.SUPPRESS_EXCEPTIONS)
        .build()

    /**
     * 주어진 JSON 문자열에 JsonPath 쿼리를 적용합니다.
     *
     * @param jsonString JSON 문자열
     * @param jsonPathExpression JsonPath 표현식
     * @return 쿼리 결과를 JSON 문자열로 반환, 오류 발생 시 null 반환
     */
    fun query(jsonString: String, jsonPathExpression: String): String? {
        return try {
            // JsonPath 컨텍스트 생성
            val context = JsonPath.using(configuration).parse(jsonString)
            
            // 쿼리 실행
            val result = context.read<Any>(jsonPathExpression)
            
            // 결과가 null인 경우 null 반환
            if (result == null) {
                LOG.warn("JsonPath 쿼리 결과가 null입니다: $jsonPathExpression")
                return null
            }
            
            // 결과를 JSON 문자열로 변환
            objectMapper.writeValueAsString(result)
        } catch (e: Exception) {
            LOG.warn("JsonPath 쿼리 실행 중 오류 발생: $jsonPathExpression", e)
            // 예외 발생 시 null 반환
            null
        }
    }

    /**
     * JsonPath 표현식이 유효한지 검증합니다.
     *
     * @param jsonPathExpression 검증할 JsonPath 표현식
     * @return 유효성 여부
     */
    fun isValidExpression(jsonPathExpression: String): Boolean {
        return try {
            // 표현식 컴파일 시도
            JsonPath.compile(jsonPathExpression)
            true
        } catch (e: Exception) {
            LOG.debug("유효하지 않은 JsonPath 표현식: $jsonPathExpression", e)
            false
        }
    }
}