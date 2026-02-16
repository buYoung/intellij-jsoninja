package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal

sealed interface JsonSchemaConstraint {
    val jsonPointer: String
    val schemaNode: JsonNode
}

data class ObjectSchemaConstraint(
    override val jsonPointer: String,
    override val schemaNode: JsonNode,
    val propertyConstraints: Map<String, JsonSchemaConstraint>,
    val requiredProperties: Set<String>,
    val patternPropertyConstraints: Map<String, JsonSchemaConstraint>,
    val additionalPropertiesConstraint: JsonSchemaConstraint?,
    val allowsAdditionalProperties: Boolean,
    val unevaluatedPropertiesConstraint: JsonSchemaConstraint?,
    val allowsUnevaluatedProperties: Boolean,
    val dependentRequiredProperties: Map<String, Set<String>>,
    val dependentSchemaConstraints: Map<String, JsonSchemaConstraint>,
    val minimumProperties: Int?,
    val maximumProperties: Int?
) : JsonSchemaConstraint

data class ArraySchemaConstraint(
    override val jsonPointer: String,
    override val schemaNode: JsonNode,
    val prefixItemConstraints: List<JsonSchemaConstraint>,
    val itemConstraint: JsonSchemaConstraint?,
    val containsConstraint: JsonSchemaConstraint?,
    val minimumItems: Int?,
    val maximumItems: Int?,
    val uniqueItems: Boolean,
    val minimumContains: Int?,
    val maximumContains: Int?,
    val unevaluatedItemsConstraint: JsonSchemaConstraint?,
    val allowsUnevaluatedItems: Boolean
) : JsonSchemaConstraint

data class StringSchemaConstraint(
    override val jsonPointer: String,
    override val schemaNode: JsonNode,
    val minimumLength: Int?,
    val maximumLength: Int?,
    val patternExpression: String?,
    val formatName: String?
) : JsonSchemaConstraint

data class NumberSchemaConstraint(
    override val jsonPointer: String,
    override val schemaNode: JsonNode,
    val minimumValue: BigDecimal?,
    val maximumValue: BigDecimal?,
    val exclusiveMinimumValue: BigDecimal?,
    val exclusiveMaximumValue: BigDecimal?,
    val multipleOfValue: BigDecimal?
) : JsonSchemaConstraint

data class IntegerSchemaConstraint(
    override val jsonPointer: String,
    override val schemaNode: JsonNode,
    val minimumValue: BigDecimal?,
    val maximumValue: BigDecimal?,
    val exclusiveMinimumValue: BigDecimal?,
    val exclusiveMaximumValue: BigDecimal?,
    val multipleOfValue: BigDecimal?
) : JsonSchemaConstraint

data class BooleanSchemaConstraint(
    override val jsonPointer: String,
    override val schemaNode: JsonNode
) : JsonSchemaConstraint

data class NullSchemaConstraint(
    override val jsonPointer: String,
    override val schemaNode: JsonNode
) : JsonSchemaConstraint

data class CompositeSchemaConstraint(
    override val jsonPointer: String,
    override val schemaNode: JsonNode,
    val allOfConstraints: List<JsonSchemaConstraint>,
    val anyOfConstraints: List<JsonSchemaConstraint>,
    val oneOfConstraints: List<JsonSchemaConstraint>,
    val notConstraint: JsonSchemaConstraint?,
    val ifConstraint: JsonSchemaConstraint?,
    val thenConstraint: JsonSchemaConstraint?,
    val elseConstraint: JsonSchemaConstraint?,
    val baseConstraint: JsonSchemaConstraint?,
    val possibleTypeNames: List<String>
) : JsonSchemaConstraint

data class AnySchemaConstraint(
    override val jsonPointer: String,
    override val schemaNode: JsonNode
) : JsonSchemaConstraint

data class BooleanLiteralSchemaConstraint(
    override val jsonPointer: String,
    override val schemaNode: JsonNode,
    val allowsAllValues: Boolean
) : JsonSchemaConstraint
