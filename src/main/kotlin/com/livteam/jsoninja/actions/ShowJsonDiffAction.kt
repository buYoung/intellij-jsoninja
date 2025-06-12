package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.ui.dialog.JsonDiffDialog

/**
 * JSON 차이를 비교하는 액션 클래스
 */
class ShowJsonDiffAction(private val icon: javax.swing.Icon) : AnAction(
    LocalizationBundle.message("action.show.json.diff"),
    LocalizationBundle.message("action.show.json.diff.description"),
    icon
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val jsonDiffService = project.service<JsonDiffService>()

        // Get current JSON from active tab
        var currentJson: String? = null
        val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            .getToolWindow("JSONinja")

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

        val dialog = JsonDiffDialog(project, jsonDiffService, currentJson)
        dialog.show()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}