package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService

/**
 * 현재 diff 세션에서 선택된 정렬 기준을 양쪽 문서에 한 번 적용합니다.
 */
class SortJsonDiffKeysOnceAction : AnAction(
    LocalizationBundle.message("action.diff.sort.keys.once"),
    LocalizationBundle.message("action.diff.sort.keys.once.description"),
    AllIcons.ObjectBrowser.SortByType
) {
    override fun actionPerformed(e: AnActionEvent) {
        val target = JsonDiffActionUtils.getTarget(e) ?: return
        target.project.service<JsonDiffService>().applySortOnce(
            session = target.session,
            isHostActive = target::isHostActive,
            onUnavailable = target::showSortUnavailableHint
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = JsonDiffActionUtils.getTarget(e) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
