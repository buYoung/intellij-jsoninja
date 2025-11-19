package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiDocumentManager
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.component.JsonEditor
import com.livteam.jsoninja.util.JsonPathHelper
import java.awt.datatransfer.StringSelection

class CopyJsonQueryAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return

        val settings = JsoninjaSettingsState.getInstance(project)
        val type = JsonQueryType.fromString(settings.jsonQueryType)

        val path = when (type) {
            JsonQueryType.JMESPATH -> JsonPathHelper.getJmesPath(element)
            JsonQueryType.JAYWAY_JSONPATH -> JsonPathHelper.getJsonPath(element)
        }

        if (path != null) {
            CopyPasteManager.getInstance().setContents(StringSelection(path))
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project

        // Check if we have a valid element at cursor
        var hasElement = false
        if (project != null && editor != null) {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            val offset = editor.caretModel.offset
            if (psiFile != null) {
                val element = psiFile.findElementAt(offset)
                hasElement = element != null
            }
        }

        // Always visible if editor exists (since it's manually added to the context menu)
        // Enabled only when there is a valid element to query
        e.presentation.isVisible = project != null && editor != null
        e.presentation.isEnabled = project != null && editor != null && hasElement
    }
}
