package com.livteam.jsoninja.ui.component.convertType

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

/** Shared presentation for the input, read-only preview, and visible conversion settings. */
class ConvertTypeWorkspacePanel(
    project: Project,
    inputComponent: JComponent,
    settingsComponent: JComponent,
    inputTitle: String,
) : JBPanel<ConvertTypeWorkspacePanel>(BorderLayout()), Disposable {
    private val previewPanel = CodePreviewPanel(project, shouldShowCopyButton = false)
    private val previewTitleLabel = JBLabel(LocalizationBundle.message("common.convert.preview"))
    private val inputLanguageLabel = JBLabel().apply {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }
    private val previewLoadingIcon = AsyncProcessIcon("JSONinjaConvertPreviewHeader").apply {
        isVisible = false
        suspend()
    }
    private var canCopyPreview = false
    private var onCopyRequested: (() -> Unit)? = null
    private val copyAction = object : DumbAwareAction(
        LocalizationBundle.messagePointer("common.convert.copy"),
        LocalizationBundle.messagePointer("common.convert.copy"),
        AllIcons.Actions.Copy,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            onCopyRequested?.invoke()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = canCopyPreview
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
    private val previewCopyToolbar: ActionToolbar = ActionManager.getInstance()
        .createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, DefaultActionGroup(copyAction), true)
        .apply {
            targetComponent = previewPanel
            component.isOpaque = false
            component.border = JBUI.Borders.empty()
        }
    private val previewHeaderActions = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
        isOpaque = false
        add(previewLoadingIcon)
        add(previewCopyToolbar.component)
    }
    private val editorSplitter = JBSplitter(false, SPLITTER_PROPORTION_KEY, 0.5f).apply {
        setHonorComponentsMinimumSize(true)
        firstComponent = createEditorPane(inputComponent, JBLabel(inputTitle), inputLanguageLabel)
        secondComponent = createEditorPane(previewPanel, previewTitleLabel, previewHeaderActions)
    }

    init {
        Disposer.register(this, previewPanel)
        Disposer.register(this, previewLoadingIcon)
        border = JBUI.Borders.empty(16, 12, 12, 12)
        add(editorSplitter, BorderLayout.CENTER)
        add(createSettingsPanel(settingsComponent), BorderLayout.EAST)
        showEmptyPreview()
    }

    fun setInputLanguage(language: String) {
        inputLanguageLabel.text = language
    }

    fun setPreviewLanguage(language: String) {
        previewTitleLabel.text = LocalizationBundle.message("common.convert.preview.language", language)
    }

    fun setOnCopyRequested(callback: () -> Unit) {
        onCopyRequested = callback
    }

    fun showEmptyPreview() {
        setLoadingIndicatorVisible(false)
        setCopyEnabled(false)
        previewPanel.setEmpty()
    }

    fun showLoadingPreview() {
        setLoadingIndicatorVisible(true)
        setCopyEnabled(false)
        previewPanel.setLoading()
    }

    fun stopLoadingIndicator() {
        setLoadingIndicatorVisible(false)
        setCopyEnabled(false)
    }

    fun showErrorPreview(message: String) {
        setLoadingIndicatorVisible(false)
        setCopyEnabled(false)
        previewPanel.setError(message)
    }

    fun showSuccessPreview(text: String, fileExtension: String, warnings: List<String> = emptyList()) {
        setLoadingIndicatorVisible(false)
        previewPanel.setSuccess(text, fileExtension, warnings)
        setCopyEnabled(text.isNotBlank())
    }

    fun clearPreviewWarnings() = previewPanel.clearWarnings()

    override fun dispose() {
        previewLoadingIcon.suspend()
        editorSplitter.dispose()
    }

    private fun setLoadingIndicatorVisible(isVisible: Boolean) {
        if (isVisible) {
            previewLoadingIcon.isVisible = true
            previewLoadingIcon.resume()
        } else {
            previewLoadingIcon.suspend()
            previewLoadingIcon.isVisible = false
        }
    }

    private fun setCopyEnabled(isEnabled: Boolean) {
        if (canCopyPreview == isEnabled) return
        canCopyPreview = isEnabled
        previewCopyToolbar.updateActionsAsync()
    }

    private fun createEditorPane(content: JComponent, title: JBLabel, trailing: JComponent): JComponent {
        val header = JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(12), 0)).apply {
            border = JBUI.Borders.empty(6, 10)
            add(title, BorderLayout.CENTER)
            add(trailing, BorderLayout.EAST)
        }
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBColor.border())
            minimumSize = JBUI.size(200, 160)
            add(header, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }
    }

    private fun createSettingsPanel(settings: JComponent): JComponent {
        val form = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.emptyRight(8)
            add(settings, BorderLayout.NORTH)
        }
        val scrollPane = JBScrollPane(
            form,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
        ).apply {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
        }
        return object : JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(16))) {
            override fun getPreferredSize(): Dimension {
                val size = super.getPreferredSize()
                return Dimension(maxOf(JBUI.scale(280), size.width), size.height)
            }
        }.apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.emptyLeft(12),
                JBUI.Borders.compound(
                    JBUI.Borders.customLineLeft(JBColor.border()),
                    JBUI.Borders.empty(8, 16, 0, 0),
                ),
            )
            add(JBLabel(LocalizationBundle.message("common.convert.settings")), BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    private companion object {
        const val SPLITTER_PROPORTION_KEY = "com.livteam.jsoninja.convertType.editorSplitter"
    }
}
