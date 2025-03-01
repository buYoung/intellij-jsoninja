package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.JsonHelperPanel

/**
 * JSON을 이스케이프 처리하는 액션 클래스입니다.
 */
class EscapeJsonAction(private val icon: javax.swing.Icon) : AnAction(
    LocalizationBundle.message("escape"),
    LocalizationBundle.message("escapeDescription"),
    icon
) {
    override fun actionPerformed(e: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        panel.escapeJson()
    }
}
