package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService

/**
 * 마지막 수동 정렬 직전의 양쪽 텍스트를 복원합니다. 정렬 이후 편집이 있으면 비활성화됩니다.
 */
class RestoreJsonDiffSortAction : AnAction(
    LocalizationBundle.message("action.diff.sort.restore"),
    LocalizationBundle.message("action.diff.sort.restore.description"),
    AllIcons.Actions.Rollback
) {
    override fun actionPerformed(e: AnActionEvent) {
        val target = JsonDiffActionUtils.getTarget(e) ?: return
        target.project.service<JsonDiffService>().restoreSort(target.session, target::isHostActive)
    }

    override fun update(e: AnActionEvent) {
        val target = JsonDiffActionUtils.getTarget(e)
        e.presentation.isVisible = target != null
        e.presentation.isEnabled = target != null &&
            target.project.service<JsonDiffService>().canRestoreSort(target.session)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}
