package com.livteam.jsonhelper2.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.PathNotFoundException
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider

/**
 * JmesPathService는 JsonPath를 사용하여 JSON 데이터를 쿼리하는 기능을 제공합니다.
 * JsonPath는 JSON 데이터를 쿼리하기 위한 표현식 언어입니다.
 */
@Service(Service.Level.PROJECT)
class JmesPathService {
    private val LOG = logger<JmesPathService>()
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
            
            // 결과가 null인 경우 빈 문자열 반환
            if (result == null) {
                LOG.warn("JsonPath 쿼리 결과가 null입니다: $jsonPathExpression")
                return ""
            }
            
            // 결과를 JSON 문자열로 변환
            objectMapper.writeValueAsString(result)
        } catch (e: Exception) {
            LOG.warn("JsonPath 쿼리 실행 중 오류 발생: $jsonPathExpression", e)
            // 예외 발생 시 빈 문자열 반환
            ""
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

    /**
     * 주어진 JSON 문자열에서 JsonPath 표현식을 사용하여 값을 추출합니다.
     * 타입 파라미터를 사용하여 결과를 특정 타입으로 변환합니다.
     *
     * @param jsonString JSON 문자열
     * @param jsonPathExpression JsonPath 표현식
     * @param clazz 반환 타입 클래스
     * @return 지정된 타입으로 변환된 결과, 오류 발생 시 null 반환
     */
    fun <T> queryAs(jsonString: String, jsonPathExpression: String, clazz: Class<T>): T? {
        return try {
            val context = JsonPath.using(configuration).parse(jsonString)
            context.read(jsonPathExpression, clazz)
        } catch (e: PathNotFoundException) {
            LOG.debug("JsonPath 경로를 찾을 수 없음: $jsonPathExpression", e)
            null
        } catch (e: Exception) {
            LOG.warn("JsonPath 쿼리 실행 중 오류 발생: $jsonPathExpression", e)
            null
        }
    }

    /**
     * 주어진 JSON 문자열에서 JsonPath 표현식과 일치하는 모든 값을 목록으로 반환합니다.
     *
     * @param jsonString JSON 문자열
     * @param jsonPathExpression JsonPath 표현식
     * @return 일치하는 값들의 목록, 오류 발생 시 빈 목록 반환
     */
    fun queryList(jsonString: String, jsonPathExpression: String): List<Any> {
        return try {
            val context = JsonPath.using(configuration).parse(jsonString)
            context.read(jsonPathExpression)
        } catch (e: Exception) {
            LOG.warn("JsonPath 쿼리 실행 중 오류 발생: $jsonPathExpression", e)
            emptyList()
        }
    }
}