package com.livteam.jsoninja.actions

import com.intellij.diff.util.Side
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService

/**
 * 현재 diff 세션의 왼쪽/오른쪽 이름을 변경합니다. JSON 내용과 전체 diff 제목은 바꾸지 않습니다.
 */
class RenameJsonDiffTitlesAction : DumbAwareAction(
    LocalizationBundle.messagePointer("action.diff.rename.titles"),
    LocalizationBundle.messagePointer("action.diff.rename.titles.description"),
    AllIcons.Actions.Edit
) {
    override fun actionPerformed(e: AnActionEvent) {
        val target = JsonDiffActionUtils.getTarget(e) ?: return
        target.project.service<JsonDiffService>().openRenameTitlesDialog(
            session = target.session,
            initialFocusedSide = Side.LEFT,
            parentComponent = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = JsonDiffActionUtils.getTarget(e) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
