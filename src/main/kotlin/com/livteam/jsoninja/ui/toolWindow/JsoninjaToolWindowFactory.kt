package com.livteam.jsoninja.ui.toolWindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.livteam.jsoninja.actions.OpenSettingsAction
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelView

class JsoninjaToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val jsonHelperPanel = JsoninjaPanelView(project)
        Disposer.register(toolWindow.disposable, jsonHelperPanel)
        val content = ContentFactory.getInstance().createContent(jsonHelperPanel, "", false)
        toolWindow.contentManager.addContent(content)

        // Register actions with the component
        val actionManager = ActionManager.getInstance()
        actionManager.getAction("com.livteam.jsoninja.action.CloseTabAction")?.let { action ->
            action.registerCustomShortcutSet(action.shortcutSet, jsonHelperPanel)
        }

        toolWindow.setTitleActions(listOf(OpenSettingsAction()))
    }

    override fun shouldBeAvailable(project: Project) = true
}
