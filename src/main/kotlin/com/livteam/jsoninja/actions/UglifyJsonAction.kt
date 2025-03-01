package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.ui.component.JsonHelperPanel

/**
 * JSON을 압축하는 액션 클래스입니다.
 */
class UglifyJsonAction(private val icon: javax.swing.Icon) : AnAction(
    LocalizationBundle.message("uglify"),
    LocalizationBundle.message("uglifyDescription"),
    icon
) {
    override fun actionPerformed(e: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        panel.formatJson(JsonFormatState.UGLIFY)
    }
}
