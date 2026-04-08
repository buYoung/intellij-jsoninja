package com.livteam.jsoninja.ui.component.convertType

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import com.livteam.jsoninja.ui.component.editor.SimpleJsonDocumentCreator
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JButton
import javax.swing.JPanel

class CodePreviewPanel(
    private val project: Project,
) : JBPanel<CodePreviewPanel>(BorderLayout()), Disposable {
    private companion object {
        private const val EMPTY_CARD = "empty"
        private const val LOADING_CARD = "loading"
        private const val ERROR_CARD = "error"
        private const val SUCCESS_CARD = "success"
    }

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val emptyLabel = JBLabel(LocalizationBundle.message("common.convert.empty.preview"))
    private val loadingLabel = JBLabel(LocalizationBundle.message("common.convert.generating"))
    private val errorLabel = JBLabel()
    private val copyButton = JButton(LocalizationBundle.message("common.convert.copy"))
    private var viewerField: EditorTextField? = null
    private var currentFileExtension: String = "txt"
    private var onCopyRequested: (() -> Unit)? = null

    init {
        border = JBUI.Borders.empty()
        setupCards()
        setEmpty()
    }

    fun setOnCopyRequested(callback: () -> Unit) {
        onCopyRequested = callback
    }

    fun setEmpty() {
        cardLayout.show(cardPanel, EMPTY_CARD)
    }

    fun setLoading() {
        cardLayout.show(cardPanel, LOADING_CARD)
    }

    fun setError(message: String) {
        errorLabel.text = LocalizationBundle.message("common.convert.error", message)
        cardLayout.show(cardPanel, ERROR_CARD)
    }

    fun setSuccess(
        text: String,
        fileExtension: String,
    ) {
        ensureViewer(fileExtension)
        viewerField?.text = text
        cardLayout.show(cardPanel, SUCCESS_CARD)
    }

    private fun setupCards() {
        copyButton.addActionListener { onCopyRequested?.invoke() }

        cardPanel.add(wrapStateLabel(emptyLabel), EMPTY_CARD)
        cardPanel.add(wrapStateLabel(loadingLabel), LOADING_CARD)
        cardPanel.add(wrapStateLabel(errorLabel), ERROR_CARD)
        cardPanel.add(
            JPanel(BorderLayout()).apply {
                add(copyButton, BorderLayout.NORTH)
            },
            SUCCESS_CARD,
        )
        add(cardPanel, BorderLayout.CENTER)
    }

    private fun wrapStateLabel(label: JBLabel): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(label, BorderLayout.NORTH)
        }
    }

    private fun ensureViewer(fileExtension: String) {
        if (viewerField != null && currentFileExtension == fileExtension) {
            return
        }

        currentFileExtension = fileExtension
        val successPanel = cardPanel.components
            .filterIsInstance<JPanel>()
            .last()
        viewerField?.let { oldViewer ->
            successPanel.remove(oldViewer)
            (oldViewer as? Disposable)?.let(Disposer::dispose)
        }

        val document = JsonDocumentFactory.createJsonDocument(
            value = "",
            project = project,
            documentCreator = SimpleJsonDocumentCreator(),
            fileExtension = fileExtension,
        )
        val fileType = resolveFileType(fileExtension)
        viewerField = EditorTextField(document, project, fileType, true, false).also { createdViewer ->
            createdViewer.putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)
            createdViewer.addSettingsProvider { editor ->
                editor.settings.isLineNumbersShown = true
                editor.settings.isUseSoftWraps = true
                editor.settings.isRightMarginShown = false
                editor.settings.isIndentGuidesShown = false
                editor.isEmbeddedIntoDialogWrapper = true
                if (editor is EditorEx) {
                    val scheme = EditorColorsManager.getInstance().globalScheme
                    editor.colorsScheme = scheme
                    editor.highlighter = HighlighterFactory.createHighlighter(project, fileType)
                    editor.setHorizontalScrollbarVisible(true)
                    editor.setVerticalScrollbarVisible(true)
                }
            }
        }
        viewerField?.let { successPanel.add(it, BorderLayout.CENTER) }
        successPanel.revalidate()
        successPanel.repaint()
    }

    private fun resolveFileType(fileExtension: String): FileType {
        val resolvedFileType = FileTypeManager.getInstance().getFileTypeByExtension(fileExtension)
        return if (resolvedFileType is UnknownFileType) PlainTextFileType.INSTANCE else resolvedFileType
    }

    override fun dispose() {
        (viewerField as? Disposable)?.let(Disposer::dispose)
        viewerField = null
    }
}
