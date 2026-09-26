package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService

/**
 * 현재 diff 세션에서 내용이 같은 배열 요소를 왼쪽 순서로 맞출지 선택합니다. 기본값은 꺼짐입니다.
 */
class ToggleJsonDiffArrayOrderAction : ToggleAction(
    LocalizationBundle.message("action.diff.ignore.array.order"),
    LocalizationBundle.message("action.diff.ignore.array.order.description"),
    AllIcons.Json.Array
) {
    override fun isSelected(e: AnActionEvent): Boolean {
        return JsonDiffActionUtils.getTarget(e)?.session?.shouldIgnoreArrayOrder == true
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val target = JsonDiffActionUtils.getTarget(e) ?: return
        target.project.service<JsonDiffService>().changeIgnoreArrayOrder(
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
