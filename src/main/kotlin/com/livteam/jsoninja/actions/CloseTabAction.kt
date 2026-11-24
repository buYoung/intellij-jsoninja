package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.LocalizationBundle

/**
 * JSON 에디터 탭을 닫는 액션 클래스입니다.
 * IDE의 표준 탭 닫기 단축키(CloseContent)를 재사용합니다.
 */
class CloseTabAction : DumbAwareAction(
    LocalizationBundle.messagePointer("action.com.livteam.jsoninja.action.CloseTabAction.text"),
    LocalizationBundle.messagePointer("action.com.livteam.jsoninja.action.CloseTabAction.description"),
    null as javax.swing.Icon?
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        val presenter = panel.presenter.getTabsPresenter()

        if (presenter.canCloseCurrentTab()) {
            presenter.closeCurrentTab()
        } else if (!presenter.isPlusTabSelected()) {
            ToolWindowManager.getInstance(project).getToolWindow("JSONinja")?.hide()
        }
    }

    override fun update(e: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(e)
        if (e.project == null || panel == null) {
            e.presentation.isEnabled = false
            return
        }

        val presenter = panel.presenter.getTabsPresenter()
        val selectedComponent = presenter.getView().selectedComponent

        e.presentation.isEnabled = selectedComponent != null && presenter.canCloseCurrentTab()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
