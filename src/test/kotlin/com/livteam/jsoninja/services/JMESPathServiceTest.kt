package com.livteam.jsoninja.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.services.JMESPathService

class JMESPathServiceTest : BasePlatformTestCase() {
    private lateinit var jmesPathService: JMESPathService

    override fun setUp() {
        super.setUp()
        jmesPathService = JMESPathService()
    }

    fun testQuery() {
        // Test with valid JSON and valid JsonPath expression
        val jsonString = """{"name": "John", "age": 30, "address": {"city": "New York", "country": "USA"}}"""
        val jsonPathExpression = "$.address.city"
        val result = jmesPathService.query(jsonString, jsonPathExpression)
        assertEquals("\"New York\"", result)

        // Test with valid JSON and invalid JsonPath expression
        val invalidExpression = "$.invalid.path"
        val nullResult = jmesPathService.query(jsonString, invalidExpression)
        assertNull(nullResult)

        // Test with invalid JSON
        val invalidJson = "{invalid json}"
        val errorResult = jmesPathService.query(invalidJson, jsonPathExpression)
        assertNull(errorResult)
    }

    fun testIsValidExpression() {
        // Test with valid JsonPath expression
        val validExpression = "$.name"
        assertTrue(jmesPathService.isValidExpression(validExpression))

        // Test with invalid JsonPath expression
        val invalidExpression = "$.[invalid"
        assertFalse(jmesPathService.isValidExpression(invalidExpression))
    }
}