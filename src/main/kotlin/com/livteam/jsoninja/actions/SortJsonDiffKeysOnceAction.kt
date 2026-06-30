package com.livteam.jsoninja.actions

import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.diff.JsonDiffKeys
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SortJsonDiffKeysOnceAction : AnAction(
    LocalizationBundle.message("action.diff.sort.keys.once"),
    LocalizationBundle.message("action.diff.sort.keys.once.description"),
    AllIcons.ObjectBrowser.SortByType
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val viewer = e.getData(DiffDataKeys.DIFF_VIEWER) as? EditorDiffViewer ?: return
        val editors = viewer.editors
        if (editors.size != 2) return

        val formatterService = project.service<JsonFormatterService>()
        val leftDocument = editors[0].document
        val rightDocument = editors[1].document
        val leftOriginalText = leftDocument.text
        val rightOriginalText = rightDocument.text
        val leftModificationStamp = leftDocument.modificationStamp
        val rightModificationStamp = rightDocument.modificationStamp

        project.service<JsoninjaCoroutineScopeService>().launch {
            try {
                val formattedDocuments = withContext(Dispatchers.Default) {
                    formatterService.formatJson(leftOriginalText, JsonFormatState.PRETTIFY, true) to
                        formatterService.formatJson(rightOriginalText, JsonFormatState.PRETTIFY, true)
                }

                withContext(Dispatchers.EDT) {
                    if (project.isDisposed) return@withContext
                    if (
                        leftDocument.modificationStamp != leftModificationStamp ||
                        rightDocument.modificationStamp != rightModificationStamp
                    ) {
                        return@withContext
                    }

                    val (leftFormatted, rightFormatted) = formattedDocuments
                    if (leftFormatted == leftDocument.text && rightFormatted == rightDocument.text) {
                        return@withContext
                    }

                    WriteCommandAction.runWriteCommandAction(project) {
                        applyFormatted(leftDocument, leftFormatted)
                        applyFormatted(rightDocument, rightFormatted)
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            }
        }
    }

    private fun applyFormatted(document: Document, formatted: String) {
        if (formatted == document.text) return

        document.putUserData(JsonDiffKeys.JSON_DIFF_CHANGE_GUARD, true)
        try {
            document.setText(formatted)
        } finally {
            document.putUserData(JsonDiffKeys.JSON_DIFF_CHANGE_GUARD, false)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val viewer = e.getData(DiffDataKeys.DIFF_VIEWER)
        e.presentation.isEnabledAndVisible = project != null && viewer is EditorDiffViewer
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
