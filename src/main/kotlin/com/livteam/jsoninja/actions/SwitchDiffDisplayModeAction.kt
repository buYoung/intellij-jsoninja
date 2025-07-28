package com.livteam.jsoninja.actions

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.diff.JsonDiffRequestChain
import com.livteam.jsoninja.ui.diff.JsonDiffVirtualFile

/**
 * Action to switch between Editor Tab and Window display modes for JSON diff
 * Note: This action will close the current diff view and reopen in the new mode
 */
class SwitchDiffDisplayModeAction(
    private val requestChain: JsonDiffRequestChain
) : AnAction(
    LocalizationBundle.message("action.switch.diff.display.mode"),
    LocalizationBundle.message("action.switch.diff.display.mode.description"),
    AllIcons.Actions.ChangeView
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = JsoninjaSettingsState.getInstance(project)
        
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
        
        // Note: Switching modes requires closing current diff and reopening
        // For now, just inform the user
        Messages.showInfoMessage(
            project,
            LocalizationBundle.message("action.switch.diff.display.mode.message", newMode.name),
            LocalizationBundle.message("action.switch.diff.display.mode")
        )
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