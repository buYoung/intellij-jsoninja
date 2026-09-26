package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import net.datafaker.Faker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.util.Locale
import java.util.Random

class SampleValueGeneratorTest {
    private val generator = SampleValueGenerator(Faker(Locale.ENGLISH, Random(1729)))

    @Test
    fun realisticStringProvidersProduceUsableValues() {
        val fields = listOf("email", "fullName", "phone", "city", "country", "streetAddress", "websiteUrl")
        val values = fields.associateWith { fieldName ->
            val value = generator.generatePrimitiveValue(fieldName, TypePrimitiveKind.STRING, true)
            assertTrue("$fieldName must produce text", value is String)
            (value as String).also { assertTrue("$fieldName must not be blank", it.isNotBlank()) }
        }

        assertTrue(values.getValue("email").contains('@'))
        val url = URI(values.getValue("websiteUrl"))
        assertTrue(url.scheme in setOf("http", "https"))
        assertTrue(!url.host.isNullOrBlank())
    }

    @Test
    fun realisticNumericAndBooleanSamplesKeepTheirContracts() {
        val id = generator.generatePrimitiveValue("userId", TypePrimitiveKind.INTEGER, true)
        val age = generator.generatePrimitiveValue("age", TypePrimitiveKind.INTEGER, true)
        assertTrue(id is Int && id in 1 until 10_000)
        assertTrue(age is Int && age in 18 until 70)
        assertEquals(true, generator.generatePrimitiveValue("isEnabled", TypePrimitiveKind.BOOLEAN, true))
    }

    @Test
    fun disabledRealisticDataKeepsDeterministicPrimitiveDefaults() {
        val expectedValues = mapOf(
            TypePrimitiveKind.STRING to "email",
            TypePrimitiveKind.INTEGER to 1,
            TypePrimitiveKind.DECIMAL to 1.0,
            TypePrimitiveKind.NUMBER to 1,
            TypePrimitiveKind.BOOLEAN to false,
        )
        expectedValues.forEach { (kind, expected) ->
            assertEquals(expected, generator.generatePrimitiveValue("email", kind, false))
        }
    }
}
