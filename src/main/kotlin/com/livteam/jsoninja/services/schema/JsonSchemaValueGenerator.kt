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
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import net.datafaker.Faker
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.random.Random

@Service(Service.Level.PROJECT)
class JsonSchemaValueGenerator(private val project: Project) {
    private val normalizer = project.service<JsonSchemaNormalizer>()
    private val validationService = project.service<JsonSchemaValidationService>()
    private val faker = Faker()
    private val jsonNodeFactory = JsonNodeFactory.instance

    fun generateValue(
        constraint: JsonSchemaConstraint,
        schemaPropertyGenerationMode: SchemaPropertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL
    ): JsonNode {
        resolvePresetValue(constraint.schemaNode)?.let { return it.deepCopy() }

        val effectiveSchemaPropertyGenerationMode = when (schemaPropertyGenerationMode) {
            SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED -> SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL
            else -> schemaPropertyGenerationMode
        }

        return when (constraint) {
            is BooleanLiteralSchemaConstraint -> generateBooleanLiteral(constraint)
            is ObjectSchemaConstraint -> generateObjectValue(constraint, effectiveSchemaPropertyGenerationMode)
            is ArraySchemaConstraint -> generateArrayValue(constraint, effectiveSchemaPropertyGenerationMode)
            is StringSchemaConstraint -> generateStringValue(constraint)
            is NumberSchemaConstraint -> generateNumberValue(constraint)
            is IntegerSchemaConstraint -> generateIntegerValue(constraint)
            is BooleanSchemaConstraint -> BooleanNode.valueOf(faker.bool().bool())
            is NullSchemaConstraint -> NullNode.instance
            is CompositeSchemaConstraint -> generateCompositeValue(constraint, effectiveSchemaPropertyGenerationMode)
            is AnySchemaConstraint -> TextNode(generateRandomFakerString())
        }
    }

    private fun generateBooleanLiteral(constraint: BooleanLiteralSchemaConstraint): JsonNode {
        if (!constraint.allowsAllValues) {
            throw JsonSchemaGenerationException(
                message = "Boolean schema 'false' cannot generate any value.",
                jsonPointer = constraint.jsonPointer
            )
        }
        return TextNode(generateRandomFakerString())
    }

    private fun generateObjectValue(
        constraint: ObjectSchemaConstraint,
        schemaPropertyGenerationMode: SchemaPropertyGenerationMode
    ): JsonNode {
        val generatedObjectNode = jsonNodeFactory.objectNode()
        val generatedPropertyNames = mutableSetOf<String>()

        constraint.requiredProperties.forEach { requiredPropertyName ->
            val propertyConstraint = constraint.propertyConstraints[requiredPropertyName]
            generatedObjectNode.set<JsonNode>(
                requiredPropertyName,
                generateValue(
                    propertyConstraint ?: AnySchemaConstraint(constraint.jsonPointer, jsonNodeFactory.objectNode()),
                    schemaPropertyGenerationMode
                )
            )
            generatedPropertyNames.add(requiredPropertyName)
        }

        val optionalPropertyNames = constraint.propertyConstraints.keys
            .filterNot { propertyName -> generatedPropertyNames.contains(propertyName) }
        val optionalPropertyNamesToGenerate = when (schemaPropertyGenerationMode) {
            SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL -> optionalPropertyNames
            SchemaPropertyGenerationMode.REQUIRED_ONLY -> {
                val minimumProperties = constraint.minimumProperties ?: generatedPropertyNames.size
                val minimumOptionalPropertyCount = max(0, minimumProperties - generatedPropertyNames.size)
                optionalPropertyNames.take(minimumOptionalPropertyCount)
            }
            SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED -> optionalPropertyNames
        }

        optionalPropertyNamesToGenerate.forEach { optionalPropertyName ->
            val propertyConstraint = constraint.propertyConstraints.getValue(optionalPropertyName)
            val optionalPropertyValue = runCatching {
                generateValue(propertyConstraint, schemaPropertyGenerationMode)
            }.getOrNull() ?: return@forEach
            generatedObjectNode.set<JsonNode>(optionalPropertyName, optionalPropertyValue)
            generatedPropertyNames.add(optionalPropertyName)
        }

        constraint.patternPropertyConstraints.forEach { (patternExpression, patternConstraint) ->
            val generatedPropertyName = generatePropertyNameFromPattern(patternExpression)
            if (generatedPropertyName != null && !generatedObjectNode.has(generatedPropertyName)) {
                val patternPropertyValue = runCatching {
                    generateValue(patternConstraint, schemaPropertyGenerationMode)
                }.getOrNull() ?: return@forEach
                generatedObjectNode.set<JsonNode>(
                    generatedPropertyName,
                    patternPropertyValue
                )
                generatedPropertyNames.add(generatedPropertyName)
            }
        }

        constraint.dependentRequiredProperties.forEach { (triggerPropertyName, dependentPropertyNames) ->
            if (generatedObjectNode.has(triggerPropertyName)) {
                dependentPropertyNames.forEach { dependentPropertyName ->
                    if (!generatedObjectNode.has(dependentPropertyName)) {
                        val dependentPropertyConstraint = constraint.propertyConstraints[dependentPropertyName]
                            ?: AnySchemaConstraint(constraint.jsonPointer, jsonNodeFactory.objectNode())
                        generatedObjectNode.set<JsonNode>(
                            dependentPropertyName,
                            generateValue(dependentPropertyConstraint, schemaPropertyGenerationMode)
                        )
                        generatedPropertyNames.add(dependentPropertyName)
                    }
                }
            }
        }

        constraint.dependentSchemaConstraints.forEach { (triggerPropertyName, dependentConstraint) ->
            if (generatedObjectNode.has(triggerPropertyName)) {
                val generatedDependentNode = generateValue(dependentConstraint, schemaPropertyGenerationMode)
                if (generatedDependentNode.isObject) {
                    val dependentObjectNode = generatedDependentNode as ObjectNode
                    dependentObjectNode.properties().forEach { dependentField ->
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

    private fun generateArrayValue(
        constraint: ArraySchemaConstraint,
        schemaPropertyGenerationMode: SchemaPropertyGenerationMode
    ): JsonNode {
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
                generatedArrayNode.add(generateValue(prefixConstraint, schemaPropertyGenerationMode))
            }
        }

        val targetCount = max(minimumItems, generatedArrayNode.size())
        while (generatedArrayNode.size() < targetCount && generatedArrayNode.size() < maximumItems) {
            val itemConstraint = constraint.itemConstraint ?: AnySchemaConstraint(constraint.jsonPointer, jsonNodeFactory.objectNode())
            generatedArrayNode.add(generateValue(itemConstraint, schemaPropertyGenerationMode))
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
                generatedArrayNode.add(generateValue(containsConstraint, schemaPropertyGenerationMode))
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
            enforceUniqueArrayItems(
                generatedArrayNode,
                constraint.itemConstraint,
                constraint.jsonPointer,
                schemaPropertyGenerationMode
            )
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
        jsonPointer: String,
        schemaPropertyGenerationMode: SchemaPropertyGenerationMode
    ) {
        val elementTextValues = mutableSetOf<String>()
        for (index in 0 until generatedArrayNode.size()) {
            val existingElement = generatedArrayNode[index]
            val elementText = existingElement.toString()
            if (!elementTextValues.add(elementText)) {
                val replacementConstraint = itemConstraint ?: AnySchemaConstraint(jsonPointer, jsonNodeFactory.objectNode())
                val replacementElement = generateValue(replacementConstraint, schemaPropertyGenerationMode)
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

    private fun generateCompositeValue(
        constraint: CompositeSchemaConstraint,
        schemaPropertyGenerationMode: SchemaPropertyGenerationMode
    ): JsonNode {
        if (constraint.oneOfConstraints.isNotEmpty()) {
            return generateOneOfValue(constraint, schemaPropertyGenerationMode)
        }

        if (constraint.anyOfConstraints.isNotEmpty()) {
            return generateAnyOfValue(constraint, schemaPropertyGenerationMode)
        }

        if (constraint.ifConstraint != null || constraint.thenConstraint != null || constraint.elseConstraint != null) {
            return generateConditionalValue(constraint, schemaPropertyGenerationMode)
        }

        if (constraint.allOfConstraints.isNotEmpty()) {
            val mergedSchemaNode = constraint.allOfConstraints.fold(
                removeKeywordFromSchema(constraint.schemaNode, "allOf")
            ) { accumulatedSchemaNode, allOfConstraint ->
                mergeSchemaNodes(accumulatedSchemaNode, allOfConstraint.schemaNode)
            }
            val mergedConstraint = normalizer.createConstraint(mergedSchemaNode, constraint.jsonPointer)
            val generatedNode = generateValue(mergedConstraint, schemaPropertyGenerationMode)
            if (!validationService.validateAgainstSchemaNode(constraint.schemaNode, generatedNode)) {
                throw JsonSchemaGenerationException(
                    message = "Unable to satisfy allOf constraints.",
                    jsonPointer = constraint.jsonPointer
                )
            }
            return generatedNode
        }

        val generatedBaseNode = constraint.baseConstraint?.let {
            generateValue(it, schemaPropertyGenerationMode)
        } ?: TextNode(generateRandomFakerString())
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

    private fun generateOneOfValue(
        constraint: CompositeSchemaConstraint,
        schemaPropertyGenerationMode: SchemaPropertyGenerationMode
    ): JsonNode {
        val baseSchemaWithoutOneOf = removeKeywordFromSchema(constraint.schemaNode, "oneOf")
        constraint.oneOfConstraints.forEachIndexed { index, oneOfConstraint ->
            val candidateSchemaNode = mergeSchemaNodes(baseSchemaWithoutOneOf, oneOfConstraint.schemaNode)
            val generatedCandidate = generateValue(
                normalizer.createConstraint(
                    candidateSchemaNode,
                    "${constraint.jsonPointer}/oneOf/$index"
                ),
                schemaPropertyGenerationMode
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

    private fun generateAnyOfValue(
        constraint: CompositeSchemaConstraint,
        schemaPropertyGenerationMode: SchemaPropertyGenerationMode
    ): JsonNode {
        val baseSchemaWithoutAnyOf = removeKeywordFromSchema(constraint.schemaNode, "anyOf")
        constraint.anyOfConstraints.forEachIndexed { index, anyOfConstraint ->
            val candidateSchemaNode = mergeSchemaNodes(baseSchemaWithoutAnyOf, anyOfConstraint.schemaNode)
            val generatedCandidate = generateValue(
                normalizer.createConstraint(
                    candidateSchemaNode,
                    "${constraint.jsonPointer}/anyOf/$index"
                ),
                schemaPropertyGenerationMode
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

    private fun generateConditionalValue(
        constraint: CompositeSchemaConstraint,
        schemaPropertyGenerationMode: SchemaPropertyGenerationMode
    ): JsonNode {
        val baseSchemaWithoutConditional = removeKeywordsFromSchema(
            schemaNode = constraint.schemaNode,
            keywordNames = setOf("if", "then", "else")
        )

        val ifConstraint = constraint.ifConstraint
        val thenConstraint = constraint.thenConstraint
        val elseConstraint = constraint.elseConstraint

        if (ifConstraint != null && thenConstraint != null) {
            val thenSchemaNode = mergeSchemaNodes(baseSchemaWithoutConditional, thenConstraint.schemaNode)
            val generatedThenNode = generateValue(
                normalizer.createConstraint(thenSchemaNode, constraint.jsonPointer),
                schemaPropertyGenerationMode
            )
            val validatesIf = validationService.validateAgainstSchemaNode(ifConstraint.schemaNode, generatedThenNode)
            val validatesFull = validationService.validateAgainstSchemaNode(constraint.schemaNode, generatedThenNode)
            if (validatesIf && validatesFull) {
                return generatedThenNode
            }
        }

        if (ifConstraint != null && elseConstraint != null) {
            val elseSchemaNode = mergeSchemaNodes(baseSchemaWithoutConditional, elseConstraint.schemaNode)
            val generatedElseNode = generateValue(
                normalizer.createConstraint(elseSchemaNode, constraint.jsonPointer),
                schemaPropertyGenerationMode
            )
            val validatesIf = validationService.validateAgainstSchemaNode(ifConstraint.schemaNode, generatedElseNode)
            val validatesFull = validationService.validateAgainstSchemaNode(constraint.schemaNode, generatedElseNode)
            if (!validatesIf && validatesFull) {
                return generatedElseNode
            }
        }

        val generatedFallbackNode = constraint.baseConstraint?.let {
            generateValue(it, schemaPropertyGenerationMode)
        } ?: generateValue(
            normalizer.createConstraint(baseSchemaWithoutConditional, constraint.jsonPointer),
            schemaPropertyGenerationMode
        )

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
                else -> generateRandomFakerString()
            }
        }

        patternExpression?.let { pattern ->
            if (pattern == "^[0-9]+$" || pattern == "^[\\\\d]+$") {
                return faker.number().digits(Random.nextInt(4, 12))
            }
            if (pattern == "^[A-Za-z]+$" || pattern == "^[a-zA-Z]+$") {
                return faker.regexify("[A-Za-z]{${Random.nextInt(4, 12)}}")
            }
            if (pattern == "^[a-zA-Z0-9_-]+$") {
                return faker.regexify("[A-Za-z0-9_-]{${Random.nextInt(6, 14)}}")
            }

            val fixedDigitPattern = Regex("""^\^\\d\{(\d+)}\$$""")
            val fixedDigitMatch = fixedDigitPattern.matchEntire(pattern)
            if (fixedDigitMatch != null) {
                val digitCount = fixedDigitMatch.groupValues[1].toInt()
                return faker.regexify("\\\\d{$digitCount}")
            }
        }

        return generateRandomFakerString()
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

        val candidateValue = if (multipleOfValue != null && multipleOfValue.compareTo(BigDecimal.ZERO) > 0) {
            val minimumMultiplier = lowerBound.divide(multipleOfValue, 0, RoundingMode.CEILING)
            val maximumMultiplier = upperBound.divide(multipleOfValue, 0, RoundingMode.FLOOR)
            if (minimumMultiplier > maximumMultiplier) {
                throw JsonSchemaGenerationException(
                    message = "Unable to satisfy multipleOf with the given bounds.",
                    jsonPointer = jsonPointer
                )
            }
            val selectedMultiplier = pickRandomMultiplier(minimumMultiplier, maximumMultiplier)
            selectedMultiplier.multiply(multipleOfValue)
        } else if (integerOnly) {
            val lowerIntegerBound = lowerBound.setScale(0, RoundingMode.CEILING)
            val upperIntegerBound = upperBound.setScale(0, RoundingMode.FLOOR)
            if (lowerIntegerBound > upperIntegerBound) {
                throw JsonSchemaGenerationException(
                    message = "Numeric bounds are contradictory.",
                    jsonPointer = jsonPointer
                )
            }
            pickRandomMultiplier(lowerIntegerBound, upperIntegerBound)
        } else {
            val lowerDouble = lowerBound.toDouble()
            val upperDouble = upperBound.toDouble()
            if (!lowerDouble.isFinite() || !upperDouble.isFinite() || lowerDouble == upperDouble) {
                lowerBound
            } else {
                val randomValue = Random.nextDouble(lowerDouble, upperDouble)
                BigDecimal.valueOf(randomValue)
            }
        }

        if (candidateValue > upperBound) {
            throw JsonSchemaGenerationException(
                message = "Unable to satisfy multipleOf with the given bounds.",
                jsonPointer = jsonPointer
            )
        }

        if (integerOnly) {
            return candidateValue.setScale(0, RoundingMode.CEILING)
        }

        return if (candidateValue.scale() > 6) {
            candidateValue.setScale(6, RoundingMode.HALF_UP)
        } else {
            candidateValue
        }
    }

    private fun pickRandomMultiplier(minimumValue: BigDecimal, maximumValue: BigDecimal): BigDecimal {
        val minimumLongValue = runCatching { minimumValue.longValueExact() }.getOrNull()
        val maximumLongValue = runCatching { maximumValue.longValueExact() }.getOrNull()
        if (minimumLongValue == null || maximumLongValue == null || minimumLongValue > maximumLongValue) {
            return minimumValue
        }

        if (minimumLongValue == maximumLongValue) {
            return BigDecimal.valueOf(minimumLongValue)
        }

        if (maximumLongValue == Long.MAX_VALUE) {
            return minimumValue
        }

        val selectedLongValue = Random.nextLong(minimumLongValue, maximumLongValue + 1)
        return BigDecimal.valueOf(selectedLongValue)
    }

    private fun generateRandomFakerString(): String {
        return when (Random.nextInt(8)) {
            0 -> faker.name().fullName()
            1 -> faker.internet().emailAddress()
            2 -> faker.company().name()
            3 -> faker.address().cityName()
            4 -> faker.commerce().productName()
            5 -> faker.job().title()
            6 -> faker.lorem().word()
            else -> faker.regexify("[A-Za-z0-9]{12}")
        }
    }

    private fun generatePropertyNameFromPattern(patternExpression: String): String? {
        val candidateNames = buildList {
            if (patternExpression.contains("^_")) add("_extension")
            if (patternExpression.contains("[0-9]")) add("123")
            if (patternExpression.contains("[A-Za-z]") || patternExpression.contains("[a-zA-Z]")) add("propertyName")
            if (patternExpression.contains("[a-zA-Z0-9_-]")) add("property_name_1")
            add("_property")
            add("property")
            add(faker.regexify("[A-Za-z0-9_]{8}"))
        }.distinct()

        val patternRegex = runCatching { Regex(patternExpression) }.getOrNull()
        if (patternRegex == null) {
            return candidateNames.firstOrNull()
        }

        return candidateNames.firstOrNull { candidateName ->
            patternRegex.matches(candidateName)
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
        for (overridingField in overridingSchemaNode.properties()) {
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
