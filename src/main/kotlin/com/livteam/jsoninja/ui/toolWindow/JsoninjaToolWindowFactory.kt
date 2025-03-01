package com.livteam.jsoninja.ui.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.livteam.jsoninja.ui.component.JsonHelperPanel

class JsoninjaToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val jsonHelperPanel = JsonHelperPanel(project)
        val content = ContentFactory.getInstance().createContent(jsonHelperPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}
