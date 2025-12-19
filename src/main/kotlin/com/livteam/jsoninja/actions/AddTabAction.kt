package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle

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
        panel.presenter.addNewTab()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(e) != null
    }
}
