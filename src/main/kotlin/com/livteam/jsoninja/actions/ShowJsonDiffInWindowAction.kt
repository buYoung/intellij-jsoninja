package com.livteam.jsoninja.actions

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.ui.diff.JsonDiffRequestChain

/**
 * Action to show JSON Diff in separate Window
 */
class ShowJsonDiffInWindowAction : AnAction(
    LocalizationBundle.message("action.show.json.diff.window"),
    LocalizationBundle.message("action.show.json.diff.window.description"),
    AllIcons.Actions.MoveToWindow
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val jsonDiffService = project.service<JsonDiffService>()
        val toolWindowManager = ToolWindowManager.getInstance(project)

        // Get current JSON from active tab
        var currentJson: String? = null
        val toolWindow = toolWindowManager.getToolWindow("JSONinja")

        if (toolWindow != null && toolWindow.isVisible) {
            val content = toolWindow.contentManager.selectedContent
            val component = content?.component

            if (component is com.livteam.jsoninja.ui.component.JsonHelperPanel) {
                val currentEditor = component.getCurrentEditor()
                if (currentEditor != null) {
                    currentJson = currentEditor.getText()
                }
            }
        }

        // Create JSONs with default templates if no content
        val leftJson = currentJson ?: "{}"
        val rightJson = "{}"
        
        // Show as window
        val diffChain = JsonDiffRequestChain(project, jsonDiffService, leftJson, rightJson)
        DiffManager.getInstance().showDiff(project, diffChain, DiffDialogHints.FRAME)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}