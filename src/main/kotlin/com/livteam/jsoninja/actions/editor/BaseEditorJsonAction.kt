package com.livteam.jsoninja.actions.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import java.util.Collections
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        val documentModificationStamp = document.modificationStamp
        val selectionStart = selectionModel.selectionStart
        val selectionEnd = selectionModel.selectionEnd

        lateinit var processingJob: Job
        processingJob = project.service<JsoninjaCoroutineScopeService>().launch(start = CoroutineStart.LAZY) {
            try {
                val result = withContext(Dispatchers.Default) {
                    if (requiresJsonValidation && !service.isValidJson(textToProcess)) {
                        return@withContext EditorActionResult.InvalidJson
                    }

                    try {
                        EditorActionResult.Success(transformJson(service, textToProcess))
                    } catch (_: Exception) {
                        EditorActionResult.InvalidJson
                    }
                }

                withContext(Dispatchers.EDT) {
                    if (project.isDisposed) return@withContext
                    if (synchronized(documentProcessingJobs) { documentProcessingJobs[document] } !== processingJob) {
                        return@withContext
                    }

                    when (result) {
                        EditorActionResult.InvalidJson -> {
                            showErrorHint(editor, LocalizationBundle.message("editor.action.error.invalid.json"))
                        }

                        is EditorActionResult.Success -> {
                            if (result.text == inputText) return@withContext
                            if (document.modificationStamp != documentModificationStamp) return@withContext

                            WriteCommandAction.runWriteCommandAction(project, getCommandName(), null, {
                                if (hasSelection) {
                                    document.replaceString(selectionStart, selectionEnd, result.text)
                                } else {
                                    document.setText(result.text)
                                }
                            })
                        }
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            }
        }
        synchronized(documentProcessingJobs) {
            documentProcessingJobs.remove(document)?.cancel()
            documentProcessingJobs[document] = processingJob
        }
        processingJob.start()
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project
        e.presentation.isEnabledAndVisible = editor != null && project != null
    }

    private fun showErrorHint(editor: Editor, message: String) {
        HintManager.getInstance().showErrorHint(editor, "JSONinja: $message")
    }

    private sealed interface EditorActionResult {
        data object InvalidJson : EditorActionResult
        data class Success(val text: String) : EditorActionResult
    }

    private companion object {
        val documentProcessingJobs: MutableMap<com.intellij.openapi.editor.Document, Job> =
            Collections.synchronizedMap(WeakHashMap())
    }
}
