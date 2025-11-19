package com.livteam.jsoninja.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.services.RandomJsonDataCreator
import com.livteam.jsoninja.ui.dialog.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.RootType

class RandomJsonDataCreatorTest : BasePlatformTestCase() {
    private lateinit var randomJsonDataCreator: RandomJsonDataCreator
    private lateinit var objectMapper: ObjectMapper
    
    override fun setUp() {
        super.setUp()
        randomJsonDataCreator = RandomJsonDataCreator()
        objectMapper = service<JsonObjectMapperService>().objectMapper
    }
    
    fun testGenerateConfiguredJsonString_Object() {
        // Test generating a JSON object
        val config = JsonGenerationConfig(
            rootType = RootType.OBJECT,
            objectPropertyCount = 5,
            arrayElementCount = 0,
            propertiesPerObjectInArray = 0,
            maxDepth = 2
        )
        
        val jsonString = randomJsonDataCreator.generateConfiguredJsonString(config)
        
        // Verify the result is valid JSON
        val jsonNode = objectMapper.readTree(jsonString)
        assertTrue("Result should be a JSON object", jsonNode.isObject)
        
        // Verify the object has the expected number of properties
        assertEquals("Object should have 5 properties", 5, jsonNode.size())
    }
    
    fun testGenerateConfiguredJsonString_ArrayOfObjects() {
        // Test generating an array of JSON objects
        val config = JsonGenerationConfig(
            rootType = RootType.ARRAY_OF_OBJECTS,
            objectPropertyCount = 0,
            arrayElementCount = 3,
            propertiesPerObjectInArray = 4,
            maxDepth = 2
        )
        
        val jsonString = randomJsonDataCreator.generateConfiguredJsonString(config)
        
        // Verify the result is valid JSON
        val jsonNode = objectMapper.readTree(jsonString)
        assertTrue("Result should be a JSON array", jsonNode.isArray)
        
        // Verify the array has the expected number of elements
        assertEquals("Array should have 3 elements", 3, jsonNode.size())
        
        // Verify each element is an object with the expected number of properties
        for (i in 0 until jsonNode.size()) {
            val element = jsonNode.get(i)
            assertTrue("Array element should be an object", element.isObject)
            assertEquals("Object in array should have 4 properties", 4, element.size())
        }
    }
    
    fun testGenerateConfiguredJsonString_MaxDepth() {
        // Test that the max depth is respected
        val config = JsonGenerationConfig(
            rootType = RootType.OBJECT,
            objectPropertyCount = 3,
            arrayElementCount = 0,
            propertiesPerObjectInArray = 0,
            maxDepth = 1
        )
        
        val jsonString = randomJsonDataCreator.generateConfiguredJsonString(config)
        
        // Verify the result is valid JSON
        val jsonNode = objectMapper.readTree(jsonString)
        
        // Check that no property is an object or array (depth limit)
        jsonNode.fields().forEach { (_, value) ->
            assertFalse("Properties should not be objects at max depth", value.isObject && value.size() > 0)
            assertFalse("Properties should not be arrays at max depth", value.isArray && value.size() > 0)
        }
    }
    
    fun testGenerateConfiguredJsonString_PrettyPrint() {
        // Test pretty printing
        val config = JsonGenerationConfig(
            rootType = RootType.OBJECT,
            objectPropertyCount = 2,
            arrayElementCount = 0,
            propertiesPerObjectInArray = 0,
            maxDepth = 2
        )
        
        // With pretty print
        val prettyJson = randomJsonDataCreator.generateConfiguredJsonString(config, prettyPrint = true)
        assertTrue("Pretty JSON should contain newlines", prettyJson.contains("\n"))
        assertTrue("Pretty JSON should contain indentation", prettyJson.contains("  "))
        
        // Without pretty print
        val uglyJson = randomJsonDataCreator.generateConfiguredJsonString(config, prettyPrint = false)
        assertFalse("Ugly JSON should not contain newlines", uglyJson.contains("\n"))
        
        // Both should be valid JSON
        val prettyNode = objectMapper.readTree(prettyJson)
        val uglyNode = objectMapper.readTree(uglyJson)
        assertTrue("Pretty JSON should be a valid JSON object", prettyNode.isObject)
        assertTrue("Ugly JSON should be a valid JSON object", uglyNode.isObject)
    }
    
    // Helper function to check if a JsonNode is a primitive value
    private fun isPrimitive(node: JsonNode): Boolean {
        return node.isTextual || node.isNumber || node.isBoolean || node.isNull
    }
}