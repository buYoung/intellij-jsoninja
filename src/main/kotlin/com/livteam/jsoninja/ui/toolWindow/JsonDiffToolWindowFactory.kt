package com.livteam.jsoninja.ui.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Tool Window Factory for JSON Diff functionality
 */
class JsonDiffToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Don't create any initial content
        // Content will be added when ShowJsonDiffAction is triggered
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}