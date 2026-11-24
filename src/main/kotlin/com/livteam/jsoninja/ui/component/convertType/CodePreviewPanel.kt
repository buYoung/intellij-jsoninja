package com.livteam.jsoninja.ui.component.convertType

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.convertType.highlighting.TypeCodeHighlighterResolver
import com.livteam.jsoninja.ui.component.convertType.highlighting.TypeCodePreviewHighlighting
import com.livteam.jsoninja.ui.component.editor.EditorTextFieldFactory
import com.livteam.jsoninja.ui.component.editor.setEditorTextAndRefreshCodeFolding
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.JButton

class CodePreviewPanel(
    private val project: Project,
    private val shouldShowCopyButton: Boolean = true,
) : JBPanel<CodePreviewPanel>(BorderLayout()), Disposable {
    private companion object {
        private const val EMPTY_CARD = "empty"
        private const val LOADING_CARD = "loading"
        private const val ERROR_CARD = "error"
        private const val SUCCESS_CARD = "success"
    }

    private val cardLayout = CardLayout()
    private val cardPanel = JBPanel<JBPanel<*>>(cardLayout)
    private val emptyLabel = JBLabel(LocalizationBundle.message("common.convert.empty.preview")).apply {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }
    private val cardLoadingIcon = AsyncProcessIcon("JSONinjaConvertPreviewCard").apply {
        suspend()
    }
    private val loadingLabel = JBLabel(LocalizationBundle.message("common.convert.generating")).apply {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }
    private val errorTextArea = JBTextArea().apply {
        name = "convertPreviewError"
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        border = JBUI.Borders.empty(12)
        foreground = NamedColorUtil.getErrorForeground()
    }
    private val errorScrollPane = JBScrollPane(errorTextArea).apply {
        border = JBUI.Borders.empty()
        viewportBorder = JBUI.Borders.empty()
        isOpaque = false
        viewport.isOpaque = false
    }
    private val copyButton = JButton(LocalizationBundle.message("common.convert.copy"))
    private val warningsText = JBTextArea().apply {
        name = "convertPreviewWarnings"
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val warningsPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(JBLabel(LocalizationBundle.message("common.convert.warnings")), BorderLayout.NORTH)
        add(JBScrollPane(warningsText).apply { preferredSize = java.awt.Dimension(0, JBUI.scale(80)) }, BorderLayout.CENTER)
        isVisible = false
    }
    private val successPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private var currentCard: String = EMPTY_CARD
    private var viewerField: EditorTextField? = null
    private var currentFileExtension: String = "txt"
    private var onCopyRequested: (() -> Unit)? = null
    @Volatile
    private var isDisposed = false

    init {
        border = JBUI.Borders.empty()
        Disposer.register(this, cardLoadingIcon)
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { refreshEditorColors() })
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { refreshThemeColors() })
        setupCards()
        setEmpty()
    }

    fun setOnCopyRequested(callback: () -> Unit) {
        onCopyRequested = callback
    }

    fun setEmpty() {
        clearWarnings()
        copyButton.isEnabled = false
        showCard(EMPTY_CARD)
    }

    fun setLoading() {
        clearWarnings()
        copyButton.isEnabled = false
        if (currentCard == EMPTY_CARD) {
            showCard(LOADING_CARD)
        }
    }

    fun setError(message: String) {
        clearWarnings()
        copyButton.isEnabled = false
        errorTextArea.text = LocalizationBundle.message("common.convert.error", message)
        errorTextArea.caretPosition = 0
        showCard(ERROR_CARD)
    }

    fun setSuccess(
        text: String,
        fileExtension: String,
    ) = setSuccess(text, fileExtension, emptyList())

    fun setSuccess(text: String, fileExtension: String, warnings: List<String>) {
        // The IDE tracks visible editors when they join a window. Creating the viewer in
        // the hidden success card leaves native highlighting inactive after a language switch.
        showCard(SUCCESS_CARD)
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

    private fun showCard(cardName: String) {
        currentCard = cardName
        if (cardName == LOADING_CARD) {
            cardLoadingIcon.resume()
        } else {
            cardLoadingIcon.suspend()
        }
        cardLayout.show(cardPanel, cardName)
    }

    private fun setupCards() {
        copyButton.addActionListener { onCopyRequested?.invoke() }

        val loadingRow = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(cardLoadingIcon)
            add(loadingLabel)
        }
        val loadingCard = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(loadingRow, BorderLayout.NORTH)
        }

        if (shouldShowCopyButton) {
            successPanel.add(copyButton, BorderLayout.NORTH)
        }
        successPanel.add(warningsPanel, BorderLayout.SOUTH)

        cardPanel.add(wrapStateLabel(emptyLabel), EMPTY_CARD)
        cardPanel.add(loadingCard, LOADING_CARD)
        cardPanel.add(errorScrollPane, ERROR_CARD)
        cardPanel.add(successPanel, SUCCESS_CARD)
        add(cardPanel, BorderLayout.CENTER)
    }

    private fun wrapStateLabel(label: JBLabel): JBPanel<*> {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(label, BorderLayout.NORTH)
        }
    }

    private fun ensureViewer(fileExtension: String) {
        if (viewerField != null && currentFileExtension == fileExtension) {
            return
        }

        currentFileExtension = fileExtension
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
        isDisposed = true
        cardLoadingIcon.suspend()
        clearWarnings()
        (viewerField as? Disposable)?.let(Disposer::dispose)
        viewerField = null
    }

    private fun refreshThemeColors() {
        if (isDisposed || project.isDisposed) return
        val applyChanges = {
            if (!isDisposed && !project.isDisposed) {
                emptyLabel.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                loadingLabel.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                errorTextArea.foreground = NamedColorUtil.getErrorForeground()
                refreshEditorColors()
            }
        }
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            applyChanges()
        } else {
            app.invokeLater(applyChanges, ModalityState.stateForComponent(this))
        }
    }

    private fun refreshEditorColors() {
        if (isDisposed || project.isDisposed) return
        val field = viewerField ?: return
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            TypeCodeHighlighterResolver.refresh(field, currentFileExtension, project)
        } else {
            app.invokeLater({
                if (!isDisposed && !project.isDisposed && field === viewerField) {
                    TypeCodeHighlighterResolver.refresh(field, currentFileExtension, project)
                }
            }, ModalityState.stateForComponent(this))
        }
    }
}
