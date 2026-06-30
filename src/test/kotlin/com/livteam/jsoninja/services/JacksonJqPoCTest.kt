package com.livteam.jsoninja.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.thisptr.jackson.jq.BuiltinFunctionLoader
import net.thisptr.jackson.jq.JsonQuery
import net.thisptr.jackson.jq.Scope
import net.thisptr.jackson.jq.Versions

class JacksonJqPoCTest : BasePlatformTestCase() {
    private lateinit var objectMapper: ObjectMapper
    private lateinit var jqScope: Scope

    override fun setUp() {
        super.setUp()
        objectMapper = service<JsonObjectMapperService>().objectMapper
        jqScope = Scope.newEmptyScope()
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, jqScope)
    }

    fun testRunSimpleFieldFilter() {
        val results = runQuery(
            jsonString = """{"name":"John","age":30}""",
            expression = ".name"
        )

        assertEquals(1, results.size)
        assertEquals("\"John\"", objectMapper.writeValueAsString(results.single()))
    }

    fun testRunNestedFilter() {
        val results = runQuery(
            jsonString = """{"address":{"city":"New York","country":"USA"}}""",
            expression = ".address.city"
        )

        assertEquals(1, results.size)
        assertEquals("\"New York\"", objectMapper.writeValueAsString(results.single()))
    }

    fun testRunProjectionFilter() {
        val results = runQuery(
            jsonString = """{"name":"John","age":30,"city":"Seoul"}""",
            expression = "{name, age}"
        )

        assertEquals(1, results.size)
        assertEquals("""{"name":"John","age":30}""", objectMapper.writeValueAsString(results.single()))
    }

    fun testRunMultipleOutputFilter() {
        val results = runQuery(
            jsonString = """{"items":[{"id":1},{"id":2},{"id":3}]}""",
            expression = ".items[]"
        )

        assertEquals(3, results.size)
        assertEquals("""{"id":1}""", objectMapper.writeValueAsString(results[0]))
        assertEquals("""{"id":2}""", objectMapper.writeValueAsString(results[1]))
        assertEquals("""{"id":3}""", objectMapper.writeValueAsString(results[2]))
    }

    fun testSerializeMultipleOutputsAsJsonArray() {
        val results = runQuery(
            jsonString = """{"items":[{"id":1},{"id":2}]}""",
            expression = ".items[]"
        )

        val serializedArray = serializeResultsAsJsonArray(results)

        assertEquals("""[{"id":1},{"id":2}]""", serializedArray)
    }

    fun testRunBuiltinMapFilter() {
        val results = runQuery(
            jsonString = """{"items":[{"name":"Alpha"},{"name":"Beta"}]}""",
            expression = ".items | map(.name)"
        )

        assertEquals(1, results.size)
        assertEquals("""["Alpha","Beta"]""", objectMapper.writeValueAsString(results.single()))
    }

    fun testRejectInvalidFilter() {
        try {
            runQuery(
                jsonString = """{"items":[1,2,3]}""",
                expression = ".items["
            )
            fail("Invalid jq filter should throw an exception")
        } catch (_: Exception) {
        }
    }

    fun testRejectInvalidJson() {
        try {
            runQuery(
                jsonString = """{"name":}""",
                expression = ".name"
            )
            fail("Invalid JSON should throw an exception")
        } catch (_: Exception) {
        }
    }

    private fun runQuery(jsonString: String, expression: String): List<JsonNode> {
        val inputNode = objectMapper.readTree(jsonString)
        val query = JsonQuery.compile(expression, Versions.JQ_1_6)
        val results = mutableListOf<JsonNode>()

        query.apply(jqScope, inputNode) { result ->
            results.add(result)
        }

        return results
    }

    private fun serializeResultsAsJsonArray(results: List<JsonNode>): String {
        val resultArray = objectMapper.createArrayNode()
        results.forEach { resultArray.add(it) }
        return objectMapper.writeValueAsString(resultArray)
    }
}
