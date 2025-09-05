package com.livteam.jsoninja.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.json.JsonFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService

/**
 * Extension for JSON diff viewer that provides automatic JSON formatting
 */
class JsonDiffExtension : DiffExtension() {
    companion object {
        private const val DEBOUNCE_DELAY = 300 // milliseconds

        private lateinit var formatterService: JsonFormatterService
    }

    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        // Only apply to EditorDiffViewer (text diffs)
        if (viewer !is EditorDiffViewer) return

        // Check if this is JSON content
        val editors = viewer.editors
        if (editors.size != 2) return // We expect exactly 2 editors for two-side diff

        // Get project
        val project = context.project ?: return
        formatterService = project.service<JsonFormatterService>()

        val isJsonDiff = editors.all { editor ->
            editor.document.text.trim().let {
                val isJsonString = formatterService.isValidJson(it)

                isJsonString || editor.virtualFile?.fileType == JsonFileType.INSTANCE
            }
        }
        if (!isJsonDiff) return

        // Install listeners for both editors
        editors.forEachIndexed { index, editor ->
            installAutoFormatter(project, editor, viewer)
        }
    }

    private fun installAutoFormatter(project: Project, editor: Editor, viewer: FrameDiffTool.DiffViewer) {
        val document = editor.document
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, viewer)

        val documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                // Cancel any pending formatting
                alarm.cancelAllRequests()

                // Schedule new formatting with debounce
                alarm.addRequest({
                    ApplicationManager.getApplication().invokeLater({
                        formatJsonDocument(project, document, formatterService)
                    }, ModalityState.defaultModalityState())
                }, DEBOUNCE_DELAY)
            }
        }

        document.addDocumentListener(documentListener)

        // Remove listener when viewer is disposed
        Disposer.register(viewer) {
            document.removeDocumentListener(documentListener)
            alarm.cancelAllRequests()
        }

        // Format initially if there's content
        if (document.text.isNotBlank()) {
            formatJsonDocument(project, document, formatterService)
        }
    }

    private fun formatJsonDocument(project: Project, document: Document, formatterService: JsonFormatterService) {
        val text = document.text.trim()
        if (text.isEmpty()) return

        try {
            // Try to format the JSON
            val formatted = formatterService.formatJson(text, JsonFormatState.PRETTIFY)

            // Only update if different to avoid infinite loops
            if (formatted != text && formatted.trim() != text) {
                ApplicationManager.getApplication().runWriteAction {
                    document.setText(formatted)
                }
            }
        } catch (e: Exception) {
            // If formatting fails, keep the original text
            // The IDE will show JSON syntax errors
        }
    }
}