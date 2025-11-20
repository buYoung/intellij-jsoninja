package com.livteam.jsoninja.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.settings.JsoninjaSettingsState

class JMESPathServiceTest : BasePlatformTestCase() {
    private lateinit var jsonQueryService: JsonQueryService

    override fun setUp() {
        super.setUp()
        jsonQueryService = project.service()
        JsoninjaSettingsState.getInstance(project).jsonQueryType = JsonQueryType.JMESPATH.name
    }

    fun testQuery() {
        val jsonString = """{"name": "John", "age": 30, "address": {"city": "New York", "country": "USA"}}"""
        val jmesPathExpression = "address.city"
        val result = jsonQueryService.query(jsonString, jmesPathExpression)
        assertEquals("\"New York\"", result)

        val invalidExpression = "address["
        val nullResult = jsonQueryService.query(jsonString, invalidExpression)
        assertNull(nullResult)

        val invalidJson = "{invalid json}"
        val errorResult = jsonQueryService.query(invalidJson, jmesPathExpression)
        assertNull(errorResult)
    }

    fun testIsValidExpression() {
        val validExpression = "name"
        assertTrue(jsonQueryService.isValidExpression(validExpression))

        val invalidExpression = "address["
        assertFalse(jsonQueryService.isValidExpression(invalidExpression))
    }
}
