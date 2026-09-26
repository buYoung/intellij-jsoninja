package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.core.JsonParser.NumberType
import com.fasterxml.jackson.databind.JsonNode
import tools.jackson.databind.JsonNode as ValidationNode
import tools.jackson.databind.node.JsonNodeFactory

/** Keeps Jackson 3 at the validator boundary without a JSON text round trip or numeric coercion. */
internal object JsonSchemaNodeAdapter {
    private val factory = JsonNodeFactory.instance

    fun convert(node: JsonNode): ValidationNode = when {
        node.isObject -> factory.objectNode().also { result ->
            node.properties().forEach { (name, value) -> result.set(name, convert(value)) }
        }
        node.isArray -> factory.arrayNode(node.size()).also { result ->
            node.forEach { result.add(convert(it)) }
        }
        node.isTextual -> factory.stringNode(node.textValue())
        node.isBoolean -> factory.booleanNode(node.booleanValue())
        node.isNull -> factory.nullNode()
        node.isMissingNode -> factory.missingNode()
        node.isBinary -> factory.binaryNode(node.binaryValue())
        node.isNumber -> when (node.numberType()) {
            NumberType.INT -> factory.numberNode(node.intValue())
            NumberType.LONG -> factory.numberNode(node.longValue())
            NumberType.BIG_INTEGER -> factory.numberNode(node.bigIntegerValue())
            NumberType.FLOAT -> factory.numberNode(node.floatValue())
            NumberType.DOUBLE -> factory.numberNode(node.doubleValue())
            NumberType.BIG_DECIMAL -> factory.numberNode(node.decimalValue())
            else -> error("Unsupported JSON number type: ${node.numberType()}")
        }
        else -> error("Unsupported JSON node type: ${node.nodeType}")
    }
}
