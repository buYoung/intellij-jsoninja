package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons

/**
 * JSON을 이쁘게 포맷팅하는 액션 클래스입니다.
 */
class PrettifyJsonAction : DumbAwareAction(
    LocalizationBundle.messagePointer("prettify"),
    LocalizationBundle.messagePointer("prettifyDescription"),
    null as javax.swing.Icon?
) {
    override fun actionPerformed(e: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        panel.presenter.formatJson()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(e) != null
        e.presentation.icon = JsoninjaIcons.getPrettyIcon(e.project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
