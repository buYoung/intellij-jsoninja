package com.livteam.jsoninja.actions

import com.intellij.diff.util.Side
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.diff.JsonDiffTitlesDialog

/**
 * 현재 diff 세션의 왼쪽/오른쪽 이름을 변경합니다. JSON 내용과 전체 diff 제목은 바꾸지 않습니다.
 */
class RenameJsonDiffTitlesAction : AnAction(
    LocalizationBundle.message("action.diff.rename.titles"),
    LocalizationBundle.message("action.diff.rename.titles.description"),
    AllIcons.Actions.Edit
) {
    override fun actionPerformed(e: AnActionEvent) {
        val target = JsonDiffActionUtils.getTarget(e) ?: return
        val session = target.session

        val dialog = JsonDiffTitlesDialog(
            project = target.project,
            parentComponent = e.getData(PlatformDataKeys.CONTEXT_COMPONENT),
            initialLeftTitle = session.getCustomTitle(Side.LEFT),
            initialRightTitle = session.getCustomTitle(Side.RIGHT),
            defaultLeftTitle = session.getDefaultTitle(Side.LEFT),
            defaultRightTitle = session.getDefaultTitle(Side.RIGHT)
        )
        dialog.show()
        if (!dialog.isOK || session.isClosed) return

        session.changeTitles(dialog.leftTitle, dialog.rightTitle)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = JsonDiffActionUtils.getTarget(e) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
