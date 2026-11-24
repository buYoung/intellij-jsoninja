package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.services.JsonDiffService

/**
 * 현재 diff 세션을 유지한 채 Editor Tab과 별도 Window 표시 모드를 전환합니다.
 */
class SwitchJsonDiffDisplayModeAction : DumbAwareAction(
    LocalizationBundle.messagePointer("action.switch.diff.display.mode"),
    LocalizationBundle.messagePointer("action.switch.diff.display.mode.description"),
    AllIcons.Actions.MoveToWindow
) {
    override fun actionPerformed(e: AnActionEvent) {
        val target = JsonDiffActionUtils.getTarget(e) ?: return
        target.project.service<JsonDiffService>().switchDisplayMode(target.session)
    }

    override fun update(e: AnActionEvent) {
        val target = JsonDiffActionUtils.getTarget(e)
        if (target == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val diffService = target.project.service<JsonDiffService>()
        val currentMode = diffService.getDisplayMode(target.session)
        e.presentation.isVisible = currentMode != null
        e.presentation.isEnabled = currentMode != null && diffService.canSwitchDisplayMode(target.session)

        when (currentMode) {
            JsonDiffDisplayMode.WINDOW -> {
                e.presentation.text = LocalizationBundle.message("action.switch.to.editor.tab")
                e.presentation.description = LocalizationBundle.message("action.switch.to.editor.tab.description")
                e.presentation.icon = AllIcons.Actions.SplitVertically
            }

            JsonDiffDisplayMode.EDITOR_TAB, null -> {
                e.presentation.text = LocalizationBundle.message("action.switch.to.window")
                e.presentation.description = LocalizationBundle.message("action.switch.to.window.description")
                e.presentation.icon = AllIcons.Actions.MoveToWindow
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}
