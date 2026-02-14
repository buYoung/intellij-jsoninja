package com.livteam.jsoninja.actions

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.diff.JsonDiffRequestChain
import com.livteam.jsoninja.ui.diff.JsonDiffVirtualFile
import com.livteam.jsoninja.utils.JsonHelperUtils

/**
 * JSON 차이를 비교하는 액션 클래스
 * Editor Tab 또는 Window 방식으로 diff를 표시합니다.
 */
class ShowJsonDiffAction : AnAction(
    LocalizationBundle.message("action.show.json.diff"),
    LocalizationBundle.message("action.show.json.diff.description"),
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        openDiffForCurrentJson(project)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        e.presentation.icon = JsoninjaIcons.getDiffIcon(project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    companion object {
        fun openDiffForCurrentJson(project: Project, forceWindow: Boolean = false) {
            val jsonDiffService = project.service<JsonDiffService>()
            val settings = JsoninjaSettingsState.getInstance(project)
            val currentJson = JsonHelperUtils.getCurrentJsonFromToolWindow(project)
            val leftJson = currentJson ?: "{}"
            val rightJson = "{}"

            val displayMode = if (forceWindow) {
                JsonDiffDisplayMode.WINDOW
            } else {
                try {
                    JsonDiffDisplayMode.valueOf(settings.diffDisplayMode)
                } catch (_: IllegalArgumentException) {
                    JsonDiffDisplayMode.WINDOW
                }
            }

            val diffSortKeys = settings.diffSortKeys

            when (displayMode) {
                JsonDiffDisplayMode.EDITOR_TAB -> showAsEditorTab(project, jsonDiffService, leftJson, rightJson, diffSortKeys)
                JsonDiffDisplayMode.WINDOW -> showAsWindow(project, jsonDiffService, leftJson, rightJson, diffSortKeys)
            }
        }

        private fun showAsEditorTab(
            project: Project,
            diffService: JsonDiffService,
            leftJson: String,
            rightJson: String,
            sortKeys: Boolean
        ) {
            val diffFile = JsonDiffVirtualFile(project, diffService, leftJson, rightJson, sortKeys)
            DiffEditorTabFilesManager.getInstance(project).showDiffFile(diffFile, true)
        }

        private fun showAsWindow(
            project: Project,
            diffService: JsonDiffService,
            leftJson: String,
            rightJson: String,
            sortKeys: Boolean
        ) {
            val diffChain = JsonDiffRequestChain(project, diffService, leftJson, rightJson, sortKeys)
            DiffManager.getInstance().showDiff(project, diffChain, DiffDialogHints.FRAME)
        }
    }
}
