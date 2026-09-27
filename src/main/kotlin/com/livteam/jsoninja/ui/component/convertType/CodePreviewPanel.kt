package com.livteam.jsoninja.ui.component.convertType

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.editor.EditorTextFieldFactory
import com.livteam.jsoninja.ui.component.convertType.highlighting.TypeCodeHighlighterResolver
import com.livteam.jsoninja.ui.component.convertType.highlighting.TypeCodePreviewHighlighting
import com.livteam.jsoninja.ui.component.editor.setEditorTextAndRefreshCodeFolding
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JButton
import javax.swing.JPanel

class CodePreviewPanel(
    private val project: Project,
    private val shouldShowCopyButton: Boolean = true,
) : JBPanel<CodePreviewPanel>(BorderLayout()), Disposable, EditorColorsListener {
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
    private val warningsText = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val warningsPanel = JPanel(BorderLayout()).apply {
        add(JBLabel(LocalizationBundle.message("common.convert.warnings")), BorderLayout.NORTH)
        add(JBScrollPane(warningsText).apply { preferredSize = java.awt.Dimension(0, JBUI.scale(80)) }, BorderLayout.CENTER)
        isVisible = false
    }
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
        clearWarnings()
        copyButton.isEnabled = false
        cardLayout.show(cardPanel, EMPTY_CARD)
    }

    fun setLoading() {
        clearWarnings()
        copyButton.isEnabled = false
        cardLayout.show(cardPanel, LOADING_CARD)
    }

    fun setError(message: String) {
        clearWarnings()
        copyButton.isEnabled = false
        errorLabel.text = LocalizationBundle.message("common.convert.error", message)
        cardLayout.show(cardPanel, ERROR_CARD)
    }

    fun setSuccess(
        text: String,
        fileExtension: String,
    ) = setSuccess(text, fileExtension, emptyList())

    fun setSuccess(text: String, fileExtension: String, warnings: List<String>) {
        // The IDE tracks visible editors when they join a window. Creating the viewer in
        // the hidden success card leaves native highlighting inactive after a language switch.
        cardLayout.show(cardPanel, SUCCESS_CARD)
        ensureViewer(fileExtension)
        setEditorTextAndRefreshCodeFolding(project, viewerField, text)
        warningsText.text = warnings.joinToString("\n")
        warningsText.caretPosition = 0
        warningsPanel.isVisible = warnings.isNotEmpty()
        copyButton.isEnabled = text.isNotBlank()
    }

    fun clearWarnings() {
        warningsText.text = ""
        warningsPanel.isVisible = false
    }

    private fun setupCards() {
        copyButton.addActionListener { onCopyRequested?.invoke() }

        cardPanel.add(wrapStateLabel(emptyLabel), EMPTY_CARD)
        cardPanel.add(wrapStateLabel(loadingLabel), LOADING_CARD)
        cardPanel.add(wrapStateLabel(errorLabel), ERROR_CARD)
        cardPanel.add(
            JPanel(BorderLayout()).apply {
                if (shouldShowCopyButton) add(copyButton, BorderLayout.NORTH)
                add(warningsPanel, BorderLayout.SOUTH)
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

        val shouldEnableCodeFolding = shouldEnableCodeFolding(fileExtension)
        viewerField = EditorTextFieldFactory.createCodeField(
            project = project,
            fileExtension = fileExtension,
            isViewer = true,
            shouldEnableCodeFolding = shouldEnableCodeFolding,
            shouldApplyEditorColors = true,
            shouldApplyHighlighter = true,
            highlighterProvider = { owner, fileType, scheme ->
                TypeCodeHighlighterResolver.create(fileExtension, owner, fileType, scheme)
            },
            shouldShowHorizontalScrollbar = true,
            shouldShowVerticalScrollbar = true,
            configureEditorSettings = {
                isLineNumbersShown = true
                isUseSoftWraps = true
                isRightMarginShown = false
                isIndentGuidesShown = false
            },
            customizeEditor = { TypeCodePreviewHighlighting.enable(this, fileExtension) },
        )
        viewerField?.let { successPanel.add(it, BorderLayout.CENTER) }
        successPanel.revalidate()
        successPanel.repaint()
    }

    private fun shouldEnableCodeFolding(fileExtension: String): Boolean {
        return fileExtension.equals("json", ignoreCase = true) ||
            fileExtension.equals("json5", ignoreCase = true)
    }

    override fun dispose() {
        clearWarnings()
        (viewerField as? Disposable)?.let(Disposer::dispose)
        viewerField = null
    }

    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
        val field = viewerField ?: return
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            TypeCodeHighlighterResolver.refresh(field, currentFileExtension, project)
        } else javax.swing.SwingUtilities.invokeLater {
            if (field === viewerField) TypeCodeHighlighterResolver.refresh(field, currentFileExtension, project)
        }
    }
}
