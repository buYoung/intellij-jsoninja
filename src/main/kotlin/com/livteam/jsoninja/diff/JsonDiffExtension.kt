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
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService

/**
 * Extension for JSON diff viewer that provides automatic JSON formatting
 */
class JsonDiffExtension : DiffExtension() {
    companion object {
        private const val DEBOUNCE_DELAY = 300 // milliseconds
        private val CHANGE_GUARD_KEY: Key<Boolean> = Key.create("JSONINJA_DIFF_CHANGE_GUARD")
    }

    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        // Only apply to EditorDiffViewer (text diffs)
        if (viewer !is EditorDiffViewer) return

        val editors = viewer.editors
        if (editors.size != 2) return // We expect exactly 2 editors for two-side diff

        // Get project and services
        val project = context.project ?: return
        val formatterService = project.service<JsonFormatterService>()

        // Determine if both sides are JSON; final validation goes through JsonFormatterService.isValidJson
        val isJsonDiff = editors.all { editor -> isJsonContent(editor, formatterService) }
        if (!isJsonDiff) return

        // Install listeners for both editors
        editors.forEach { editor ->
            installAutoFormatter(project, editor, viewer, formatterService)
        }
    }

    private fun isJsonContent(editor: Editor, formatterService: JsonFormatterService): Boolean {
        // Fast path: file type check
        if (editor.virtualFile?.fileType == JsonFileType.INSTANCE) return true

        val text = editor.document.text
        if (text.isBlank()) return false

        val trimmed = text.trim()
        if (!(trimmed.startsWith('{') || trimmed.startsWith('['))) return false

        // Mandatory validation via JsonFormatterService
        return formatterService.isValidJson(trimmed)
    }

    private fun installAutoFormatter(
        project: Project,
        editor: Editor,
        viewer: FrameDiffTool.DiffViewer,
        formatterService: JsonFormatterService
    ) {
        val document = editor.document
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, viewer)

        val documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                // Skip if this change originated from our own formatter
                if (document.getUserData(CHANGE_GUARD_KEY) == true) return

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
        // Avoid re-entrant updates
        if (document.getUserData(CHANGE_GUARD_KEY) == true) return

        val text = document.text
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        try {
            // Try to format the JSON (JsonFormatterService internally validates via isValidJson)
            val formatted = formatterService.formatJson(trimmed, JsonFormatState.PRETTIFY)

            // Only update if different to avoid infinite loops
            if (formatted != trimmed) {
                document.putUserData(CHANGE_GUARD_KEY, true)
                try {
                    ApplicationManager.getApplication().runWriteAction {
                        document.setText(formatted)
                    }
                } finally {
                    document.putUserData(CHANGE_GUARD_KEY, false)
                }
            }
        } catch (e: Exception) {
            // If formatting fails, keep the original text; IDE will indicate JSON issues
        }
    }
}
