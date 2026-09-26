package com.livteam.jsoninja.services.treesitter

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.livteam.jsoninja.model.typeConversion.TypeEnumValue

internal object TypeScriptEnumValueResolver {
    private const val DIGITS = "[0-9](?:_?[0-9])*"
    private val decimalLiteral = Regex("[+-]?(?:$DIGITS(?:\\.(?:$DIGITS)?)?|\\.$DIGITS)(?:[eE][+-]?$DIGITS)?")
    private val radixLiteral = Regex("([+-]?)0([xXbBoO])([0-9a-fA-F](?:_?[0-9a-fA-F])*)")

    fun resolve(
        members: List<WasmEnumValue>,
        objectMapper: ObjectMapper,
        onUnresolved: (WasmEnumValue) -> Unit,
    ): List<TypeEnumValue> {
        var nextNumericValue: Double? = 0.0
        return members.map { member ->
            val value = if (member.valueText == null) {
                nextNumericValue?.let { TypeEnumValue.NumberValue(it) }
            } else {
                parseLiteral(member.valueText.trim(), objectMapper)
            } ?: TypeEnumValue.Unresolved(member.valueText)
            nextNumericValue = (value as? TypeEnumValue.NumberValue)?.value?.plus(1.0)
            if (value is TypeEnumValue.Unresolved) onUnresolved(member)
            value
        }
    }

    private fun parseLiteral(text: String, objectMapper: ObjectMapper): TypeEnumValue? {
        if (text.startsWith('"') || text.startsWith('\'')) {
            val node = try {
                objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text)
            } catch (_: JsonProcessingException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }
            return node?.takeIf(JsonNode::isTextual)?.asText()?.let { TypeEnumValue.StringValue(it) }
        }
        if (decimalLiteral.matches(text)) {
            return text.replace("_", "").toDoubleOrNull()?.takeIf { it.isFinite() }?.let { TypeEnumValue.NumberValue(it) }
        }
        val radixMatch = radixLiteral.matchEntire(text) ?: return null
        val radix = when (radixMatch.groupValues[2].lowercase()) {
            "x" -> 16
            "o" -> 8
            else -> 2
        }
        val magnitude = radixMatch.groupValues[3].replace("_", "").toBigIntegerOrNull(radix) ?: return null
        val value = if (radixMatch.groupValues[1] == "-") magnitude.negate() else magnitude
        return value.toDouble().takeIf { it.isFinite() }?.let { TypeEnumValue.NumberValue(it) }
    }
}

