package com.livteam.jsoninja.extensions

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
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

    override fun preprocessOnPaste(
        project: Project,
        file: PsiFile,
        editor: Editor,
        text: String,
        rawText: RawText?
    ): String {
        // 1. Check if the editor is a JSONinja editor
        if (editor.document.getUserData(JsonEditor.JSONINJA_EDITOR_KEY) != true) {
            return text
        }

        // 2. Get services
        val formatterService = project.getService(JsonFormatterService::class.java)
        val settings = JsoninjaSettingsState.getInstance(project)

        // 3. Validate JSON first to avoid processing non-JSON content
        if (!formatterService.isValidJson(text)) {
            return text
        }

        // 4. Format the JSON
        // We use the settings.indentSize which is handled internally by the service now
        return try {
            val pasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)
            formatterService.formatJson(text, pasteFormatState)
        } catch (e: Exception) {
            // Fallback to original text if formatting fails
            text
        }
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
