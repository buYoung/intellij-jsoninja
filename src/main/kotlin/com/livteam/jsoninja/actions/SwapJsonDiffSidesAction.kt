package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService

/**
 * 현재 diff 세션의 왼쪽과 오른쪽 JSON 내용 및 제목을 맞바꿉니다.
 */
class SwapJsonDiffSidesAction : DumbAwareAction(
    LocalizationBundle.messagePointer("action.diff.swap.sides"),
    LocalizationBundle.messagePointer("action.diff.swap.sides.description"),
    AllIcons.Actions.SwapPanels
) {
    override fun actionPerformed(e: AnActionEvent) {
        val target = JsonDiffActionUtils.getTarget(e) ?: return
        target.project.service<JsonDiffService>().swapSides(target.session, target::isHostActive)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = JsonDiffActionUtils.getTarget(e) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
