package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.settings.JsoninjaSettingsConfigurable

/**
 * JSONinja 설정 페이지를 여는 액션 클래스입니다.
 */
class OpenSettingsAction : DumbAwareAction(
    LocalizationBundle.messagePointer("settings.action.text"),
    LocalizationBundle.messagePointer("settings.action.description"),
    AllIcons.General.Settings
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, JsoninjaSettingsConfigurable::class.java)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}