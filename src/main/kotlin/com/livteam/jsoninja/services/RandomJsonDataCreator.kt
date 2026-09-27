package com.livteam.jsoninja.services

import com.fasterxml.jackson.databind.SerializationFeature
import com.intellij.openapi.components.service
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.random.RandomJsonStructureGenerator
import com.livteam.jsoninja.services.random.RandomJsonValueGenerator
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonRootType
import net.datafaker.Faker
import java.util.Locale
import kotlin.random.Random
import kotlin.random.asJavaRandom

class RandomJsonDataCreator(
    private val defaultMaxObjectProperties: Int = 5,
    private val defaultMaxArrayElements: Int = 5,
    private val nullProbability: Double = 0.0,
    private val faker: Faker? = null,
) {
    private val mapper = service<JsonObjectMapperService>().objectMapper

    fun generateConfiguredJsonString(
        config: JsonGenerationConfig,
        prettyPrint: Boolean = true,
        checkCancellation: () -> Unit = {},
    ): String {
        val propertyCount = when (config.jsonRootType) {
            JsonRootType.OBJECT -> config.objectPropertyCount
            JsonRootType.ARRAY_OF_OBJECTS -> config.propertiesPerObjectInArray
        }
        require(propertyCount in 1..100 && (config.jsonRootType == JsonRootType.OBJECT || config.arrayElementCount in 1..100)) {
            LocalizationBundle.message("dialog.generate.json.validation.integer.range", 1, 100)
        }
        require(config.maxDepth in 1..10) {
            LocalizationBundle.message("dialog.generate.json.validation.integer.range", 1, 10)
        }
        require(defaultMaxObjectProperties in 1..100 && defaultMaxArrayElements in 1..100)
        require(nullProbability in 0.0..1.0)
        checkCancellation()

        // Structure and values consume separate random streams, so new values keep the same keys and types.
        val structureRandom = Random(config.randomStructureSeed ?: Random.nextLong())
        val shape = RandomJsonStructureGenerator(
            structureRandom, config.maxDepth, defaultMaxObjectProperties, checkCancellation,
        ).generateObject(propertyCount)
        val valueRandom = Random(config.randomValueSeed ?: Random.nextLong())
        val valueFaker = faker ?: Faker(Locale.ENGLISH, valueRandom.asJavaRandom())
        val valueGenerator = RandomJsonValueGenerator(
            valueRandom, valueFaker, defaultMaxArrayElements, nullProbability, checkCancellation,
        )
        val data = when (config.jsonRootType) {
            JsonRootType.OBJECT -> valueGenerator.generateObject(shape)
            JsonRootType.ARRAY_OF_OBJECTS -> valueGenerator.generateArray(shape, config.arrayElementCount)
        }
        checkCancellation()
        val writer = if (prettyPrint) mapper.writerWithDefaultPrettyPrinter() else mapper.writer()
        val json = writer.without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValueAsString(data)
        return if (config.isJson5 && prettyPrint) {
            addJson5Features(json, valueRandom, checkCancellation)
        } else {
            json
        }
    }

    fun applyJson5Features(jsonString: String): String {
        val random = Random(Random.nextLong())
        return addJson5Features(jsonString, random) {}
    }

    /**
     * JSON5 특징을 추가: 주석과 trailing comma
     */
    private fun addJson5Features(
        jsonString: String,
        random: Random,
        checkCancellation: () -> Unit,
    ): String {
        val lines = jsonString.lines().toMutableList()
        val result = mutableListOf<String>()

        // 상단에 JSON5 주석 추가
        result.add("// Generated JSON5 Data")
        result.add("// Supports comments, trailing commas, and unquoted keys")

        for (i in lines.indices) {
            checkCancellation()
            val line = lines[i]
            val trimmed = line.trim()

            // 객체나 배열의 마지막 요소에 trailing comma 추가
            if (i < lines.size - 1) {
                val nextTrimmed = lines[i + 1].trim()
                // 값이 무엇이든(문자열, 숫자, 불리언, null, 객체/배열 등) 닫는 괄호 앞이면 쉼표 추가
                // 단, 여는 괄호 바로 뒤에 닫는 괄호가 오는 빈 객체/배열인 경우는 제외
                if ((nextTrimmed.startsWith("}") || nextTrimmed.startsWith("]"))
                    && !trimmed.endsWith(",")
                    && !trimmed.endsWith("{")
                    && !trimmed.endsWith("[")
                ) {
                    result.add(line + ",")
                    continue
                }
            }

            // 일부 속성에 인라인 주석 추가 (랜덤하게)
            if (trimmed.contains(":") && random.nextDouble() < 0.15) {
                result.add("$line  // ${generateRandomComment(random)}")
            } else {
                result.add(line)
            }
        }

        return result.joinToString("\n")
    }

    /**
     * JSON5 주석은 고정 후보에서 선택하며 Datafaker는 데이터 값에만 사용한다.
     */
    private fun generateRandomComment(random: Random): String = when (random.nextInt(4)) {
        0 -> "Generated sample value"
        1 -> "Random example data"
        2 -> "Value follows the selected field type"
        else -> "Sample data for development"
    }
}
