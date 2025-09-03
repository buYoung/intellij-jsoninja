package com.livteam.jsoninja.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService

class JsonFormatterServiceTest : BasePlatformTestCase() {
    private lateinit var jsonFormatterService: JsonFormatterService

    override fun setUp() {
        super.setUp()
        jsonFormatterService = JsonFormatterService(project)
    }

    fun testFormatJson() {
        // Test prettify
        val uglyJson = """{"name":"John","age":30,"address":{"city":"New York","country":"USA"}}"""
        val prettifiedJson = jsonFormatterService.formatJson(uglyJson, JsonFormatState.PRETTIFY)
        assertTrue(prettifiedJson.contains("\n"))
        assertTrue(prettifiedJson.contains("  "))
        assertTrue(prettifiedJson.contains(": "))

        // Test uglify
        val prettyJson = """{
  "name": "John",
  "age": 30,
  "address": {
    "city": "New York",
    "country": "USA"
  }
}"""
        val uglifiedJson = jsonFormatterService.formatJson(prettyJson, JsonFormatState.UGLIFY)
        assertFalse(uglifiedJson.contains("\n"))
        assertFalse(uglifiedJson.contains("  "))

        // Test with invalid JSON
        val invalidJson = "{invalid json}"
        val result = jsonFormatterService.formatJson(invalidJson, JsonFormatState.PRETTIFY)
        assertEquals(invalidJson, result)

        // Test with empty JSON
        val emptyJson = ""
        val emptyResult = jsonFormatterService.formatJson(emptyJson, JsonFormatState.PRETTIFY)
        assertEquals(emptyJson, emptyResult)
    }

    fun testIsValidJson() {
        // Test with valid JSON
        val validJson = """{"name":"John","age":30}"""
        assertTrue(jsonFormatterService.isValidJson(validJson))

        // Test with invalid JSON
        val invalidJson = "{invalid json}"
        assertFalse(jsonFormatterService.isValidJson(invalidJson))

        // Test with empty JSON
        val emptyJson = ""
        assertFalse(jsonFormatterService.isValidJson(emptyJson))
    }

    fun testEscapeJson() {
        // Test escaping JSON
        val json = """{"name":"John","quote":"He said \"Hello\""}"""
        val escaped = jsonFormatterService.escapeJson(json)
        assertTrue(escaped.contains("\\\\"))
        assertTrue(escaped.contains("\\\""))

        // Test with invalid JSON
        val invalidJson = "{invalid json}"
        val invalidResult = jsonFormatterService.escapeJson(invalidJson)
        assertEquals(invalidJson, invalidResult)
    }

    fun testUnescapeJson() {
        // First escape a JSON string using the service's escapeJson method
        val originalJson = """{"name":"John","quote":"He said \"Hello\""}"""
        val escapedJson = jsonFormatterService.escapeJson(originalJson)

        // Now test unescaping it
        val unescaped = jsonFormatterService.unescapeJson(escapedJson)
        assertEquals(originalJson, unescaped)

        // Test with non-escaped JSON
        val nonEscapedJson = """{"name":"John"}"""
        val nonEscapedResult = jsonFormatterService.unescapeJson(nonEscapedJson)
        assertEquals(nonEscapedJson, nonEscapedResult)
    }

    fun testFullyUnescapeJson() {
        // Create a multi-level escaped JSON string by applying escapeJson multiple times
        val originalJson = """{"name":"John"}"""
        var escapedJson = originalJson

        // Apply escapeJson three times to create a multi-level escaped JSON
        repeat(3) {
            escapedJson = jsonFormatterService.escapeJson(escapedJson)
        }

        // Now test fully unescaping it
        val fullyUnescaped = jsonFormatterService.fullyUnescapeJson(escapedJson)
        assertEquals(originalJson, fullyUnescaped)
    }

    fun testSetIndentSize() {
        // Test setting indent size
        jsonFormatterService.setIndentSize(4)
        val uglyJson = """{"name":"John","age":30}"""
        val prettifiedJson = jsonFormatterService.formatJson(uglyJson, JsonFormatState.PRETTIFY)
        assertTrue(prettifiedJson.contains("    ")) // Should have 4 spaces for indentation

        // Reset to default
        jsonFormatterService.resetSettings()
    }

    fun testSetSortKeys() {
        val unsortedJson = """{"z":"last","a":"first","m":"middle"}"""
        // Test sorting keys using sortKeys flag for PRETTIFY state

        val sortedFormattedJson = jsonFormatterService.formatJson(unsortedJson, JsonFormatState.PRETTIFY_SORTED)
        val unsortedFormattedJson = jsonFormatterService.formatJson(unsortedJson, JsonFormatState.PRETTIFY)

        // The keys should be sorted alphabetically: a, m, z
        val indexA = sortedFormattedJson.indexOf("\"a\"")
        val indexM = sortedFormattedJson.indexOf("\"m\"")
        val indexZ = sortedFormattedJson.indexOf("\"z\"")

        assertTrue(indexA < indexM)
        assertTrue(indexM < indexZ)
        // JSON should be pretty printed
        assertTrue(sortedFormattedJson.contains("\n"))
        assertTrue(sortedFormattedJson.contains("  "))
        assertFalse(sortedFormattedJson == unsortedFormattedJson)
        // Reset settings to default
    }

    fun testFormatInvalidJsonWithTrailingContent() {
        // Test the bug case: partially valid JSON with trailing invalid content
        val partiallyValidJson = """{"test":1},"test""""
        val result = jsonFormatterService.formatJson(partiallyValidJson, JsonFormatState.PRETTIFY)

        // The formatter should return the original string unchanged when JSON is invalid
        assertEquals(partiallyValidJson, result)

        // Verify that this JSON is indeed considered invalid
        assertFalse(jsonFormatterService.isValidJson(partiallyValidJson))

        // Additional test cases for similar scenarios
        val anotherInvalidJson = """[1,2,3]extra"""
        val anotherResult = jsonFormatterService.formatJson(anotherInvalidJson, JsonFormatState.PRETTIFY)
        assertEquals(anotherInvalidJson, anotherResult)
        assertFalse(jsonFormatterService.isValidJson(anotherInvalidJson))
    }

    fun testFormatJsonWithTrailingCommaInObject() {
        // Test formatting JSON with trailing comma in object
        val inputWithTrailingComma = """[{"items": 72183,}]"""
        
        // Should be considered valid JSON
        assertTrue(jsonFormatterService.isValidJson(inputWithTrailingComma))
        
        // Should format correctly (trailing comma removed)
        val prettified = jsonFormatterService.formatJson(inputWithTrailingComma, JsonFormatState.PRETTIFY)
        assertTrue(prettified.contains("\n"))
        assertTrue(prettified.contains("  "))
        assertFalse(prettified.contains(",}"))
        assertTrue(prettified.contains("72183"))
        
        // Test uglify mode
        val uglified = jsonFormatterService.formatJson(inputWithTrailingComma, JsonFormatState.UGLIFY)
        assertEquals("""[{"items":72183}]""", uglified)
    }

    fun testFormatJsonWithTrailingCommaInObjectAndArray() {
        // Test formatting JSON with trailing commas in both object and array
        val inputWithTrailingCommas = """[{"items": 72183,},]"""
        
        // Should be considered valid JSON
        assertTrue(jsonFormatterService.isValidJson(inputWithTrailingCommas))
        
        // Should format correctly (trailing commas removed)
        val prettified = jsonFormatterService.formatJson(inputWithTrailingCommas, JsonFormatState.PRETTIFY)
        assertTrue(prettified.contains("\n"))
        assertTrue(prettified.contains("  "))
        assertFalse(prettified.contains(",}"))
        assertFalse(prettified.contains(",]"))
        assertTrue(prettified.contains("72183"))
        
        // Test uglify mode
        val uglified = jsonFormatterService.formatJson(inputWithTrailingCommas, JsonFormatState.UGLIFY)
        assertEquals("""[{"items":72183}]""", uglified)
    }

    fun testFormatSimpleObjectWithTrailingComma() {
        // Test simple object with trailing comma
        val inputWithTrailingComma = """{"a":1,}"""
        
        // Should be considered valid JSON
        assertTrue(jsonFormatterService.isValidJson(inputWithTrailingComma))
        
        // Should format correctly
        val uglified = jsonFormatterService.formatJson(inputWithTrailingComma, JsonFormatState.UGLIFY)
        assertEquals("""{"a":1}""", uglified)
        
        val prettified = jsonFormatterService.formatJson(inputWithTrailingComma, JsonFormatState.PRETTIFY)
        assertTrue(prettified.contains("\n"))
        assertFalse(prettified.contains(",}"))
    }

    fun testFormatArrayWithTrailingComma() {
        // Test array with trailing comma
        val inputWithTrailingComma = """[1,2,3,]"""
        
        // Should be considered valid JSON
        assertTrue(jsonFormatterService.isValidJson(inputWithTrailingComma))
        
        // Should format correctly
        val uglified = jsonFormatterService.formatJson(inputWithTrailingComma, JsonFormatState.UGLIFY)
        assertEquals("[1,2,3]", uglified)
    }

    fun testInvalidJsonWithTrailingTokensStillFails() {
        // Ensure FAIL_ON_TRAILING_TOKENS still works (not related to commas inside JSON)
        val inputWithTrailingTokens = """{"a":1} junk"""
        
        // Should be considered invalid
        assertFalse(jsonFormatterService.isValidJson(inputWithTrailingTokens))
        
        // Should return original when formatting fails
        val result = jsonFormatterService.formatJson(inputWithTrailingTokens, JsonFormatState.PRETTIFY)
        assertEquals(inputWithTrailingTokens, result)
    }
}
