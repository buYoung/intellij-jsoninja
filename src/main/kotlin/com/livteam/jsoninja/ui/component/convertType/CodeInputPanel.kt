package com.livteam.jsoninja.ui.component.convertType

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.ui.component.editor.EditorTextFieldFactory
import com.livteam.jsoninja.ui.component.editor.setEditorTextAndRefreshCodeFolding
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
        setEditorTextAndRefreshCodeFolding(project, editorField, text)
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

        editorField = EditorTextFieldFactory.createCodeField(
            project = project,
            fileExtension = fileExtension,
            initialText = text,
            placeholderText = placeholderText,
            shouldApplyEditorColors = true,
            shouldApplyHighlighter = true,
            shouldShowHorizontalScrollbar = true,
            shouldShowVerticalScrollbar = true,
            configureEditorSettings = {
                isLineNumbersShown = true
                isWhitespacesShown = false
                isUseSoftWraps = true
                isRightMarginShown = false
                isIndentGuidesShown = true
                isCaretRowShown = true
            },
        ).also { createdEditorField ->
            createdEditorField.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    onTextChanged?.invoke(createdEditorField.text)
                }
            })
        }

        editorField?.let { add(it, BorderLayout.CENTER) }
        revalidate()
        repaint()
    }

    override fun dispose() {
        (editorField as? Disposable)?.let(Disposer::dispose)
        editorField = null
    }
}
