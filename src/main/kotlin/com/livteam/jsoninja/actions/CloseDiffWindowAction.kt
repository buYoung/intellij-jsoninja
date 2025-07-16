package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.ui.panel.JsonDiffPanel

/**
 * ESC 키로 JSON Diff 창을 닫는 액션 클래스입니다.
 * 탭이 여러 개면 현재 탭만 닫고, 하나면 전체 창을 닫습니다.
 */
class CloseDiffWindowAction : AnAction(
    "Close Diff Window",
    "Close JSON diff window",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JSONinja-Diff") ?: return
        
        // 현재 활성화된 content의 component가 JsonDiffPanel인지 확인
        val currentContent = toolWindow.contentManager.selectedContent
        val diffPanel = currentContent?.component as? JsonDiffPanel ?: return
        
        diffPanel.closeCurrentTabOrWindow()
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JSONinja-Diff")
        // Tool window가 존재하고 visible한 경우에만 활성화
        e.presentation.isEnabled = toolWindow != null && toolWindow.isVisible
    }
}