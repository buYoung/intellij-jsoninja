package com.livteam.jsoninja.services.typeConversion.languages.cpp

import com.livteam.jsoninja.model.typeConversion.TypeEnumValue
import java.math.BigInteger

internal object CppEnumValues {
    private val literal = Regex("([+-]?)(0[xX][0-9a-fA-F]+|0[bB][01]+|0[0-7]*|[1-9][0-9]*)(?:[uU](?:ll|LL|[lL])?|(?:ll|LL|[lL])[uU]?)?")
    private val invalidSeparator = Regex("(?<![0-9a-fA-F])'|'(?![0-9a-fA-F])")
    fun resolve(members: List<Pair<String, String?>>): List<TypeEnumValue> {
        var next: BigInteger? = BigInteger.ZERO
        return members.map { (_, raw) ->
            val value = if (raw == null) next else if (invalidSeparator.containsMatchIn(raw)) null else parse(raw.trim().replace("'", ""))
            next = value?.plus(BigInteger.ONE)
            if (value != null && value.abs() <= BigInteger("9007199254740991")) TypeEnumValue.NumberValue(value.toDouble())
            else TypeEnumValue.Unresolved(raw)
        }
    }
    private fun parse(text: String): BigInteger? {
        val match = literal.matchEntire(text) ?: return null
        val digits = match.groupValues[2]
        val value = when {
            digits.startsWith("0x", true) -> digits.drop(2).toBigIntegerOrNull(16)
            digits.startsWith("0b", true) -> digits.drop(2).toBigIntegerOrNull(2)
            digits.startsWith('0') -> digits.toBigIntegerOrNull(8)
            else -> digits.toBigIntegerOrNull()
        } ?: return null
        return if (match.groupValues[1] == "-") value.negate() else value
    }
}
