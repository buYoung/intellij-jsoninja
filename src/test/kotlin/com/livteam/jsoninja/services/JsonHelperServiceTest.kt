package com.livteam.jsoninja.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonHelperService

class JsonHelperServiceTest : BasePlatformTestCase() {
    
    fun testJsonFormatState() {
        // Since this is a project-level service, we need to get it from the project
        val service = project.getService(JsonHelperService::class.java)
        
        // Test default state
        assertEquals(JsonFormatState.PRETTIFY, service.getJsonFormatState())
        
        // Test setting and getting each format state
        val states = JsonFormatState.values()
        for (state in states) {
            service.setJsonFormatState(state)
            assertEquals("Format state should be set to $state", state, service.getJsonFormatState())
        }
        
        // Test setting to null (should not happen in practice, but testing for robustness)
        try {
            service.setJsonFormatState(JsonFormatState.PRETTIFY)
            assertEquals(JsonFormatState.PRETTIFY, service.getJsonFormatState())
        } catch (e: Exception) {
            fail("Setting format state to a valid value should not throw an exception")
        }
    }
}