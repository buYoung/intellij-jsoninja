package com.livteam.jsoninja.actions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.PsiDocumentManager
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.utils.JsonPathHelper
import java.awt.datatransfer.StringSelection

class CopyJsonQueryAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        val offset = editor.caretModel.offset

        val settings = JsoninjaSettingsState.getInstance(project)
        val type = JsonQueryType.fromString(settings.jsonQueryType)

        val path = JsonPathHelper.getPathFromTemplateText(
            documentText = editor.document.text,
            offset = offset,
            project = project,
            queryType = type
        )?.path ?: run {
            val element = psiFile.findElementAt(offset)
            if (element != null) JsonPathHelper.getPath(element, type) else null
        }

        if (path != null) {
            CopyPasteManager.getInstance().setContents(StringSelection(path))
            HintManager.getInstance().showInformationHint(
                editor,
                LocalizationBundle.message("action.copy.json.query.copied", path),
            )
        } else {
            HintManager.getInstance().showErrorHint(
                editor,
                "JSONinja: ${LocalizationBundle.message("action.copy.json.query.unavailable")}",
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project

        // Check if we have a valid element at cursor
        var hasElement = false
        if (project != null && editor != null) {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            val offset = e.getData(CommonDataKeys.CARET)?.offset ?: editor.caretModel.offset
            if (psiFile != null) {
                val element = psiFile.findElementAt(offset)
                hasElement = element != null
            }

            val queryType = JsonQueryType.fromString(JsoninjaSettingsState.getInstance(project).jsonQueryType)
            val engineLabel = when (queryType) {
                JsonQueryType.JAYWAY_JSONPATH -> LocalizationBundle.message("query.engine.jsonpath")
                JsonQueryType.JMESPATH -> LocalizationBundle.message("query.engine.jmespath")
                JsonQueryType.JACKSON_JQ -> LocalizationBundle.message("query.engine.jq")
            }
            e.presentation.text = LocalizationBundle.message("action.copy.json.query.by.type", engineLabel)
        }

        // Always visible if editor exists (since it's manually added to the context menu)
        // Enabled only when there is a valid element to query
        e.presentation.isVisible = project != null && editor != null
        e.presentation.isEnabled = project != null && editor != null && hasElement
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
