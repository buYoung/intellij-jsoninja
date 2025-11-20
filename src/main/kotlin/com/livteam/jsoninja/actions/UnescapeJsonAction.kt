package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle

/**
 * JSON의 이스케이프를 해제하는 액션 클래스입니다.
 */
class UnescapeJsonAction : AnAction(
    LocalizationBundle.message("unescape"),
    LocalizationBundle.message("unescapeDescription"),
    JsonHelperActionUtils.getIcon("/icons/unescape.svg")
) {
    override fun actionPerformed(e: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        panel.unescapeJson()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(e) != null
    }
}
