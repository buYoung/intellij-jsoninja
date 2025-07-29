package com.livteam.jsoninja.actions

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.diff.JsonDiffRequestChain
import com.livteam.jsoninja.ui.diff.JsonDiffVirtualFile

/**
 * Action to switch between Editor Tab and Window display modes for JSON diff
 * This action allows switching from the current diff view toolbar
 */
class SwitchDiffDisplayModeAction : AnAction {
    
    // Default constructor for plugin.xml
    constructor() : super(
        LocalizationBundle.message("action.switch.diff.display.mode"),
        LocalizationBundle.message("action.switch.diff.display.mode.description"),
        AllIcons.Actions.ChangeView
    )
    
    // Constructor with request chain (for backward compatibility)
    constructor(requestChain: JsonDiffRequestChain) : this()
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = JsoninjaSettingsState.getInstance(project)
        val jsonDiffService = project.service<JsonDiffService>()
        
        // Toggle display mode
        val currentMode = try {
            JsonDiffDisplayMode.valueOf(settings.diffDisplayMode)
        } catch (e: IllegalArgumentException) {
            JsonDiffDisplayMode.EDITOR_TAB
        }
        
        val newMode = when (currentMode) {
            JsonDiffDisplayMode.EDITOR_TAB -> JsonDiffDisplayMode.WINDOW
            JsonDiffDisplayMode.WINDOW -> JsonDiffDisplayMode.EDITOR_TAB
        }
        
        // Save new mode to settings
        settings.diffDisplayMode = newMode.name
        
        // Get current diff contents if available
        // For now, we'll create a new diff with default content
        // In a real implementation, we would need to access the current diff viewer's content
        val leftJson = "{\"example\": \"left\"}"
        val rightJson = "{\"example\": \"right\"}"
        
        // Open in new mode
        when (newMode) {
            JsonDiffDisplayMode.EDITOR_TAB -> {
                val diffFile = JsonDiffVirtualFile(project, jsonDiffService, leftJson, rightJson)
                DiffEditorTabFilesManager.getInstance(project).showDiffFile(diffFile, true)
            }
            JsonDiffDisplayMode.WINDOW -> {
                val diffChain = JsonDiffRequestChain(project, jsonDiffService, leftJson, rightJson)
                DiffManager.getInstance().showDiff(project, diffChain, DiffDialogHints.FRAME)
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        
        // Update icon/text based on current mode
        val settings = project?.let { JsoninjaSettingsState.getInstance(it) }
        val currentMode = settings?.let {
            try {
                JsonDiffDisplayMode.valueOf(it.diffDisplayMode)
            } catch (e: IllegalArgumentException) {
                JsonDiffDisplayMode.EDITOR_TAB
            }
        }
        
        // Show appropriate icon/tooltip based on what mode we'll switch TO
        when (currentMode) {
            JsonDiffDisplayMode.EDITOR_TAB -> {
                e.presentation.text = LocalizationBundle.message("action.switch.to.window")
                e.presentation.description = LocalizationBundle.message("action.switch.to.window.description")
            }
            JsonDiffDisplayMode.WINDOW -> {
                e.presentation.text = LocalizationBundle.message("action.switch.to.editor.tab")
                e.presentation.description = LocalizationBundle.message("action.switch.to.editor.tab.description")
            }
            null -> {}
        }
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}