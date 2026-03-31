package com.livteam.jsoninja.ui.component.convertType

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import com.livteam.jsoninja.ui.component.editor.SimpleJsonDocumentCreator
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class CodeInputPanel(
    private val project: Project?,
    initialLanguage: SupportedLanguage = SupportedLanguage.KOTLIN,
    initialPlaceholderText: String = "",
) : Disposable {
    private var selectedLanguage = initialLanguage
    private var placeholderText = initialPlaceholderText
    private var onTextChangedCallback: ((String) -> Unit)? = null

    private lateinit var rootPanel: JPanel
    private lateinit var editorTextField: EditorTextField

    private val editorDocumentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            onTextChangedCallback?.invoke(editorTextField.text)
        }
    }

    val component: JComponent by lazy { createComponent() }

    fun setOnTextChanged(callback: (String) -> Unit) {
        onTextChangedCallback = callback
    }

    fun getText(): String = editorTextField.text

    fun setText(text: String) {
        editorTextField.text = text
    }

    fun setPlaceholder(text: String) {
        placeholderText = text
        if (::editorTextField.isInitialized) {
            editorTextField.setPlaceholder(text)
        }
    }

    fun setLanguage(language: SupportedLanguage) {
        selectedLanguage = language
        if (!::rootPanel.isInitialized) {
            return
        }

        val currentText = editorTextField.text
        replaceEditorTextField(currentText)
    }

    override fun dispose() {
        if (::editorTextField.isInitialized) {
            editorTextField.removeDocumentListener(editorDocumentListener)
            (editorTextField as? Disposable)?.let { Disposer.dispose(it) }
        }
        if (::rootPanel.isInitialized) {
            rootPanel.removeAll()
        }
    }

    private fun createComponent(): JComponent {
        rootPanel = JPanel(BorderLayout())
        editorTextField = createEditorTextField("")
        rootPanel.add(editorTextField, BorderLayout.CENTER)
        return rootPanel
    }

    private fun replaceEditorTextField(text: String) {
        editorTextField.removeDocumentListener(editorDocumentListener)
        rootPanel.remove(editorTextField)
        (editorTextField as? Disposable)?.let { Disposer.dispose(it) }

        editorTextField = createEditorTextField(text)
        rootPanel.add(editorTextField, BorderLayout.CENTER)
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    private fun createEditorTextField(text: String): EditorTextField {
        val document = JsonDocumentFactory.createJsonDocument(
            text,
            project,
            SimpleJsonDocumentCreator(),
            selectedLanguage.fileExtension,
        )

        val fileType = FileTypeManager.getInstance().getFileTypeByExtension(selectedLanguage.fileExtension)
            .takeUnless { it is UnknownFileType }
            ?: PlainTextFileType.INSTANCE

        return EditorTextField(document, project, fileType, false, false).apply {
            preferredSize = JBUI.size(620, 180)
            setPlaceholder(placeholderText)
            addSettingsProvider { editor ->
                editor.settings.applyCodeInputSettings()
                editor.isEmbeddedIntoDialogWrapper = true
            }
            addDocumentListener(editorDocumentListener)
            putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)
        }
    }

    private fun EditorSettings.applyCodeInputSettings() {
        isLineNumbersShown = true
        isWhitespacesShown = true
        isCaretRowShown = true
        isUseSoftWraps = true
    }
}
