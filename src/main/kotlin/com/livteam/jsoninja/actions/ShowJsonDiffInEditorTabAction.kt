package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonDiffDisplayMode

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
        ShowJsonDiffAction.openDiffForCurrentJson(project, JsonDiffDisplayMode.EDITOR_TAB)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
