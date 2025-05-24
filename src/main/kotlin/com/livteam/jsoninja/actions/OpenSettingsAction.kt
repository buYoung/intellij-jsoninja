package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.settings.JsoninjaSettingsConfigurable

/**
 * JSONinja 설정 페이지를 여는 액션 클래스입니다.
 */
class OpenSettingsAction : AnAction(
    LocalizationBundle.message("settings.action.text"),
    LocalizationBundle.message("settings.action.description"),
    AllIcons.General.Settings
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, JsoninjaSettingsConfigurable::class.java)
    }
}