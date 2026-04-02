package com.livteam.jsoninja.ui.component.convertType

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import com.livteam.jsoninja.ui.component.editor.SimpleJsonDocumentCreator
import java.awt.BorderLayout
import javax.swing.JPanel

class CodeInputPanel(
    private val project: Project,
) : JBPanel<CodeInputPanel>(BorderLayout()), Disposable {
    private var editorField: EditorTextField? = null
    private var currentFileExtension: String = "txt"
    private var currentPlaceholderText: String = ""
    private var onTextChanged: ((String) -> Unit)? = null

    init {
        border = JBUI.Borders.empty()
        rebuildEditor(text = "", fileExtension = currentFileExtension, placeholderText = currentPlaceholderText)
    }

    fun updateLanguage(
        fileExtension: String,
        placeholderText: String,
    ) {
        if (currentFileExtension == fileExtension && currentPlaceholderText == placeholderText) {
            return
        }
        rebuildEditor(getText(), fileExtension, placeholderText)
    }

    fun setText(text: String) {
        editorField?.text = text
    }

    fun getText(): String = editorField?.text.orEmpty()

    fun setOnTextChanged(callback: (String) -> Unit) {
        onTextChanged = callback
    }

    private fun rebuildEditor(
        text: String,
        fileExtension: String,
        placeholderText: String,
    ) {
        currentFileExtension = fileExtension
        currentPlaceholderText = placeholderText
        editorField?.let { oldEditorField ->
            remove(oldEditorField)
            (oldEditorField as? Disposable)?.let(Disposer::dispose)
        }

        val document = JsonDocumentFactory.createJsonDocument(
            value = text,
            project = project,
            documentCreator = SimpleJsonDocumentCreator(),
            fileExtension = fileExtension,
        )
        val fileType = resolveFileType(fileExtension)
        editorField = EditorTextField(document, project, fileType, false, false).also { createdEditorField ->
            createdEditorField.setPlaceholder(placeholderText)
            createdEditorField.putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)
            createdEditorField.addSettingsProvider { editor ->
                editor.settings.isLineNumbersShown = true
                editor.settings.isWhitespacesShown = false
                editor.settings.isUseSoftWraps = true
                editor.settings.isRightMarginShown = false
                editor.settings.isIndentGuidesShown = true
                editor.settings.isFoldingOutlineShown = true
                editor.settings.isCaretRowShown = true
                editor.isEmbeddedIntoDialogWrapper = true
                if (editor is EditorEx) {
                    val scheme = EditorColorsManager.getInstance().globalScheme
                    editor.colorsScheme = scheme
                    editor.highlighter = HighlighterFactory.createHighlighter(project, fileType)
                    editor.setHorizontalScrollbarVisible(true)
                    editor.setVerticalScrollbarVisible(true)
                }
            }
            createdEditorField.document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    onTextChanged?.invoke(createdEditorField.text)
                }
            })
        }

        editorField?.let { add(it, BorderLayout.CENTER) }
        revalidate()
        repaint()
    }

    private fun resolveFileType(fileExtension: String): FileType {
        val resolvedFileType = FileTypeManager.getInstance().getFileTypeByExtension(fileExtension)
        return if (resolvedFileType is UnknownFileType) PlainTextFileType.INSTANCE else resolvedFileType
    }

    override fun dispose() {
        (editorField as? Disposable)?.let(Disposer::dispose)
        editorField = null
    }
}
