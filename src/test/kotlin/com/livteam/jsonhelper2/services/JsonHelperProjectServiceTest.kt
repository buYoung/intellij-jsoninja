package com.livteam.jsonhelper2.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.services.JsonHelperProjectService

class JsonHelperProjectServiceTest : BasePlatformTestCase() {
    
    fun testGetRandomNumber() {
        // Since this is a project-level service, we need to get it from the project
        val service = project.getService(JsonHelperProjectService::class.java)
        
        // Test that the random number is within the expected range
        val randomNumber = service.getRandomNumber()
        assertTrue("Random number should be between 1 and 300", randomNumber in 1..300)
        
        // Test that multiple calls return different numbers (not guaranteed but highly likely)
        val anotherRandomNumber = service.getRandomNumber()
        val yetAnotherRandomNumber = service.getRandomNumber()
        
        // At least one of the three numbers should be different from the others
        val allSame = randomNumber == anotherRandomNumber && anotherRandomNumber == yetAnotherRandomNumber
        assertFalse("Multiple calls to getRandomNumber should return different values", allSame)
    }
}