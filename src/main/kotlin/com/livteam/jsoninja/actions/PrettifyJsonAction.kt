package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonFormatState

/**
 * JSON을 이쁘게 포맷팅하는 액션 클래스입니다.
 */
class PrettifyJsonAction(private val icon: javax.swing.Icon) : AnAction(
    LocalizationBundle.message("prettify"),
    LocalizationBundle.message("prettifyDescription"),
    icon
) {
    override fun actionPerformed(e: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        panel.formatJson(JsonFormatState.PRETTIFY)
    }
}
