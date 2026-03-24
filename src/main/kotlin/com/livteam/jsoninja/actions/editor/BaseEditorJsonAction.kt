package com.livteam.jsoninja.actions.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonFormatterService

abstract class BaseEditorJsonAction : AnAction() {

    protected open val requiresJsonValidation: Boolean = true
    protected open val unescapeBeforeTransform: Boolean = false

    abstract fun transformJson(service: JsonFormatterService, input: String): String

    abstract fun getCommandName(): String

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val document = editor.document
        val service = project.getService(JsonFormatterService::class.java)

        val selectionModel = editor.selectionModel
        val hasSelection = selectionModel.hasSelection()
        val inputText = if (hasSelection) {
            selectionModel.selectedText ?: return
        } else {
            document.text
        }

        if (inputText.isBlank()) return

        val textToProcess = if (unescapeBeforeTransform && service.containsEscapeCharacters(inputText)) {
            service.fullyUnescapeJson(inputText)
        } else {
            inputText
        }

        if (requiresJsonValidation && !service.isValidJson(textToProcess)) {
            showErrorHint(editor, LocalizationBundle.message("editor.action.error.invalid.json"))
            return
        }

        val result = try {
            transformJson(service, textToProcess)
        } catch (ex: Exception) {
            showErrorHint(editor, LocalizationBundle.message("editor.action.error.invalid.json"))
            return
        }

        if (result == inputText) return

        WriteCommandAction.runWriteCommandAction(project, getCommandName(), null, {
            if (hasSelection) {
                document.replaceString(
                    selectionModel.selectionStart,
                    selectionModel.selectionEnd,
                    result
                )
            } else {
                document.setText(result)
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project
        e.presentation.isEnabledAndVisible = editor != null && project != null
    }

    private fun showErrorHint(editor: Editor, message: String) {
        HintManager.getInstance().showErrorHint(editor, "JSONinja: $message")
    }
}
