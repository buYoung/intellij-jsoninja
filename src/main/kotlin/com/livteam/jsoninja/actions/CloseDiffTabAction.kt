package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.ui.panel.JsonDiffPanel

/**
 * JSON Diff 탭 또는 창을 닫는 액션 클래스입니다.
 * IDE의 표준 탭 닫기 단축키(CloseContent)를 재사용합니다.
 * 탭이 여러 개면 현재 탭만 닫고, 하나면 전체 창을 닫습니다.
 */
class CloseDiffTabAction : AnAction(
    "Close Diff Tab",
    "Close current JSON diff tab or window",
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