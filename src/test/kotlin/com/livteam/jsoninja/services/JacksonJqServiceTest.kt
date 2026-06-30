package com.livteam.jsoninja.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.settings.JsoninjaSettingsState

class JacksonJqServiceTest : BasePlatformTestCase() {
    private lateinit var jsonQueryService: JsonQueryService

    override fun setUp() {
        super.setUp()
        jsonQueryService = project.service()
        JsoninjaSettingsState.getInstance(project).jsonQueryType = JsonQueryType.JACKSON_JQ.name
    }

    fun testSimpleFieldFilter() {
        val result = jsonQueryService.query("""{"name":"John","age":30}""", ".name")
        assertEquals("\"John\"", result)
    }

    fun testNestedFilter() {
        val result = jsonQueryService.query(
            """{"address":{"city":"New York","country":"USA"}}""",
            ".address.city"
        )
        assertEquals("\"New York\"", result)
    }

    fun testProjectionFilter() {
        val result = jsonQueryService.query(
            """{"name":"John","age":30,"city":"Seoul"}""",
            "{name, age}"
        )
        assertEquals("""{"name":"John","age":30}""", result)
    }

    fun testMultipleOutputFilter() {
        val result = jsonQueryService.query(
            """{"items":[{"id":1},{"id":2},{"id":3}]}""",
            ".items[]"
        )
        assertEquals("""[{"id":1},{"id":2},{"id":3}]""", result)
    }

    fun testSingleArrayResult() {
        val result = jsonQueryService.query(
            """{"items":[{"name":"Alpha"},{"name":"Beta"}]}""",
            ".items | map(.name)"
        )
        assertEquals("""["Alpha","Beta"]""", result)
    }

    fun testInvalidExpression() {
        val result = jsonQueryService.query("""{"items":[1,2,3]}""", ".items[")
        assertNull(result)
    }

    fun testInvalidJson() {
        val result = jsonQueryService.query("""{"name":}""", ".name")
        assertNull(result)
    }

    fun testIsValidExpression() {
        assertTrue(jsonQueryService.isValidExpression(".name"))
        assertTrue(jsonQueryService.isValidExpression(".items[]"))
        assertFalse(jsonQueryService.isValidExpression(".items["))
    }
}
