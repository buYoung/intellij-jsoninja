package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.components.service
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonDiffSortMode
import com.livteam.jsoninja.services.JsonDiffService
import javax.swing.JComponent

/**
 * diff toolbar에서 현재 정렬 기준을 표시하고 바꾸는 선택 상자
 */
class SelectJsonDiffSortModeAction : ComboBoxAction() {

    init {
        templatePresentation.text = LocalizationBundle.message("action.diff.sort.mode")
        templatePresentation.description = LocalizationBundle.message("action.diff.sort.mode.description")
    }

    override fun update(e: AnActionEvent) {
        val target = JsonDiffActionUtils.getTarget(e)
        e.presentation.isEnabledAndVisible = target != null
        if (target == null) return

        e.presentation.text = LocalizationBundle.message(
            "action.diff.sort.mode.selected",
            getSortModeText(target.session.sortMode)
        )
    }

    public override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        return DefaultActionGroup(JsonDiffSortMode.entries.map { SortModeOptionAction(it) })
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    private class SortModeOptionAction(
        private val sortMode: JsonDiffSortMode
    ) : AnAction(getSortModeText(sortMode), getSortModeDescription(sortMode), null), Toggleable {

        override fun actionPerformed(e: AnActionEvent) {
            val target = JsonDiffActionUtils.getTarget(e) ?: return
            target.project.service<JsonDiffService>().changeSortMode(
                session = target.session,
                sortMode = sortMode,
                isHostActive = target::isHostActive,
                onUnavailable = target::showSortUnavailableHint
            )
        }

        override fun update(e: AnActionEvent) {
            val target = JsonDiffActionUtils.getTarget(e)
            e.presentation.isEnabledAndVisible = target != null
            Toggleable.setSelected(e.presentation, target?.session?.sortMode == sortMode)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }
    }

    private companion object {
        fun getSortModeText(sortMode: JsonDiffSortMode): String {
            return when (sortMode) {
                JsonDiffSortMode.KEY_ASCENDING -> LocalizationBundle.message("action.diff.sort.mode.key.ascending")
                JsonDiffSortMode.LEFT_ORDER -> LocalizationBundle.message("action.diff.sort.mode.left.order")
            }
        }

        fun getSortModeDescription(sortMode: JsonDiffSortMode): String {
            return when (sortMode) {
                JsonDiffSortMode.KEY_ASCENDING ->
                    LocalizationBundle.message("action.diff.sort.mode.key.ascending.description")

                JsonDiffSortMode.LEFT_ORDER ->
                    LocalizationBundle.message("action.diff.sort.mode.left.order.description")
            }
        }
    }
}
