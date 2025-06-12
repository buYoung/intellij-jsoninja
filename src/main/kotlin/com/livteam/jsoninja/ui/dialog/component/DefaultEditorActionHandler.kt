package com.livteam.jsoninja.ui.dialog.component

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.JsonEditor
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * Default implementation of editor actions
 */
class DefaultEditorActionHandler(
    private val project: Project,
    private val editorPanel: JsonDiffEditorPanel? = null
) : JsonDiffEditorPanel.EditorActionHandler {

    companion object {
        private const val JSON_FILE_EXTENSION = "json"
    }

    override fun loadFromFile(editor: JsonEditor) {
        val descriptor = createFileChooserDescriptor()
        val file = FileChooser.chooseFile(descriptor, project, null)
        
        file?.let {
            loadFileContent(it.path, editor)
        }
    }

    override fun pasteFromClipboard(editor: JsonEditor) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val data = clipboard.getData(DataFlavor.stringFlavor) as? String
            data?.let {
                editor.setText(it)
                editorPanel?.triggerCallbackForEditor(editor, it)
            }
        } catch (e: Exception) {
            // Silently ignore clipboard errors
        }
    }

    private fun createFileChooserDescriptor(): FileChooserDescriptor {
        return FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.extension == JSON_FILE_EXTENSION }
    }

    private fun loadFileContent(filePath: String, editor: JsonEditor) {
        try {
            val content = File(filePath).readText()
            editor.setText(content)
            editorPanel?.triggerCallbackForEditor(editor, content)
        } catch (e: Exception) {
            showFileReadError(e)
        }
    }

    private fun showFileReadError(exception: Exception) {
        Messages.showErrorDialog(
            project,
            LocalizationBundle.message("dialog.json.diff.file.read.error", exception.message ?: ""),
            LocalizationBundle.message("dialog.json.diff.error")
        )
    }
}