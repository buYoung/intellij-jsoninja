package com.livteam.jsoninja.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.JsonFormatState
import org.junit.Test

class JsonDiffServiceTest : BasePlatformTestCase() {
    
    private lateinit var jsonDiffService: JsonDiffService
    private lateinit var formatterService: JsonFormatterService
    
    override fun setUp() {
        super.setUp()
        formatterService = JsonFormatterService()
        jsonDiffService = JsonDiffService(project)
    }
    
    @Test
    fun testValidateAndFormatWithValidJson() {
        // Given
        val validJson = """{"name":"test","value":123}"""
        
        // When
        val (isValid, formatted) = jsonDiffService.validateAndFormat(validJson, false)
        
        // Then
        assertTrue("JSON should be valid", isValid)
        assertNotNull("Formatted JSON should not be null", formatted)
        assertTrue("Formatted JSON should contain proper indentation", formatted!!.contains("\n"))
    }
    
    @Test
    fun testValidateAndFormatWithInvalidJson() {
        // Given
        val invalidJson = """{"name":"test","value":123"""  // Missing closing brace
        
        // When
        val (isValid, formatted) = jsonDiffService.validateAndFormat(invalidJson, false)
        
        // Then
        assertFalse("JSON should be invalid", isValid)
        assertNull("Formatted JSON should be null for invalid JSON", formatted)
    }
    
    @Test
    fun testValidateAndFormatWithSemanticSorting() {
        // Given
        val json = """{"z":"last","a":"first","m":"middle"}"""
        
        // When
        val (isValid, formatted) = jsonDiffService.validateAndFormat(json, true)
        
        // Then
        assertTrue("JSON should be valid", isValid)
        assertNotNull("Formatted JSON should not be null", formatted)
        
        // Check if keys are sorted
        val formattedStr = formatted!!
        val aIndex = formattedStr.indexOf("\"a\"")
        val mIndex = formattedStr.indexOf("\"m\"")
        val zIndex = formattedStr.indexOf("\"z\"")
        
        assertTrue("Key 'a' should come before 'm'", aIndex < mIndex)
        assertTrue("Key 'm' should come before 'z'", mIndex < zIndex)
    }
    
    @Test
    fun testCreateDiffRequestWithValidJson() {
        // Given
        val leftJson = """{"name":"left","value":1}"""
        val rightJson = """{"name":"right","value":2}"""
        val title = "Test Diff"
        
        // When
        val diffRequest = jsonDiffService.createDiffRequest(leftJson, rightJson, title, false)
        
        // Then
        assertNotNull("Diff request should not be null", diffRequest)
        assertEquals("Title should match", title, diffRequest.title)
        assertNotNull("Left content should not be null", diffRequest.contents[0])
        assertNotNull("Right content should not be null", diffRequest.contents[1])
    }
    
    @Test
    fun testCreateDiffRequestWithInvalidJson() {
        // Given
        val leftJson = """{"name":"left","value":1}"""
        val rightJson = """{"name":"right","value":2"""  // Invalid JSON
        
        // When
        val diffRequest = jsonDiffService.createDiffRequest(leftJson, rightJson, null, false)
        
        // Then
        assertNotNull("Diff request should not be null even with invalid JSON", diffRequest)
        assertNotNull("Left content should not be null", diffRequest.contents[0])
        assertNotNull("Right content should not be null", diffRequest.contents[1])
        
        // Should use original JSON when formatting fails
        assertTrue("Right content should contain original invalid JSON", 
            diffRequest.contents[1].document.text.contains(rightJson))
    }
    
    @Test
    fun testCreateDiffRequestWithEmptyJson() {
        // Given
        val leftJson = ""
        val rightJson = "{}"
        
        // When
        val diffRequest = jsonDiffService.createDiffRequest(leftJson, rightJson, null, false)
        
        // Then
        assertNotNull("Diff request should not be null", diffRequest)
        assertEquals("Empty string should remain empty", "", diffRequest.contents[0].document.text)
        assertTrue("Empty object should be formatted", 
            diffRequest.contents[1].document.text.contains("{}"))
    }
    
    @Test
    fun testValidateAndFormatWithComplexJson() {
        // Given
        val complexJson = """
            {
                "users": [
                    {"id": 1, "name": "Alice"},
                    {"id": 2, "name": "Bob"}
                ],
                "settings": {
                    "theme": "dark",
                    "notifications": true
                }
            }
        """.trimIndent()
        
        // When
        val (isValid, formatted) = jsonDiffService.validateAndFormat(complexJson, false)
        
        // Then
        assertTrue("Complex JSON should be valid", isValid)
        assertNotNull("Formatted JSON should not be null", formatted)
        assertTrue("Formatted JSON should maintain structure", 
            formatted!!.contains("users") && formatted.contains("settings"))
    }
}