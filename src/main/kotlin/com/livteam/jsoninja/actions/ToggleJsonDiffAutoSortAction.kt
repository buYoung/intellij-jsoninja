package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService

/**
 * 현재 diff 세션에서만 자동 정렬을 켜고 끕니다. 설정의 기본값은 변경하지 않습니다.
 */
class ToggleJsonDiffAutoSortAction : ToggleAction(
    LocalizationBundle.message("action.diff.sort.auto"),
    LocalizationBundle.message("action.diff.sort.auto.description"),
    AllIcons.ObjectBrowser.Sorted
) {
    override fun isSelected(e: AnActionEvent): Boolean {
        return JsonDiffActionUtils.getTarget(e)?.session?.shouldAutoSort == true
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val target = JsonDiffActionUtils.getTarget(e) ?: return
        target.project.service<JsonDiffService>().changeAutoSort(
            session = target.session,
            isEnabled = state,
            isHostActive = target::isHostActive,
            onUnavailable = target::showSortUnavailableHint
        )
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = JsonDiffActionUtils.getTarget(e) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
