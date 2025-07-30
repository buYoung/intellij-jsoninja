package com.livteam.jsoninja.actions

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.icons.AllIcons
import com.livteam.jsoninja.LocalizationBundle
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
class ShowJsonDiffAction : AnAction {
    // 파라미터 없는 생성자 (plugin.xml에서 사용)
    constructor() : super(
        LocalizationBundle.message("action.show.json.diff"),
        LocalizationBundle.message("action.show.json.diff.description"),
        AllIcons.Actions.Diff
    )

    // 아이콘을 받는 생성자 (프로그래밍 방식으로 사용)
    constructor(icon: javax.swing.Icon) : super(
        LocalizationBundle.message("action.show.json.diff"),
        LocalizationBundle.message("action.show.json.diff.description"),
        icon
    )

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val jsonDiffService = project.service<JsonDiffService>()
        val settings = JsoninjaSettingsState.getInstance(project)

        // Get current JSON from active tab using utility
        val currentJson = JsonHelperUtils.getCurrentJsonFromToolWindow(project)
        
        // Create JSONs with default templates if no content
        val leftJson = currentJson ?: "{}"
        val rightJson = "{}"

        // Get display mode from settings
        val displayMode = try {
            JsonDiffDisplayMode.valueOf(settings.diffDisplayMode)
        } catch (e: IllegalArgumentException) {
            JsonDiffDisplayMode.WINDOW
        }

        when (displayMode) {
            JsonDiffDisplayMode.EDITOR_TAB -> showAsEditorTab(project, jsonDiffService, leftJson, rightJson)
            JsonDiffDisplayMode.WINDOW -> showAsWindow(project, jsonDiffService, leftJson, rightJson)
        }
    }

    private fun showAsEditorTab(project: Project, diffService: JsonDiffService, leftJson: String, rightJson: String) {
        val diffFile = JsonDiffVirtualFile(project, diffService, leftJson, rightJson)
        DiffEditorTabFilesManager.getInstance(project).showDiffFile(diffFile, true)
    }

    private fun showAsWindow(project: Project, diffService: JsonDiffService, leftJson: String, rightJson: String) {
        val diffChain = JsonDiffRequestChain(project, diffService, leftJson, rightJson)
        DiffManager.getInstance().showDiff(project, diffChain, DiffDialogHints.FRAME)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}