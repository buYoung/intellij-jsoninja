package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.JsonHelperPanel

/**
 * 새 탭을 추가하는 액션 클래스입니다.
 */
class AddTabAction : AnAction(
    LocalizationBundle.message("addTab"),
    LocalizationBundle.message("addTabDescription"),
    AllIcons.General.Add
) {
    override fun actionPerformed(e: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        panel.addNewTab()
    }
}
