package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class JsonSchemaNodeAdapterTest {
    private val factory = JsonNodeFactory.instance

    @Test
    fun preservesNumbersWithoutTextRoundTripOrPrecisionLoss() {
        val integer = BigInteger("123456789012345678901234567890")
        val decimal = BigDecimal("0.123456789012345678901234567890")
        val source = factory.arrayNode().add(42).add(Long.MAX_VALUE).add(integer)
            .add(1.25f).add(2.5).add(decimal)

        val result = JsonSchemaNodeAdapter.convert(source)

        assertEquals(42, result[0].intValue())
        assertEquals(Long.MAX_VALUE, result[1].longValue())
        assertEquals(integer, result[2].bigIntegerValue())
        assertEquals(1.25f, result[3].floatValue(), 0.0f)
        assertEquals(2.5, result[4].doubleValue(), 0.0)
        assertEquals(0, decimal.compareTo(result[5].decimalValue()))
    }

    @Test
    fun preservesNestedValuesAndDoesNotShareMutableContainers() {
        val source = factory.objectNode().apply {
            putArray("a/b~c").add("한글\n\"quoted\"").add(true).addNull()
            putObject("empty")
        }
        val result = JsonSchemaNodeAdapter.convert(source)
        source.withArray("a/b~c").removeAll()

        assertEquals("한글\n\"quoted\"", result["a/b~c"][0].stringValue())
        assertTrue(result["a/b~c"][1].booleanValue())
        assertTrue(result["a/b~c"][2].isNull)
        assertTrue(result["empty"].isObject)
        assertEquals(0, result["empty"].size())
    }

    @Test
    fun preservesBinaryMissingAndNonFiniteNodes() {
        val bytes = byteArrayOf(0, 1, -1)
        assertArrayEquals(bytes, JsonSchemaNodeAdapter.convert(factory.binaryNode(bytes)).binaryValue())
        assertTrue(JsonSchemaNodeAdapter.convert(factory.missingNode()).isMissingNode)
        assertTrue(JsonSchemaNodeAdapter.convert(factory.numberNode(Double.NaN)).doubleValue().isNaN())
        assertEquals(Double.POSITIVE_INFINITY,
            JsonSchemaNodeAdapter.convert(factory.numberNode(Double.POSITIVE_INFINITY)).doubleValue(), 0.0)
    }
}
