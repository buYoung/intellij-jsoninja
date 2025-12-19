package com.livteam.jsoninja.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelView

/**
 * Utility functions for JSONinja plugin
 */
object JsonHelperUtils {

    /**
     * Get current JSON content from the active tab in JSONinja tool window
     * @param project The current project
     * @return The JSON content from active tab, or null if not available
     */
    fun getCurrentJsonFromToolWindow(project: Project): String? {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("JSONinja") ?: return null

        if (!toolWindow.isVisible) return null

        val content = toolWindow.contentManager.selectedContent ?: return null
        val component = content.component as? JsoninjaPanelView ?: return null

        return component.presenter.getCurrentEditor()?.getText()
    }
}
