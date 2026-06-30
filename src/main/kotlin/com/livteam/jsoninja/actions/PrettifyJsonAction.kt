package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons

/**
 * JSON을 이쁘게 포맷팅하는 액션 클래스입니다.
 */
class PrettifyJsonAction : AnAction(
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
}
