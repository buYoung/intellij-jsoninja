package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import net.datafaker.Faker
import java.util.Locale
import kotlin.random.Random

class SampleValueGenerator(
    private val faker: Faker = Faker(Locale.ENGLISH),
) {
    fun generatePrimitiveValue(
        fieldName: String,
        primitiveKind: TypePrimitiveKind,
        usesRealisticSampleData: Boolean,
    ): Any? {
        if (!usesRealisticSampleData) {
            return defaultPrimitiveValue(fieldName, primitiveKind)
        }

        val normalizedFieldName = fieldName.lowercase(Locale.ENGLISH)
        return when {
            "email" in normalizedFieldName -> faker.internet().emailAddress()
            "name" in normalizedFieldName -> faker.name().fullName()
            "phone" in normalizedFieldName || "mobile" in normalizedFieldName -> faker.phoneNumber().cellPhone()
            "city" in normalizedFieldName -> faker.address().cityName()
            "country" in normalizedFieldName -> faker.address().country()
            "street" in normalizedFieldName || "address" in normalizedFieldName -> faker.address().streetAddress()
            "url" in normalizedFieldName || "uri" in normalizedFieldName -> faker.internet().url()
            normalizedFieldName == "id" || normalizedFieldName.endsWith("id") -> Random.nextInt(1, 10_000)
            "age" in normalizedFieldName -> Random.nextInt(18, 70)
            normalizedFieldName.startsWith("is") || normalizedFieldName.startsWith("has") || normalizedFieldName.startsWith("can") -> true
            else -> defaultPrimitiveValue(fieldName, primitiveKind)
        }
    }

    private fun defaultPrimitiveValue(
        fieldName: String,
        primitiveKind: TypePrimitiveKind,
    ): Any? {
        return when (primitiveKind) {
            TypePrimitiveKind.STRING -> fieldName.replaceFirstChar(Char::lowercase)
            TypePrimitiveKind.INTEGER -> 1
            TypePrimitiveKind.DECIMAL -> 1.0
            TypePrimitiveKind.NUMBER -> 1
            TypePrimitiveKind.BOOLEAN -> fieldName.startsWith("is") || fieldName.startsWith("has") || fieldName.startsWith("can")
        }
    }
}
