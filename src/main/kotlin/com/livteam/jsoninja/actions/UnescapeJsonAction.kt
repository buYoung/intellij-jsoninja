package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons

/**
 * JSON의 이스케이프를 해제하는 액션 클래스입니다.
 */
class UnescapeJsonAction : AnAction(
    LocalizationBundle.message("unescape"),
    LocalizationBundle.message("unescapeDescription"),
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        panel.presenter.unescapeJson()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(e) != null
        e.presentation.icon = JsoninjaIcons.getUnescapeIcon(e.project)
    }
}
