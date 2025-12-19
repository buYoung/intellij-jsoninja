package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelView

/**
 * JSON 에디터 탭을 닫는 액션 클래스입니다.
 * IDE의 표준 탭 닫기 단축키(CloseContent)를 재사용합니다.
 */
class CloseTabAction : AnAction(
    "Close Tab",
    "Close current JSON editor tab",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JSONinja") ?: return

        // Tool window의 component가 JsonHelperPanelView인지 확인
        val content = toolWindow.contentManager.selectedContent
        val panel = content?.component as? JsoninjaPanelView ?: return

        val presenter = panel.presenter.getTabsPresenter()

        // 탭 개수에 따라 다른 동작 수행
        val canClose = presenter.canCloseCurrentTab()
        if (canClose) {
            // 탭이 2개 이상일 때: 현재 탭만 닫기
            presenter.closeCurrentTab()
        } else {
            // 탭이 1개일 때: Tool Window 자체를 닫기
            val selectedComponent = presenter.getView().selectedComponent
            if (selectedComponent != null && selectedComponent.name != "addNewTab") {
                toolWindow.hide()
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JSONinja")
        if (toolWindow == null || !toolWindow.isVisible) {
            e.presentation.isEnabled = false
            return
        }

        // Tool window의 component가 JsonHelperPanelView인지 확인
        val content = toolWindow.contentManager.selectedContent
        val panel = content?.component as? JsoninjaPanelView
        if (panel == null) {
            e.presentation.isEnabled = false
            return
        }

        val presenter = panel.presenter.getTabsPresenter()
        val selectedComponent = presenter.getView().selectedComponent

        // 현재 선택된 컴포넌트가 "+" 탭이 아닌 경우에만 활성화
        e.presentation.isEnabled = selectedComponent != null && selectedComponent.name != "addNewTab"
    }
}
