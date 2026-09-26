package com.livteam.jsoninja.services.schema

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.networknt.schema.SpecificationVersion
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.CancellationException

class JsonSchemaValidationServiceTest : BasePlatformTestCase() {
    private val validationService get() = project.service<JsonSchemaValidationService>()
    private val mapper get() = validationService.getStrictObjectMapper()

    fun testSupportedDialectsAndHttpHttpsAliases() {
        for (version in SpecificationVersion.entries) {
            for (scheme in listOf("http", "https")) {
                val dialectId = "$scheme://${version.dialectId.substringAfter("://").removeSuffix("#")}#"
                val schema = mapper.createObjectNode().put("\$schema", dialectId).put("type", "integer").put("minimum", 2)
                assertEquals(version, JsonSchemaTraversal.dialect(schema))
                val compiled = validationService.compileSchema(schema)
                assertTrue(validationService.validateInstance(compiled, mapper.readTree("2")).isValid)
                assertFalse(validationService.validateInstance(compiled, mapper.readTree("1")).isValid)
            }
        }
    }

    fun testMissingDialectUses202012AndNestedDialectInherits() {
        val schema = mapper.readTree("""{"prefixItems":[{"const":1}],"items":false}""")
        assertEquals(SpecificationVersion.DRAFT_2020_12, JsonSchemaTraversal.dialect(schema))
        val compiled = validationService.compileSchema(schema)
        assertTrue(validationService.validateInstance(compiled, mapper.readTree("[1]")).isValid)
        assertFalse(validationService.validateInstance(compiled, mapper.readTree("[1,2]")).isValid)
        assertEquals(SpecificationVersion.DRAFT_4,
            JsonSchemaTraversal.dialect(mapper.readTree("{}"), SpecificationVersion.DRAFT_4))
    }

    fun testUnknownAndNonStringDialectsAreRejected() {
        for (dialect in listOf("\"https://example.invalid/schema\"", "\"json-schema.org/draft-07/schema\"", "null", "42")) {
            val result = validationService.validateSchema(mapper.readTree("""{"${'$'}schema":$dialect}"""))
            assertFalse(result.isValid)
            assertEquals("#/\$schema", result.jsonPointer)
        }
    }

    fun testErrorPointerEscapesPropertyNamesAndIncludesArrayIndex() {
        val schema = mapper.readTree("""{"type":"object","properties":{"a/b~c":{"type":"array","items":{"type":"integer"}}}}""")
        val result = validationService.validateInstance(validationService.compileSchema(schema),
            mapper.readTree("""{"a/b~c":[1,"wrong"]}"""))
        assertFalse(result.isValid)
        assertEquals("#/a~1b~0c/1", result.jsonPointer)
        assertEquals(1, result.validationMessages.size)
        assertTrue(result.errorMessage!!.isNotBlank())
    }

    fun testBooleanSchemasAndInvalidSchemaNodes() {
        assertTrue(validationService.validateAgainstSchemaNode(mapper.readTree("true"), mapper.readTree("null")))
        val result = validationService.validateInstance(validationService.compileSchema(mapper.readTree("false")), mapper.readTree("null"))
        assertFalse(result.isValid)
        assertEquals("#", result.jsonPointer)
        assertFalse(validationService.validateSchema(mapper.readTree("\"not a schema\"")).isValid)
        assertFalse(validationService.validateSchema(mapper.readTree("""{"properties":{"x":"not a schema"}}""")).isValid)
    }

    fun testLargeIntegersAndExactDecimalsReachValidator() {
        for (number in listOf("123456789012345678901234567890", "0.123456789012345678901234567890")) {
            val schema = mapper.readTree("""{"const":$number}""")
            val compiled = validationService.compileSchema(schema)
            assertTrue(validationService.validateInstance(compiled, mapper.readTree(number)).isValid)
            assertFalse(validationService.validateInstance(compiled, mapper.readTree("0")).isValid)
        }
    }

    fun testDraft4AndDraft7ExclusiveMinimumSemantics() {
        for (schemaText in listOf(
            """{"${'$'}schema":"http://json-schema.org/draft-04/schema#","type":"number","minimum":2,"exclusiveMinimum":true}""",
            """{"${'$'}schema":"http://json-schema.org/draft-07/schema#","type":"number","exclusiveMinimum":2}"""
        )) {
            val compiled = validationService.compileSchema(mapper.readTree(schemaText))
            assertFalse(validationService.validateInstance(compiled, mapper.readTree("2")).isValid)
            assertTrue(validationService.validateInstance(compiled, mapper.readTree("2.5")).isValid)
        }
    }

    fun testUnionTypeBoundsAndAdditionalProperties() {
        val compiled = validationService.compileSchema(mapper.readTree("""{"type":["number","null"],"minimum":2,"maximum":4}"""))
        for (value in listOf("null", "2", "4")) assertTrue(validationService.validateInstance(compiled, mapper.readTree(value)).isValid)
        for (value in listOf("1", "5", "\"3\"")) assertFalse(validationService.validateInstance(compiled, mapper.readTree(value)).isValid)
        val objectSchema = validationService.compileSchema(mapper.readTree("""{"type":"object","additionalProperties":{"type":"string"}}"""))
        assertTrue(validationService.validateInstance(objectSchema, mapper.readTree("""{"extra":"ok"}""")).isValid)
        assertFalse(validationService.validateInstance(objectSchema, mapper.readTree("""{"extra":42}""")).isValid)
    }

    fun testFormatAssertionDefaultsFollowDialect() {
        val schema = mapper.createObjectNode().put("type", "string").put("format", "email")
        val instance = mapper.readTree("\"not-an-email\"")
        assertTrue(validationService.validateAgainstSchemaNode(schema, instance))
        schema.put("\$schema", "http://json-schema.org/draft-07/schema#")
        assertFalse(validationService.validateAgainstSchemaNode(schema, instance))
    }

    fun testInstancePayloadKeywordsRemainData() {
        val schema = mapper.readTree("""{"const":{"${'$'}schema":"not-a-dialect","${'$'}ref":"not-a-resource"}}""")
        assertTrue(validationService.validateAgainstSchemaNode(schema, schema["const"]))
    }

    fun testCancellationIsNotReportedAsInvalidJson() {
        val cancelledNode = ObjectNode(JsonNodeFactory.instance, object : LinkedHashMap<String, JsonNode>() {
            override val entries: MutableSet<MutableMap.MutableEntry<String, JsonNode>>
                get() = throw CancellationException("cancelled")
        })
        val compiled = validationService.compileSchema(mapper.readTree("true"))
        org.junit.Assert.assertThrows(CancellationException::class.java) { validationService.compileSchema(cancelledNode) }
        org.junit.Assert.assertThrows(CancellationException::class.java) { validationService.validateInstance(compiled, cancelledNode) }
    }
}
