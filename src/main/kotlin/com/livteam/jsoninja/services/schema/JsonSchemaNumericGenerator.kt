package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.BigIntegerNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.intellij.openapi.progress.ProgressManager
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.random.Random

internal object JsonSchemaNumericGenerator {
    fun fromSchema(schemaNode: JsonNode, isInteger: Boolean, jsonPointer: String): JsonNode {
        val value = generate(
            minimumValue = schemaNode.path("minimum").takeIf { it.isNumber }?.decimalValue(),
            maximumValue = schemaNode.path("maximum").takeIf { it.isNumber }?.decimalValue(),
            exclusiveMinimumValue = schemaNode.path("exclusiveMinimum").takeIf { it.isNumber }?.decimalValue(),
            exclusiveMaximumValue = schemaNode.path("exclusiveMaximum").takeIf { it.isNumber }?.decimalValue(),
            multipleOfValue = schemaNode.path("multipleOf").takeIf { it.isNumber }?.decimalValue(),
            isInteger = isInteger,
            jsonPointer = jsonPointer
        )
        return if (isInteger) BigIntegerNode(value.toBigIntegerExact()) else DecimalNode(value)
    }

    fun generate(
        minimumValue: BigDecimal?,
        maximumValue: BigDecimal?,
        exclusiveMinimumValue: BigDecimal?,
        exclusiveMaximumValue: BigDecimal?,
        multipleOfValue: BigDecimal?,
        isInteger: Boolean,
        jsonPointer: String
    ): BigDecimal {
        ProgressManager.checkCanceled()
        val lowerBound = listOfNotNull(minimumValue, exclusiveMinimumValue).maxOrNull()
        val upperBound = listOfNotNull(maximumValue, exclusiveMaximumValue).minOrNull()
        val isLowerExclusive = lowerBound != null && exclusiveMinimumValue?.compareTo(lowerBound) == 0
        val isUpperExclusive = upperBound != null && exclusiveMaximumValue?.compareTo(upperBound) == 0
        if (lowerBound != null && upperBound != null &&
            (lowerBound > upperBound || lowerBound.compareTo(upperBound) == 0 && (isLowerExclusive || isUpperExclusive))
        ) {
            throw JsonSchemaGenerationException("Numeric bounds are contradictory.", jsonPointer)
        }
        if (multipleOfValue != null && multipleOfValue <= BigDecimal.ZERO) {
            throw JsonSchemaGenerationException("Unable to satisfy multipleOf with the given bounds.", jsonPointer)
        }

        val step = when {
            isInteger && multipleOfValue != null -> integerStep(multipleOfValue)
            isInteger -> BigDecimal.ONE
            else -> multipleOfValue
        }
        val candidate = if (step != null) {
            val lowerMultiplier = lowerBound?.let { bound ->
                val multiplier = bound.divide(step, 0, RoundingMode.CEILING)
                if (isLowerExclusive && multiplier.multiply(step) <= bound) multiplier + BigDecimal.ONE else multiplier
            }
            val upperMultiplier = upperBound?.let { bound ->
                val multiplier = bound.divide(step, 0, RoundingMode.FLOOR)
                if (isUpperExclusive && multiplier.multiply(step) >= bound) multiplier - BigDecimal.ONE else multiplier
            }
            if (lowerMultiplier != null && upperMultiplier != null && lowerMultiplier > upperMultiplier) {
                val message = if (multipleOfValue == null) "Numeric bounds are contradictory."
                    else "Unable to satisfy multipleOf with the given bounds."
                throw JsonSchemaGenerationException(message, jsonPointer)
            }
            // The finite sampling window is chosen only after deriving the real feasible bounds.
            val sampleLower = lowerMultiplier ?: upperMultiplier?.subtract(BigDecimal(100)) ?: BigDecimal.ZERO
            val sampleUpper = upperMultiplier ?: sampleLower.add(BigDecimal(100))
            val offset = (sampleUpper - sampleLower)
                .multiply(BigDecimal(Random.nextInt(0, 1001)))
                .divide(BigDecimal(1000), 0, RoundingMode.FLOOR)
            (sampleLower + offset).multiply(step)
        } else {
            when {
                lowerBound != null && upperBound != null ->
                    lowerBound + (upperBound - lowerBound).multiply(BigDecimal(Random.nextInt(1, 1000))).divide(BigDecimal(1000))
                lowerBound != null -> lowerBound + BigDecimal(Random.nextInt(1, 1001)).movePointLeft(1)
                upperBound != null -> upperBound - BigDecimal(Random.nextInt(1, 1001)).movePointLeft(1)
                else -> BigDecimal(Random.nextInt(0, 1001)).movePointLeft(1)
            }
        }
        ProgressManager.checkCanceled()
        return candidate
    }

    private fun integerStep(multipleOf: BigDecimal): BigDecimal {
        val normalized = multipleOf.stripTrailingZeros()
        if (normalized.scale() <= 0) return normalized
        val numerator = normalized.unscaledValue()
        // A denominator larger than the numerator's bit length cannot remove additional factors.
        val denominator = BigInteger.TEN.pow(minOf(normalized.scale(), numerator.bitLength()))
        ProgressManager.checkCanceled()
        return BigDecimal(numerator.divide(numerator.gcd(denominator)))
    }
}
