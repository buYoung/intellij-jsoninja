package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.ui.content.ContentFactory
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.ui.panel.JsonDiffPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread

/**
 * JSON 차이를 비교하는 액션 클래스
 */
class ShowJsonDiffAction : AnAction {
    // 파라미터 없는 생성자 (plugin.xml에서 사용)
    constructor() : super(
        LocalizationBundle.message("action.show.json.diff"),
        LocalizationBundle.message("action.show.json.diff.description"),
        AllIcons.Actions.Diff
    )
    
    // 아이콘을 받는 생성자 (프로그래밍 방식으로 사용)
    constructor(icon: javax.swing.Icon) : super(
        LocalizationBundle.message("action.show.json.diff"),
        LocalizationBundle.message("action.show.json.diff.description"),
        icon
    )
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

        // Create diff panel
        val diffPanel = JsonDiffPanel(project, jsonDiffService, currentJson, project)
        
        // Create content for tool window
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(
            diffPanel,
            LocalizationBundle.message("dialog.json.diff.title"),
            false
        )
        content.isCloseable = true
        
        // Get tool window for diff (already registered in plugin.xml)
        val diffToolWindow = toolWindowManager.getToolWindow("JSONinja-Diff")
        if (diffToolWindow == null) {
            // This should not happen if plugin.xml is configured correctly
            return
        }
        
        // Add content to tool window
        diffToolWindow.contentManager.addContent(content)
        diffToolWindow.contentManager.setSelectedContent(content)
        
        // Set to WINDOWED mode for independent window
        diffToolWindow.setType(ToolWindowType.WINDOWED, null)
        
        // Show the tool window
        diffToolWindow.show()
        
        // Activate the tool window to bring it to front and give focus
        diffToolWindow.activate(null)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}