package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle

/**
 * JSON의 이스케이프를 해제하는 액션 클래스입니다.
 */
class UnescapeJsonAction(private val icon: javax.swing.Icon) : AnAction(
    LocalizationBundle.message("unescape"),
    LocalizationBundle.message("unescapeDescription"),
    icon
) {
    override fun actionPerformed(e: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        panel.unescapeJson()
    }
}
