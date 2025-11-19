package com.livteam.jsoninja.services

import com.fasterxml.jackson.databind.SerializationFeature
import com.intellij.openapi.components.service
import com.livteam.jsoninja.ui.dialog.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.RootType
import net.datafaker.Faker
import java.util.Locale
import java.util.Locale.getDefault
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// 배열 요소 타입을 정의하는 Enum 추가
private enum class ArrayElementType {
    STRING, NUMBER_INT, NUMBER_DOUBLE, BOOLEAN, DATE, UUID, OBJECT, ARRAY // 필요에 따라 추가 가능
}

class RandomJsonDataCreator (
    private val defaultMaxObjectProperties: Int = 5,
    private val defaultMaxArrayElements: Int = 5,
    private val nullProbability: Double = 0.1,
    private val faker: Faker = Faker(Locale.ENGLISH)
) {
    private val mapper = service<JsonObjectMapperService>().objectMapper

    // 개발 친화적인 공통 키 목록 확장
    private val commonKeys = listOf(
        "id", "userId", "productId", "orderId", "sessionId", "requestId",
        "name", "firstName", "lastName", "username", "email", "role", "permission",
        "status", "statusCode", "errorCode", "message", "description", "details",
        "createdAt", "updatedAt", "timestamp", "startDate", "endDate",
        "count", "total", "page", "size", "limit", "offset",
        "data", "payload", "items", "results", "errors", "metadata",
        "config", "settings", "options", "attributes", "tags",
        "url", "link", "path", "version", "type", "value", "isEnabled", "isActive"
    )

    /**
     * 설정(config)에 따라 JSON 문자열을 생성하는 메인 메소드
     */
    fun generateConfiguredJsonString(config: JsonGenerationConfig, prettyPrint: Boolean = true): String {
        val maxDepth = config.maxDepth
        val generatedData = when (config.rootType) {
            RootType.OBJECT -> generateSpecificObject(0, config.objectPropertyCount, maxDepth)
            RootType.ARRAY_OF_OBJECTS -> generateSpecificArrayOfObjects(0, config.arrayElementCount, config.propertiesPerObjectInArray, maxDepth)
        }
        // ... (ObjectMapper 로직 동일)
        return if (prettyPrint) {
            mapper.writerWithDefaultPrettyPrinter()
                .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // Maintain original behavior locally
                .writeValueAsString(generatedData)
        } else {
            mapper.writer()
                .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writeValueAsString(generatedData)
        }
    }

    /**
     * 개발 친화적인 키 이름 생성 시도
     */
    private fun generateDeveloperFriendlyKey(): String {
        return when (Random.nextInt(3)) {
            // camelCase (예: firstName, orderId)
            0 -> run {
                faker.lorem().word()
                    .replaceFirstChar { it.lowercase(getDefault()) }
            } + run {
                faker.lorem().word()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
            }
            // snake_case (예: user_id, created_at)
            1 -> faker.lorem().word() + "_" + faker.lorem().word()
            // 그냥 단어 (기존 방식)
            else -> faker.lorem().word()
        }
    }

    /**
     * 지정된 개수의 속성을 가진 객체 생성 (개선된 키 생성 로직 사용)
     */
    private fun generateSpecificObject(currentDepth: Int, propertyCount: Int, maxDepth: Int): Map<String, Any?> {
        if (currentDepth >= maxDepth) return mapOf("depth_limit_reached" to currentDepth)

        val map = mutableMapOf<String, Any?>()
        // 키 생성: 공통 키 + 개발 친화적 키 + Faker 단어 혼합
        val potentialKeys = (commonKeys + List(propertyCount) { generateDeveloperFriendlyKey() } + List(propertyCount) { faker.lorem().word() })
            .distinct()
            .shuffled()
            .take(propertyCount) // 정확히 propertyCount 만큼 키 선택

        potentialKeys.forEach { key ->
            // 값은 기존의 랜덤 생성 로직 사용 (깊이 증가, maxDepth 전달)
            map[key] = generateRandomElement(currentDepth + 1, maxDepth)
        }
        return map
    }

    /**
     * 지정된 길이의 배열 생성 (내부 객체 생성 시 개선된 로직 사용)
     */
    private fun generateSpecificArrayOfObjects(currentDepth: Int, arrayLength: Int, propertiesPerObject: Int, maxDepth: Int): List<Map<String, Any?>> {
        if (currentDepth >= maxDepth) return listOf(mapOf("depth_limit_reached" to currentDepth))

        val list = mutableListOf<Map<String, Any?>>()
        repeat(arrayLength) {
            // 각 요소는 '지정된 속성 개수를 가진 객체' (깊이 증가, maxDepth 전달)
            list.add(generateSpecificObject(currentDepth + 1, propertiesPerObject, maxDepth))
        }
        return list
    }


    /**
     * 중첩된 요소 무작위 생성 (maxDepth 파라미터 사용)
     */
    private fun generateRandomElement(currentDepth: Int, maxDepth: Int): Any? {
        if (Random.nextDouble() < nullProbability) return null
        if (currentDepth >= maxDepth) return generateDeveloperFriendlyPrimitive()

        val structureProbability = 0.4 * (1.0 - currentDepth.toDouble() / maxDepth)
        return when {
            Random.nextDouble() < structureProbability -> {
                if (Random.nextBoolean()) {
                    generateRandomNestedObject(currentDepth, maxDepth)
                } else {
                    // 타입 일관성을 적용한 배열 생성 메소드 호출
                    generateConsistentTypedArray(currentDepth, maxDepth)
                }
            }
            else -> generateDeveloperFriendlyPrimitive()
        }
    }

    /**
     * 중첩된 객체 무작위 생성 (개선된 키 사용)
     */
    private fun generateRandomNestedObject(currentDepth: Int, maxDepth: Int): Map<String, Any?> {
        if (currentDepth >= maxDepth) return mapOf("depth_limit_reached_nested" to currentDepth)

        val map = mutableMapOf<String, Any?>()
        val numProperties = Random.nextInt(1, defaultMaxObjectProperties + 1)
        // 키 생성: 공통 키 + 개발 친화적 키 + Faker 단어 혼합
        val potentialKeys = (commonKeys + List(numProperties) { generateDeveloperFriendlyKey() } + List(numProperties) { faker.lorem().word() })
            .distinct()
            .shuffled()
            .take(numProperties)

        potentialKeys.forEach { key ->
            map[key] = generateRandomElement(currentDepth + 1, maxDepth)
        }
        return map
    }

    /**
     * 중첩된 배열 무작위 생성 (변경 없음)
     */
    private fun generateRandomNestedArray(currentDepth: Int, maxDepth: Int): List<Any?> {
        if (currentDepth >= maxDepth) return listOf("depth_limit_reached_nested_array_$currentDepth")

        val list = mutableListOf<Any?>()
        val numElements = Random.nextInt(0, defaultMaxArrayElements + 1)
        repeat(numElements) {
            list.add(generateRandomElement(currentDepth + 1, maxDepth))
        }
        return list
    }

    /**
     * 타입이 일관된 중첩 배열을 생성
     */
    private fun generateConsistentTypedArray(currentDepth: Int, maxDepth: Int): List<Any?> {
        // 깊이 제한 검사
        if (currentDepth >= maxDepth) return listOf("depth_limit_reached_nested_array_$currentDepth")

        val list = mutableListOf<Any?>()
        val numElements = Random.nextInt(1, defaultMaxArrayElements + 1) // 빈 배열은 제외 (최소 1개)

        // 이 배열 인스턴스가 담을 요소 타입을 무작위로 *한 번만* 결정
        // 단, 너무 깊으면 객체/배열 타입 제외
        val possibleTypes = if (currentDepth + 1 >= maxDepth) {
            ArrayElementType.values().filter { it != ArrayElementType.OBJECT && it != ArrayElementType.ARRAY }
        } else {
            ArrayElementType.values().toList()
        }
        val chosenElementType = possibleTypes.random()


        // 결정된 타입의 요소를 numElements 개수만큼 생성하여 리스트에 추가
        repeat(numElements) {
            // null 확률도 각 요소에 적용 가능
            if (Random.nextDouble() < nullProbability / 2) { // 배열 내 null 확률은 조금 낮게 설정
                list.add(null)
            } else {
                list.add(generateElementOfType(chosenElementType, currentDepth + 1, maxDepth))
            }
        }
        return list
    }

    /**
     * 지정된 타입(elementType)의 요소를 생성하는 헬퍼 함수
     */
    private fun generateElementOfType(elementType: ArrayElementType, currentDepth: Int, maxDepth: Int): Any? {
        // 여기서 currentDepth + 1 이 maxDepth를 넘는지 다시 체크할 필요는 없음.
        // generateConsistentTypedArray 에서 이미 걸렀기 때문.
        return when (elementType) {
            ArrayElementType.STRING -> generateRandomDatafakerString() // 문자열 생성
            ArrayElementType.NUMBER_INT -> faker.number().numberBetween(1, 100000) // 정수 생성
            ArrayElementType.NUMBER_DOUBLE -> faker.number().randomDouble(2, 0, 5000) // 실수 생성
            ArrayElementType.BOOLEAN -> faker.bool().bool() // 불리언 생성
            ArrayElementType.DATE -> faker.timeAndDate().past(365 * 2, TimeUnit.DAYS, "YYYY-MM-dd mm:hh:ss.SSS") // ISO 날짜 생성
            ArrayElementType.UUID -> faker.internet().uuid() // UUID 생성
            ArrayElementType.OBJECT -> generateRandomNestedObject(currentDepth, maxDepth) // 중첩 객체 생성 (재귀)
            ArrayElementType.ARRAY -> generateConsistentTypedArray(currentDepth, maxDepth) // 중첩 배열 생성 (재귀, 타입 일관성 유지)
            // 추가적인 타입이 있다면 여기에 case 추가
        }
    }

    /**
     * 개발 친화적인 기본 타입(Primitive) 값 생성
     */
    private fun generateDeveloperFriendlyPrimitive(): Any {
        // generateElementOfType과 유사하게, 어떤 종류의 기본값을 생성할지 결정
        val type = ArrayElementType.values()
            .filter { it != ArrayElementType.OBJECT && it != ArrayElementType.ARRAY } // 객체/배열 제외
            .random() // 무작위 기본 타입 선택

        // 선택된 기본 타입에 맞는 값 생성 (null 반환 없음)
        return when (type) {
            ArrayElementType.STRING -> generateRandomDatafakerString()
            ArrayElementType.NUMBER_INT -> faker.number().numberBetween(1, 100000)
            ArrayElementType.NUMBER_DOUBLE -> faker.number().randomDouble(2, 0, 5000)
            ArrayElementType.BOOLEAN -> faker.bool().bool()
            ArrayElementType.DATE -> faker.timeAndDate().past(365 * 2, TimeUnit.DAYS, "YYYY-MM-dd mm:hh:ss.SSS")
            ArrayElementType.UUID -> faker.internet().uuid()
            // OBJECT, ARRAY는 여기서 생성되지 않음
            else -> faker.lorem().word() // 혹시 모를 예외 케이스 처리
        }
    }

    /**
     * Datafaker를 사용하여 다양한 종류의 "문자열" 생성 (generateDeveloperFriendlyPrimitive 내부에서 호출됨)
     */
    private fun generateRandomDatafakerString(): String {
        return when (Random.nextInt(10)) { // 문자열 내에서도 종류 분기
            0 -> faker.name().fullName()
            1 -> faker.address().fullAddress()
            2 -> faker.company().name()
            3 -> faker.job().title()
            4 -> faker.commerce().productName()
            5 -> faker.color().name()
            6 -> faker.team().name()
            7 -> faker.app().name()
            8 -> faker.lorem().word() // 가장 기본적인 단어
            else -> faker.file().fileName()
        }
    }
}