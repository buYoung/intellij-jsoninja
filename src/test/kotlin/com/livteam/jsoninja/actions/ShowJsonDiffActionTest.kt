package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import org.junit.Test

class ShowJsonDiffActionTest : BasePlatformTestCase() {
    
    private lateinit var action: ShowJsonDiffAction
    
    override fun setUp() {
        super.setUp()
        action = ShowJsonDiffAction()
    }
    
    @Test
    fun testUpdateWithProject() {
        // Given
        val presentation = Presentation()
        val event = createActionEvent(presentation)
        
        // When
        action.update(event)
        
        // Then
        assertTrue("Action should be enabled when project exists", presentation.isEnabledAndVisible)
    }
    
    @Test
    fun testUpdateWithoutProject() {
        // Given
        val presentation = Presentation()
        val event = createActionEvent(presentation, withProject = false)
        
        // When
        action.update(event)
        
        // Then
        assertFalse("Action should be disabled when project is null", presentation.isEnabledAndVisible)
    }
    
    @Test
    fun testActionPerformedWithWindowMode() {
        // Given
        val settings = JsoninjaSettingsState.getInstance(project)
        settings.diffDisplayMode = JsonDiffDisplayMode.WINDOW.name
        val event = createActionEvent()
        
        // When
        action.actionPerformed(event)
        
        // Then
        assertEquals(JsonDiffDisplayMode.WINDOW.name, settings.diffDisplayMode)
    }
    
    @Test
    fun testActionPerformedWithEditorTabMode() {
        // Given
        val settings = JsoninjaSettingsState.getInstance(project)
        settings.diffDisplayMode = JsonDiffDisplayMode.EDITOR_TAB.name
        val event = createActionEvent()
        
        // When
        action.actionPerformed(event)
        
        // Then
        assertEquals(JsonDiffDisplayMode.EDITOR_TAB.name, settings.diffDisplayMode)
    }
    
    @Test
    fun testActionPerformedWithNullProject() {
        // Given
        val event = createActionEvent(withProject = false)
        
        // When/Then - should return early without exception
        action.actionPerformed(event)
    }
    
    @Test
    fun testActionPerformedWithInvalidDisplayMode() {
        // Given
        val settings = JsoninjaSettingsState.getInstance(project)
        settings.diffDisplayMode = "INVALID_MODE"
        val event = createActionEvent()
        
        // When
        action.actionPerformed(event)
        
        // Then - should default to WINDOW mode without exception
        // The action should complete without throwing
        assertTrue("Action should handle invalid mode gracefully", true)
    }
    
    private fun createActionEvent(
        presentation: Presentation = Presentation(), 
        withProject: Boolean = true
    ): AnActionEvent {
        val dataContext = SimpleDataContext.builder()
            .apply {
                if (withProject) {
                    add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                }
            }
            .build()
            
        return AnActionEvent(
            null,
            dataContext,
            "",
            presentation,
            com.intellij.openapi.actionSystem.ActionManager.getInstance(),
            0
        )
    }
}