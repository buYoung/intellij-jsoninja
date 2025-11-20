package com.livteam.jsoninja.ui.toolWindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.livteam.jsoninja.actions.CloseTabAction
import com.livteam.jsoninja.actions.OpenSettingsAction
import com.livteam.jsoninja.ui.component.JsonHelperPanel

class JsoninjaToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val jsonHelperPanel = JsonHelperPanel(project)
        Disposer.register(toolWindow.disposable, jsonHelperPanel)
        val content = ContentFactory.getInstance().createContent(jsonHelperPanel, "", false)
        toolWindow.contentManager.addContent(content)

        // Create action group for tool window
        val group = DefaultActionGroup()
        group.add(OpenSettingsAction())
        group.add(CloseTabAction())
        
        // Register actions with the component
        val actionManager = ActionManager.getInstance()
        actionManager.getAction("com.livteam.jsoninja.action.CloseTabAction")?.let { action ->
            action.registerCustomShortcutSet(action.shortcutSet, jsonHelperPanel)
        }
        
        toolWindow.setTitleActions(listOf(OpenSettingsAction()))
    }

    override fun shouldBeAvailable(project: Project) = true
}
