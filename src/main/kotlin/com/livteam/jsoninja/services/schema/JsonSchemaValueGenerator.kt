package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import net.datafaker.Faker
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@Service(Service.Level.PROJECT)
class JsonSchemaValueGenerator(private val project: Project) {
    private val normalizer = project.service<JsonSchemaNormalizer>()
    private val validationService = project.service<JsonSchemaValidationService>()
    private val faker = Faker()
    private val jsonNodeFactory = JsonNodeFactory.instance

    companion object {
        private const val DEFAULT_OPTIONAL_PROPERTY_COUNT = 3
    }

    fun generateValue(constraint: JsonSchemaConstraint): JsonNode {
        resolvePresetValue(constraint.schemaNode)?.let { return it.deepCopy() }

        return when (constraint) {
            is BooleanLiteralSchemaConstraint -> generateBooleanLiteral(constraint)
            is ObjectSchemaConstraint -> generateObjectValue(constraint)
            is ArraySchemaConstraint -> generateArrayValue(constraint)
            is StringSchemaConstraint -> generateStringValue(constraint)
            is NumberSchemaConstraint -> generateNumberValue(constraint)
            is IntegerSchemaConstraint -> generateIntegerValue(constraint)
            is BooleanSchemaConstraint -> BooleanNode.valueOf(Random.nextBoolean())
            is NullSchemaConstraint -> NullNode.instance
            is CompositeSchemaConstraint -> generateCompositeValue(constraint)
            is AnySchemaConstraint -> TextNode("value")
        }
    }

    private fun generateBooleanLiteral(constraint: BooleanLiteralSchemaConstraint): JsonNode {
        if (!constraint.allowsAllValues) {
            throw JsonSchemaGenerationException(
                message = "Boolean schema 'false' cannot generate any value.",
                jsonPointer = constraint.jsonPointer
            )
        }
        return TextNode("value")
    }

    private fun generateObjectValue(constraint: ObjectSchemaConstraint): JsonNode {
        val generatedObjectNode = jsonNodeFactory.objectNode()
        val generatedPropertyNames = mutableSetOf<String>()

        constraint.requiredProperties.forEach { requiredPropertyName ->
            val propertyConstraint = constraint.propertyConstraints[requiredPropertyName]
            generatedObjectNode.set<JsonNode>(
                requiredPropertyName,
                generateValue(propertyConstraint ?: AnySchemaConstraint(constraint.jsonPointer, jsonNodeFactory.objectNode()))
            )
            generatedPropertyNames.add(requiredPropertyName)
        }

        val remainingPropertyNames = constraint.propertyConstraints.keys
            .filterNot { propertyName -> generatedPropertyNames.contains(propertyName) }

        val effectiveMinProperties = constraint.minimumProperties
            ?: (generatedPropertyNames.size + min(DEFAULT_OPTIONAL_PROPERTY_COUNT, remainingPropertyNames.size))
        val targetOptionalCount = min(
            remainingPropertyNames.size,
            max(0, effectiveMinProperties - generatedPropertyNames.size)
        )
        remainingPropertyNames.take(targetOptionalCount).forEach { optionalPropertyName ->
            val propertyConstraint = constraint.propertyConstraints.getValue(optionalPropertyName)
            generatedObjectNode.set<JsonNode>(optionalPropertyName, generateValue(propertyConstraint))
            generatedPropertyNames.add(optionalPropertyName)
        }

        constraint.patternPropertyConstraints.forEach { (patternExpression, patternConstraint) ->
            val generatedPropertyName = generatePropertyNameFromPattern(patternExpression)
            if (!generatedObjectNode.has(generatedPropertyName)) {
                generatedObjectNode.set<JsonNode>(generatedPropertyName, generateValue(patternConstraint))
                generatedPropertyNames.add(generatedPropertyName)
            }
        }

        constraint.dependentRequiredProperties.forEach { (triggerPropertyName, dependentPropertyNames) ->
            if (generatedObjectNode.has(triggerPropertyName)) {
                dependentPropertyNames.forEach { dependentPropertyName ->
                    if (!generatedObjectNode.has(dependentPropertyName)) {
                        val dependentPropertyConstraint = constraint.propertyConstraints[dependentPropertyName]
                            ?: AnySchemaConstraint(constraint.jsonPointer, jsonNodeFactory.objectNode())
                        generatedObjectNode.set<JsonNode>(dependentPropertyName, generateValue(dependentPropertyConstraint))
                        generatedPropertyNames.add(dependentPropertyName)
                    }
                }
            }
        }

        constraint.dependentSchemaConstraints.forEach { (triggerPropertyName, dependentConstraint) ->
            if (generatedObjectNode.has(triggerPropertyName)) {
                val generatedDependentNode = generateValue(dependentConstraint)
                if (generatedDependentNode.isObject) {
                    val dependentObjectNode = generatedDependentNode as ObjectNode
                    dependentObjectNode.fields().forEachRemaining { dependentField ->
                        if (!generatedObjectNode.has(dependentField.key)) {
                            generatedObjectNode.set<JsonNode>(dependentField.key, dependentField.value)
                            generatedPropertyNames.add(dependentField.key)
                        }
                    }
                }
            }
        }

        val maximumProperties = constraint.maximumProperties
        if (maximumProperties != null && generatedObjectNode.size() > maximumProperties) {
            val removablePropertyNames = generatedObjectNode.fieldNames().asSequence().toList()
                .filterNot { propertyName -> constraint.requiredProperties.contains(propertyName) }
            val removableIterator = removablePropertyNames.iterator()
            while (generatedObjectNode.size() > maximumProperties && removableIterator.hasNext()) {
                generatedObjectNode.remove(removableIterator.next())
            }
        }

        return generatedObjectNode
    }

    private fun generateArrayValue(constraint: ArraySchemaConstraint): JsonNode {
        val generatedArrayNode = jsonNodeFactory.arrayNode()
        val minimumItems = constraint.minimumItems ?: 0
        val maximumItems = constraint.maximumItems ?: max(3, minimumItems)

        if (minimumItems > maximumItems) {
            throw JsonSchemaGenerationException(
                message = "Array bounds are contradictory (minItems > maxItems).",
                jsonPointer = constraint.jsonPointer
            )
        }

        constraint.prefixItemConstraints.forEach { prefixConstraint ->
            if (generatedArrayNode.size() < maximumItems) {
                generatedArrayNode.add(generateValue(prefixConstraint))
            }
        }

        val targetCount = max(minimumItems, generatedArrayNode.size())
        while (generatedArrayNode.size() < targetCount && generatedArrayNode.size() < maximumItems) {
            val itemConstraint = constraint.itemConstraint ?: AnySchemaConstraint(constraint.jsonPointer, jsonNodeFactory.objectNode())
            generatedArrayNode.add(generateValue(itemConstraint))
        }

        val containsConstraint = constraint.containsConstraint
        if (containsConstraint != null) {
            val minimumContains = constraint.minimumContains ?: 1
            var matchedContainsCount = countContainsMatches(generatedArrayNode, containsConstraint)
            while (matchedContainsCount < minimumContains) {
                if (generatedArrayNode.size() >= maximumItems) {
                    throw JsonSchemaGenerationException(
                        message = "Unable to satisfy contains/minContains without exceeding maxItems.",
                        jsonPointer = constraint.jsonPointer
                    )
                }
                generatedArrayNode.add(generateValue(containsConstraint))
                matchedContainsCount = countContainsMatches(generatedArrayNode, containsConstraint)
            }

            val maximumContains = constraint.maximumContains
            if (maximumContains != null && matchedContainsCount > maximumContains) {
                throw JsonSchemaGenerationException(
                    message = "Generated array violates maxContains constraint.",
                    jsonPointer = constraint.jsonPointer
                )
            }
        }

        if (constraint.uniqueItems) {
            enforceUniqueArrayItems(generatedArrayNode, constraint.itemConstraint, constraint.jsonPointer)
        }

        return generatedArrayNode
    }

    private fun countContainsMatches(generatedArrayNode: ArrayNode, containsConstraint: JsonSchemaConstraint): Int {
        return generatedArrayNode.count { arrayElement ->
            validationService.validateAgainstSchemaNode(containsConstraint.schemaNode, arrayElement)
        }
    }

    private fun enforceUniqueArrayItems(
        generatedArrayNode: ArrayNode,
        itemConstraint: JsonSchemaConstraint?,
        jsonPointer: String
    ) {
        val elementTextValues = mutableSetOf<String>()
        for (index in 0 until generatedArrayNode.size()) {
            val existingElement = generatedArrayNode[index]
            val elementText = existingElement.toString()
            if (!elementTextValues.add(elementText)) {
                val replacementConstraint = itemConstraint ?: AnySchemaConstraint(jsonPointer, jsonNodeFactory.objectNode())
                val replacementElement = generateValue(replacementConstraint)
                if (!elementTextValues.add(replacementElement.toString())) {
                    throw JsonSchemaGenerationException(
                        message = "Unable to satisfy uniqueItems constraint.",
                        jsonPointer = jsonPointer
                    )
                }
                generatedArrayNode.set(index, replacementElement)
            }
        }
    }

    private fun generateStringValue(constraint: StringSchemaConstraint): JsonNode {
        var generatedValue = generateStringByFormatOrPattern(
            formatName = constraint.formatName,
            patternExpression = constraint.patternExpression
        )

        val minimumLength = constraint.minimumLength ?: 0
        val maximumLength = constraint.maximumLength ?: max(minimumLength, generatedValue.length + 4)

        if (minimumLength > maximumLength) {
            throw JsonSchemaGenerationException(
                message = "String bounds are contradictory (minLength > maxLength).",
                jsonPointer = constraint.jsonPointer
            )
        }

        if (generatedValue.length < minimumLength) {
            val missingLength = minimumLength - generatedValue.length
            generatedValue += "x".repeat(missingLength)
        }
        if (generatedValue.length > maximumLength) {
            generatedValue = generatedValue.take(maximumLength)
        }

        val patternExpression = constraint.patternExpression
        if (!patternExpression.isNullOrBlank()) {
            val generatedMatchesPattern = runCatching {
                Regex(patternExpression).matches(generatedValue)
            }.getOrDefault(false)
            if (!generatedMatchesPattern) {
                throw JsonSchemaGenerationException(
                    message = "Unable to generate value for pattern: $patternExpression",
                    jsonPointer = constraint.jsonPointer
                )
            }
        }

        return TextNode(generatedValue)
    }

    private fun generateNumberValue(constraint: NumberSchemaConstraint): JsonNode {
        val generatedValue = generateNumericValue(
            minimumValue = constraint.minimumValue,
            maximumValue = constraint.maximumValue,
            exclusiveMinimumValue = constraint.exclusiveMinimumValue,
            exclusiveMaximumValue = constraint.exclusiveMaximumValue,
            multipleOfValue = constraint.multipleOfValue,
            integerOnly = false,
            jsonPointer = constraint.jsonPointer
        )
        return DecimalNode(generatedValue)
    }

    private fun generateIntegerValue(constraint: IntegerSchemaConstraint): JsonNode {
        val generatedValue = generateNumericValue(
            minimumValue = constraint.minimumValue,
            maximumValue = constraint.maximumValue,
            exclusiveMinimumValue = constraint.exclusiveMinimumValue,
            exclusiveMaximumValue = constraint.exclusiveMaximumValue,
            multipleOfValue = constraint.multipleOfValue,
            integerOnly = true,
            jsonPointer = constraint.jsonPointer
        )
        return IntNode(generatedValue.toInt())
    }

    private fun generateCompositeValue(constraint: CompositeSchemaConstraint): JsonNode {
        if (constraint.oneOfConstraints.isNotEmpty()) {
            return generateOneOfValue(constraint)
        }

        if (constraint.anyOfConstraints.isNotEmpty()) {
            return generateAnyOfValue(constraint)
        }

        if (constraint.ifConstraint != null || constraint.thenConstraint != null || constraint.elseConstraint != null) {
            return generateConditionalValue(constraint)
        }

        if (constraint.allOfConstraints.isNotEmpty()) {
            val mergedSchemaNode = constraint.allOfConstraints.fold(
                removeKeywordFromSchema(constraint.schemaNode, "allOf")
            ) { accumulatedSchemaNode, allOfConstraint ->
                mergeSchemaNodes(accumulatedSchemaNode, allOfConstraint.schemaNode)
            }
            val mergedConstraint = normalizer.createConstraint(mergedSchemaNode, constraint.jsonPointer)
            val generatedNode = generateValue(mergedConstraint)
            if (!validationService.validateAgainstSchemaNode(constraint.schemaNode, generatedNode)) {
                throw JsonSchemaGenerationException(
                    message = "Unable to satisfy allOf constraints.",
                    jsonPointer = constraint.jsonPointer
                )
            }
            return generatedNode
        }

        val generatedBaseNode = constraint.baseConstraint?.let { generateValue(it) } ?: TextNode("value")
        constraint.notConstraint?.let { notConstraint ->
            if (validationService.validateAgainstSchemaNode(notConstraint.schemaNode, generatedBaseNode)) {
                throw JsonSchemaGenerationException(
                    message = "Generated value violates not constraint.",
                    jsonPointer = constraint.jsonPointer
                )
            }
        }

        if (!validationService.validateAgainstSchemaNode(constraint.schemaNode, generatedBaseNode)) {
            throw JsonSchemaGenerationException(
                message = "Unable to satisfy composite constraints.",
                jsonPointer = constraint.jsonPointer
            )
        }
        return generatedBaseNode
    }

    private fun generateOneOfValue(constraint: CompositeSchemaConstraint): JsonNode {
        val baseSchemaWithoutOneOf = removeKeywordFromSchema(constraint.schemaNode, "oneOf")
        constraint.oneOfConstraints.forEachIndexed { index, oneOfConstraint ->
            val candidateSchemaNode = mergeSchemaNodes(baseSchemaWithoutOneOf, oneOfConstraint.schemaNode)
            val generatedCandidate = generateValue(
                normalizer.createConstraint(
                    candidateSchemaNode,
                    "${constraint.jsonPointer}/oneOf/$index"
                )
            )

            val validBranchCount = constraint.oneOfConstraints.count { branchConstraint ->
                validationService.validateAgainstSchemaNode(branchConstraint.schemaNode, generatedCandidate)
            }
            val validatesComposite = validationService.validateAgainstSchemaNode(constraint.schemaNode, generatedCandidate)
            if (validBranchCount == 1 && validatesComposite) {
                return generatedCandidate
            }
        }

        throw JsonSchemaGenerationException(
            message = "Unable to satisfy oneOf constraints.",
            jsonPointer = constraint.jsonPointer
        )
    }

    private fun generateAnyOfValue(constraint: CompositeSchemaConstraint): JsonNode {
        val baseSchemaWithoutAnyOf = removeKeywordFromSchema(constraint.schemaNode, "anyOf")
        constraint.anyOfConstraints.forEachIndexed { index, anyOfConstraint ->
            val candidateSchemaNode = mergeSchemaNodes(baseSchemaWithoutAnyOf, anyOfConstraint.schemaNode)
            val generatedCandidate = generateValue(
                normalizer.createConstraint(
                    candidateSchemaNode,
                    "${constraint.jsonPointer}/anyOf/$index"
                )
            )
            if (validationService.validateAgainstSchemaNode(constraint.schemaNode, generatedCandidate)) {
                return generatedCandidate
            }
        }

        throw JsonSchemaGenerationException(
            message = "Unable to satisfy anyOf constraints.",
            jsonPointer = constraint.jsonPointer
        )
    }

    private fun generateConditionalValue(constraint: CompositeSchemaConstraint): JsonNode {
        val baseSchemaWithoutConditional = removeKeywordsFromSchema(
            schemaNode = constraint.schemaNode,
            keywordNames = setOf("if", "then", "else")
        )

        val ifConstraint = constraint.ifConstraint
        val thenConstraint = constraint.thenConstraint
        val elseConstraint = constraint.elseConstraint

        if (ifConstraint != null && thenConstraint != null) {
            val thenSchemaNode = mergeSchemaNodes(baseSchemaWithoutConditional, thenConstraint.schemaNode)
            val generatedThenNode = generateValue(normalizer.createConstraint(thenSchemaNode, constraint.jsonPointer))
            val validatesIf = validationService.validateAgainstSchemaNode(ifConstraint.schemaNode, generatedThenNode)
            val validatesFull = validationService.validateAgainstSchemaNode(constraint.schemaNode, generatedThenNode)
            if (validatesIf && validatesFull) {
                return generatedThenNode
            }
        }

        if (ifConstraint != null && elseConstraint != null) {
            val elseSchemaNode = mergeSchemaNodes(baseSchemaWithoutConditional, elseConstraint.schemaNode)
            val generatedElseNode = generateValue(normalizer.createConstraint(elseSchemaNode, constraint.jsonPointer))
            val validatesIf = validationService.validateAgainstSchemaNode(ifConstraint.schemaNode, generatedElseNode)
            val validatesFull = validationService.validateAgainstSchemaNode(constraint.schemaNode, generatedElseNode)
            if (!validatesIf && validatesFull) {
                return generatedElseNode
            }
        }

        val generatedFallbackNode = constraint.baseConstraint?.let { generateValue(it) }
            ?: generateValue(normalizer.createConstraint(baseSchemaWithoutConditional, constraint.jsonPointer))

        if (!validationService.validateAgainstSchemaNode(constraint.schemaNode, generatedFallbackNode)) {
            throw JsonSchemaGenerationException(
                message = "Unable to satisfy if/then/else constraints.",
                jsonPointer = constraint.jsonPointer
            )
        }

        return generatedFallbackNode
    }

    private fun resolvePresetValue(schemaNode: JsonNode): JsonNode? {
        val constNode = schemaNode.path("const")
        if (!constNode.isMissingNode) {
            return constNode
        }

        val enumNode = schemaNode.path("enum")
        if (enumNode.isArray && enumNode.size() > 0) {
            return enumNode.first()
        }

        val defaultNode = schemaNode.path("default")
        if (!defaultNode.isMissingNode && !defaultNode.isNull) {
            return defaultNode
        }

        val examplesNode = schemaNode.path("examples")
        if (examplesNode.isArray && examplesNode.size() > 0) {
            return examplesNode.first()
        }

        return null
    }

    private fun generateStringByFormatOrPattern(formatName: String?, patternExpression: String?): String {
        formatName?.let { format ->
            return when (format) {
                "email" -> faker.internet().emailAddress()
                "uuid" -> faker.internet().uuid()
                "uri", "url" -> faker.internet().url()
                "hostname" -> faker.internet().domainName()
                "date-time" -> Instant.now().atOffset(ZoneOffset.UTC).toString()
                "date" -> LocalDate.now(ZoneOffset.UTC).toString()
                "ipv4" -> faker.internet().ipV4Address()
                else -> "value"
            }
        }

        patternExpression?.let { pattern ->
            if (pattern == "^[0-9]+$" || pattern == "^[\\\\d]+$") {
                return "123456"
            }
            if (pattern == "^[A-Za-z]+$" || pattern == "^[a-zA-Z]+$") {
                return "sampleText"
            }
            if (pattern == "^[a-zA-Z0-9_-]+$") {
                return "sample_value_1"
            }

            val fixedDigitPattern = Regex("""^\^\\d\{(\d+)}\$$""")
            val fixedDigitMatch = fixedDigitPattern.matchEntire(pattern)
            if (fixedDigitMatch != null) {
                val digitCount = fixedDigitMatch.groupValues[1].toInt()
                return "1".repeat(digitCount)
            }
        }

        return "value"
    }

    private fun generateNumericValue(
        minimumValue: BigDecimal?,
        maximumValue: BigDecimal?,
        exclusiveMinimumValue: BigDecimal?,
        exclusiveMaximumValue: BigDecimal?,
        multipleOfValue: BigDecimal?,
        integerOnly: Boolean,
        jsonPointer: String
    ): BigDecimal {
        var lowerBound = exclusiveMinimumValue ?: minimumValue ?: BigDecimal.ZERO
        var upperBound = exclusiveMaximumValue ?: maximumValue ?: BigDecimal.valueOf(100)

        if (exclusiveMinimumValue != null) {
            lowerBound = lowerBound.add(if (integerOnly) BigDecimal.ONE else BigDecimal("0.1"))
        }
        if (exclusiveMaximumValue != null) {
            upperBound = upperBound.subtract(if (integerOnly) BigDecimal.ONE else BigDecimal("0.1"))
        }

        if (lowerBound > upperBound) {
            throw JsonSchemaGenerationException(
                message = "Numeric bounds are contradictory.",
                jsonPointer = jsonPointer
            )
        }

        var candidateValue = lowerBound

        if (multipleOfValue != null && multipleOfValue.compareTo(BigDecimal.ZERO) > 0) {
            val multiplier = lowerBound.divide(multipleOfValue, 0, RoundingMode.CEILING)
            candidateValue = multiplier.multiply(multipleOfValue)
        }

        if (candidateValue > upperBound) {
            throw JsonSchemaGenerationException(
                message = "Unable to satisfy multipleOf with the given bounds.",
                jsonPointer = jsonPointer
            )
        }

        if (integerOnly) {
            candidateValue = candidateValue.setScale(0, RoundingMode.CEILING)
        } else if (candidateValue.scale() > 6) {
            candidateValue = candidateValue.setScale(6, RoundingMode.HALF_UP)
        }

        return candidateValue
    }

    private fun generatePropertyNameFromPattern(patternExpression: String): String {
        return when {
            patternExpression.contains("[0-9]") -> "123"
            patternExpression.contains("[A-Za-z]") || patternExpression.contains("[a-zA-Z]") -> "propertyName"
            patternExpression.contains("[a-zA-Z0-9_-]") -> "property_name_1"
            else -> "property"
        }
    }

    private fun removeKeywordFromSchema(schemaNode: JsonNode, keyword: String): JsonNode {
        if (!schemaNode.isObject) return schemaNode
        val objectNode = schemaNode.deepCopy() as ObjectNode
        objectNode.remove(keyword)
        return objectNode
    }

    private fun removeKeywordsFromSchema(schemaNode: JsonNode, keywordNames: Set<String>): JsonNode {
        if (!schemaNode.isObject) return schemaNode
        val objectNode = schemaNode.deepCopy() as ObjectNode
        keywordNames.forEach { keywordName -> objectNode.remove(keywordName) }
        return objectNode
    }

    private fun mergeSchemaNodes(baseSchemaNode: JsonNode, overridingSchemaNode: JsonNode): JsonNode {
        if (!baseSchemaNode.isObject || !overridingSchemaNode.isObject) {
            return overridingSchemaNode.deepCopy()
        }

        val mergedSchemaNode = baseSchemaNode.deepCopy() as ObjectNode
        val overridingFieldIterator = overridingSchemaNode.fields()
        while (overridingFieldIterator.hasNext()) {
            val overridingField = overridingFieldIterator.next()
            val existingFieldNode = mergedSchemaNode.path(overridingField.key)
            val mergedFieldNode = mergeSchemaField(
                fieldName = overridingField.key,
                currentFieldNode = existingFieldNode.takeUnless { it.isMissingNode },
                overridingFieldNode = overridingField.value
            )
            mergedSchemaNode.set<JsonNode>(overridingField.key, mergedFieldNode)
        }

        return mergedSchemaNode
    }

    private fun mergeSchemaField(
        fieldName: String,
        currentFieldNode: JsonNode?,
        overridingFieldNode: JsonNode
    ): JsonNode {
        if (currentFieldNode == null || currentFieldNode.isMissingNode) {
            return overridingFieldNode.deepCopy()
        }

        return when (fieldName) {
            "required" -> mergeRequiredArrays(currentFieldNode, overridingFieldNode)
            "enum" -> mergeEnumArrays(currentFieldNode, overridingFieldNode)
            "type" -> mergeTypeField(currentFieldNode, overridingFieldNode)
            "minLength", "minItems", "minProperties", "minimum", "exclusiveMinimum" -> {
                if (currentFieldNode.isNumber && overridingFieldNode.isNumber) {
                    if (currentFieldNode.decimalValue() >= overridingFieldNode.decimalValue()) {
                        currentFieldNode.deepCopy()
                    } else {
                        overridingFieldNode.deepCopy()
                    }
                } else {
                    overridingFieldNode.deepCopy()
                }
            }

            "maxLength", "maxItems", "maxProperties", "maximum", "exclusiveMaximum" -> {
                if (currentFieldNode.isNumber && overridingFieldNode.isNumber) {
                    if (currentFieldNode.decimalValue() <= overridingFieldNode.decimalValue()) {
                        currentFieldNode.deepCopy()
                    } else {
                        overridingFieldNode.deepCopy()
                    }
                } else {
                    overridingFieldNode.deepCopy()
                }
            }

            "properties", "patternProperties", "dependentSchemas" -> {
                if (currentFieldNode.isObject && overridingFieldNode.isObject) {
                    mergeSchemaNodes(currentFieldNode, overridingFieldNode)
                } else {
                    overridingFieldNode.deepCopy()
                }
            }

            else -> {
                if (currentFieldNode.isObject && overridingFieldNode.isObject) {
                    mergeSchemaNodes(currentFieldNode, overridingFieldNode)
                } else {
                    overridingFieldNode.deepCopy()
                }
            }
        }
    }

    private fun mergeRequiredArrays(currentFieldNode: JsonNode, overridingFieldNode: JsonNode): JsonNode {
        if (!currentFieldNode.isArray || !overridingFieldNode.isArray) {
            return overridingFieldNode.deepCopy()
        }

        val mergedArrayNode = jsonNodeFactory.arrayNode()
        val requiredPropertyNames = linkedSetOf<String>()
        currentFieldNode.forEach { requiredNode ->
            if (requiredNode.isTextual) {
                requiredPropertyNames.add(requiredNode.asText())
            }
        }
        overridingFieldNode.forEach { requiredNode ->
            if (requiredNode.isTextual) {
                requiredPropertyNames.add(requiredNode.asText())
            }
        }
        requiredPropertyNames.forEach { requiredPropertyName ->
            mergedArrayNode.add(requiredPropertyName)
        }
        return mergedArrayNode
    }

    private fun mergeEnumArrays(currentFieldNode: JsonNode, overridingFieldNode: JsonNode): JsonNode {
        if (!currentFieldNode.isArray || !overridingFieldNode.isArray) {
            return overridingFieldNode.deepCopy()
        }

        val currentValueSet = currentFieldNode.map { it.toString() }.toSet()
        val mergedArrayNode = jsonNodeFactory.arrayNode()
        overridingFieldNode.forEach { candidateNode ->
            if (currentValueSet.contains(candidateNode.toString())) {
                mergedArrayNode.add(candidateNode)
            }
        }

        return if (mergedArrayNode.isEmpty) overridingFieldNode.deepCopy() else mergedArrayNode
    }

    private fun mergeTypeField(currentFieldNode: JsonNode, overridingFieldNode: JsonNode): JsonNode {
        val currentTypeValues = extractTypeValues(currentFieldNode)
        val overridingTypeValues = extractTypeValues(overridingFieldNode)
        if (currentTypeValues.isEmpty()) {
            return overridingFieldNode.deepCopy()
        }
        if (overridingTypeValues.isEmpty()) {
            return currentFieldNode.deepCopy()
        }

        val intersectionTypeValues = currentTypeValues.intersect(overridingTypeValues.toSet())
        if (intersectionTypeValues.isEmpty()) {
            return overridingFieldNode.deepCopy()
        }

        return if (intersectionTypeValues.size == 1) {
            TextNode(intersectionTypeValues.first())
        } else {
            jsonNodeFactory.arrayNode().apply {
                intersectionTypeValues.forEach { typeName -> add(typeName) }
            }
        }
    }

    private fun extractTypeValues(typeNode: JsonNode): List<String> {
        return when {
            typeNode.isTextual -> listOf(typeNode.asText())
            typeNode.isArray -> typeNode.mapNotNull { elementNode ->
                elementNode.takeIf { it.isTextual }?.asText()
            }

            else -> emptyList()
        }
    }
}
