package com.livteam.jsoninja.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.JsonFormatState

class JsonDiffServiceTest : BasePlatformTestCase() {
    
    private lateinit var jsonDiffService: JsonDiffService
    
    override fun setUp() {
        super.setUp()
        jsonDiffService = JsonDiffService(project)
    }
    
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
    
    fun testValidateAndFormatWithInvalidJson() {
        // Given
        val invalidJson = """{"name":"test",,"value":123}"""  // Double comma - definitely invalid
        
        // When
        val (isValid, formatted) = jsonDiffService.validateAndFormat(invalidJson, false)
        
        // Then
        assertFalse("JSON should be invalid", isValid)
        assertNull("Formatted JSON should be null for invalid JSON", formatted)
    }
    
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
        // Note: We can't directly access document content in tests, but the request is created successfully
    }
    
    fun testCreateDiffRequestWithEmptyJson() {
        // Given
        val leftJson = ""
        val rightJson = "{}"
        
        // When
        val diffRequest = jsonDiffService.createDiffRequest(leftJson, rightJson, null, false)
        
        // Then
        assertNotNull("Diff request should not be null", diffRequest)
        assertEquals("Should have 2 contents", 2, diffRequest.contents.size)
        assertNotNull("Left content should not be null", diffRequest.contents[0])
        assertNotNull("Right content should not be null", diffRequest.contents[1])
    }
    
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