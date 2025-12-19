package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.model.JsonFormatState

/**
 * JSON을 압축하는 액션 클래스입니다.
 */
class UglifyJsonAction : AnAction(
    LocalizationBundle.message("uglify"),
    LocalizationBundle.message("uglifyDescription"),
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        // UGLIFY는 설정 UI에서 선택할 수 없으므로 기본 포맷 상태는 변경하지 않는다.
        panel.presenter.formatJson(JsonFormatState.UGLIFY)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(e) != null
        e.presentation.icon = JsoninjaIcons.getUglifyIcon(e.project)
    }
}
