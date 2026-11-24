package com.livteam.jsoninja.actions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.utils.JsonPathHelper
import java.awt.datatransfer.StringSelection

class CopyJsonArrayQueryAction : DumbAwareAction() {
    companion object {
        private const val PLACEHOLDER_OPEN = "{{"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val offset = editor.caretModel.offset
        val queryType = JsonQueryType.fromString(JsoninjaSettingsState.getInstance(project).jsonQueryType)
        val path = resolveWildcardPath(project, editor, offset, queryType) ?: return

        CopyPasteManager.getInstance().setContents(StringSelection(path))
        HintManager.getInstance().showInformationHint(
            editor,
            LocalizationBundle.message("action.copy.json.query.copied", path),
        )
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project
        if (project == null || editor == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val offset = e.getData(CommonDataKeys.CARET)?.offset ?: editor.caretModel.offset
        val queryType = JsonQueryType.fromString(JsoninjaSettingsState.getInstance(project).jsonQueryType)
        val wildcardPath = resolveWildcardPath(project, editor, offset, queryType)
        if (wildcardPath == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val engineLabel = when (queryType) {
            JsonQueryType.JAYWAY_JSONPATH -> LocalizationBundle.message("query.engine.jsonpath")
            JsonQueryType.JMESPATH -> LocalizationBundle.message("query.engine.jmespath")
            JsonQueryType.JACKSON_JQ -> LocalizationBundle.message("query.engine.jq")
        }
        e.presentation.text = LocalizationBundle.message(
            "action.copy.json.array.query.by.type",
            engineLabel,
        )
        e.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun resolveWildcardPath(
        project: Project,
        editor: Editor,
        offset: Int,
        queryType: JsonQueryType,
    ): String? {
        val document = editor.document
        if (document.immutableCharSequence.indexOf(PLACEHOLDER_OPEN) >= 0) {
            val templatePath = JsonPathHelper.getArrayWildcardPathFromTemplateText(
                documentText = document.text,
                offset = offset,
                project = project,
                queryType = queryType,
            )?.path
            if (templatePath != null) {
                return templatePath
            }
        }

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
        val element = psiFile.findElementAt(offset) ?: return null
        return JsonPathHelper.getArrayWildcardPath(element, queryType)
    }
}
