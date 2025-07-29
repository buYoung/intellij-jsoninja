package com.livteam.jsoninja.actions

import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.ui.diff.JsonDiffVirtualFile
import com.livteam.jsoninja.utils.JsonHelperUtils

/**
 * Action to show JSON Diff in Editor Tab
 */
class ShowJsonDiffInEditorTabAction : AnAction(
    LocalizationBundle.message("action.show.json.diff.editor.tab"),
    LocalizationBundle.message("action.show.json.diff.editor.tab.description"),
    AllIcons.Actions.SplitVertically
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val jsonDiffService = project.service<JsonDiffService>()

        // Get current JSON from active tab using utility
        val currentJson = JsonHelperUtils.getCurrentJsonFromToolWindow(project)
        
        // Create JSONs with default templates if no content
        val leftJson = currentJson ?: "{}"
        val rightJson = "{}"
        
        // Show as editor tab
        val diffFile = JsonDiffVirtualFile(project, jsonDiffService, leftJson, rightJson)
        DiffEditorTabFilesManager.getInstance(project).showDiffFile(diffFile, true)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}