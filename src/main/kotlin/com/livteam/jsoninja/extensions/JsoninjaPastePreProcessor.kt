package com.livteam.jsoninja.extensions

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.component.JsonEditor

/**
 * Pre-processes pasted content in JSONinja editors.
 * Automatically formats valid JSON content upon paste according to plugin settings.
 */
class JsoninjaPastePreProcessor : CopyPastePreProcessor {

    companion object {
        // Threshold for switching to background processing (approx. 100KB)
        // Below this size, processing on EDT is fast enough and avoids progress dialog flicker.
        private const val SYNC_PROCESSING_THRESHOLD = 100_000
    }

    override fun preprocessOnPaste(
        project: Project,
        file: PsiFile,
        editor: Editor,
        text: String,
        rawText: RawText?
    ): String {
        if (editor.document.getUserData(JsonEditor.JSONINJA_EDITOR_KEY) != true) {
            return text
        }

        val formatterService = project.getService(JsonFormatterService::class.java)
        val settings = JsoninjaSettingsState.getInstance(project)

        // Define formatting logic
        fun formatText(): String {
            if (!formatterService.isValidJson(text)) {
                return text
            }
            return try {
                val pasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)
                formatterService.formatJson(text, pasteFormatState)
            } catch (e: Exception) {
                // Fallback to original text if formatting fails
                text
            }
        }

        // Choose execution strategy based on content size
        if (text.length < SYNC_PROCESSING_THRESHOLD) {
            return formatText()
        }

        // 4. Safe path: Process on background thread with modal progress
        // This avoids freezing the UI for large content while ensuring the paste operation waits for the result.
        val resultRef = Ref.create(text)
        ProgressManager.getInstance().runProcessWithProgressSynchronously({
            resultRef.set(formatText())
        }, "Formatting JSON...", true, project)

        return resultRef.get()
    }

    override fun preprocessOnCopy(
        file: PsiFile,
        startOffsets: IntArray,
        endOffsets: IntArray,
        text: String
    ): String? {
        // We don't need to modify copied text
        return null
    }
}
