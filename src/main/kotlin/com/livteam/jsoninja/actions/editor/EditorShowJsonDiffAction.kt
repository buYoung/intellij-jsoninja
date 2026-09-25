package com.livteam.jsoninja.actions.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.actions.ShowJsonDiffAction
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 편집기의 선택 영역(선택이 없으면 문서 전체)을 JSON Diff 왼쪽에 엽니다.
 * 비교만 수행하며 원본 편집기와 tool window 내용은 변경하지 않습니다.
 */
class EditorShowJsonDiffAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val document = editor.document
        val selectionModel = editor.selectionModel

        val hasSelection = selectionModel.hasSelection()
        val inputText = if (hasSelection) {
            selectionModel.selectedText ?: return
        } else {
            document.text
        }
        val invalidJsonMessageKey = if (hasSelection) {
            "editor.action.error.invalid.json"
        } else {
            "editor.action.error.invalid.json.document"
        }

        if (inputText.isBlank()) {
            showErrorHint(editor, LocalizationBundle.message(invalidJsonMessageKey))
            return
        }

        val documentModificationStamp = document.modificationStamp
        val selectionStart = selectionModel.selectionStart
        val selectionEnd = selectionModel.selectionEnd

        project.service<JsoninjaCoroutineScopeService>().launch {
            val isValidJson = withContext(Dispatchers.Default) {
                project.service<JsonFormatterService>().isValidJson(inputText)
            }

            withContext(Dispatchers.EDT) {
                if (project.isDisposed || editor.isDisposed) return@withContext
                if (document.modificationStamp != documentModificationStamp) return@withContext
                if (hasSelection != selectionModel.hasSelection() ||
                    (hasSelection && (selectionStart != selectionModel.selectionStart ||
                        selectionEnd != selectionModel.selectionEnd))
                ) return@withContext

                if (!isValidJson) {
                    showErrorHint(editor, LocalizationBundle.message(invalidJsonMessageKey))
                    return@withContext
                }

                project.service<JsonDiffService>().openDiff(
                    displayMode = ShowJsonDiffAction.getDefaultDisplayMode(project),
                    currentJson = inputText,
                    defaultSortKeys = JsoninjaSettingsState.getInstance(project).diffSortKeys
                )
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = e.project != null && editor != null
        if (editor == null) return

        val textKey = if (editor.selectionModel.hasSelection()) {
            "editor.action.show.json.diff.selection"
        } else {
            "editor.action.show.json.diff.document"
        }
        e.presentation.text = LocalizationBundle.message(textKey)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private fun showErrorHint(editor: Editor, message: String) {
        HintManager.getInstance().showErrorHint(editor, "JSONinja: $message")
    }
}
