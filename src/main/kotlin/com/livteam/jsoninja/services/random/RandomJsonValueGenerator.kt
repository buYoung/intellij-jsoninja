package com.livteam.jsoninja.services.random

import com.livteam.jsoninja.LocalizationBundle
import net.datafaker.Faker
import java.util.IdentityHashMap
import kotlin.random.Random

internal class RandomJsonValueGenerator(
    private val random: Random,
    faker: Faker,
    private val maxArrayElements: Int,
    private val nullProbability: Double,
    private val checkCancellation: () -> Unit,
) {
    private var nodeCount = 0
    private val primitiveGenerator = RandomJsonPrimitiveGenerator(random, faker)

    fun generateObject(shape: RandomJsonShape.ObjectShape): Map<String, Any?> =
        generateObject(shape, IdentityHashMap())

    fun generateArray(shape: RandomJsonShape, length: Int): List<Any?> {
        checkCancellation()
        val uniqueIds = IdentityHashMap<RandomJsonShape.PrimitiveShape, MutableSet<Int>>()
        return List(length) { generateValue(shape, uniqueIds, canBeNull = false) }
    }

    private fun generateObject(
        shape: RandomJsonShape.ObjectShape,
        uniqueIds: IdentityHashMap<RandomJsonShape.PrimitiveShape, MutableSet<Int>>,
    ): Map<String, Any?> = shape.fields.mapValues { (_, fieldShape) -> generateValue(fieldShape, uniqueIds) }

    private fun generateValue(
        shape: RandomJsonShape,
        uniqueIds: IdentityHashMap<RandomJsonShape.PrimitiveShape, MutableSet<Int>>,
        canBeNull: Boolean = true,
    ): Any? {
        checkCancellation()
        nodeCount++
        check(nodeCount <= MAX_VALUE_NODES) {
            LocalizationBundle.message("dialog.generate.json.random.too.large")
        }
        if (canBeNull && nullProbability > 0 && random.nextDouble() < nullProbability) return null
        return when (shape) {
            is RandomJsonShape.ObjectShape -> generateObject(shape, uniqueIds)
            is RandomJsonShape.ArrayShape -> generateArray(shape.element, random.nextInt(1, maxArrayElements + 1))
            is RandomJsonShape.PrimitiveShape -> {
                if ((shape.rule as? RandomJsonValueRule.Generated)?.kind == RandomJsonValueKind.UNIQUE_ID) {
                    generateUniqueId(uniqueIds.getOrPut(shape) { mutableSetOf() })
                } else {
                    primitiveGenerator.generate(shape.rule)
                }
            }
        }
    }

    private fun generateUniqueId(usedIds: MutableSet<Int>): Int {
        var candidate = random.nextInt(1, 1_000_000_000)
        while (!usedIds.add(candidate)) {
            checkCancellation()
            candidate = if (candidate == 999_999_999) 1 else candidate + 1
        }
        return candidate
    }

    private companion object {
        const val MAX_VALUE_NODES = 100_000
    }
}
