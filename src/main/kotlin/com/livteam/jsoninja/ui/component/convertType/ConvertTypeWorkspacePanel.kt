package com.livteam.jsoninja.ui.component.convertType

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton
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
        foreground = UIUtil.getContextHelpForeground()
    }
    private val statusLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(10, 0, 0, 0)
    }
    private val copyButton = JButton(LocalizationBundle.message("common.convert.copy.result"))
    private val editorSplitter = JBSplitter(false, 0.5f).apply {
        setHonorComponentsMinimumSize(true)
        firstComponent = createEditorPane(inputComponent, JBLabel(inputTitle), inputLanguageLabel)
        secondComponent = createEditorPane(
            previewPanel,
            previewTitleLabel,
            JBLabel(LocalizationBundle.message("common.convert.read.only")).apply {
                foreground = UIUtil.getContextHelpForeground()
            },
        )
    }
    private var onCopyRequested: (() -> Unit)? = null

    init {
        border = JBUI.Borders.empty(16, 12, 12, 12)
        add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(editorSplitter, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }, BorderLayout.CENTER)
        add(createSettingsPanel(settingsComponent), BorderLayout.EAST)
        copyButton.addActionListener { onCopyRequested?.invoke() }
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
        previewPanel.setEmpty()
        setPreviewStatus(LocalizationBundle.message("common.convert.status.empty"), canCopy = false)
    }

    fun showLoadingPreview() {
        previewPanel.setLoading()
        setPreviewStatus(LocalizationBundle.message("common.convert.status.loading"), canCopy = false)
    }

    fun showErrorPreview(message: String) {
        previewPanel.setError(message)
        setPreviewStatus(LocalizationBundle.message("common.convert.status.error"), canCopy = false)
    }

    fun showSuccessPreview(text: String, fileExtension: String, warnings: List<String> = emptyList()) {
        previewPanel.setSuccess(text, fileExtension, warnings)
        val statusKey = if (warnings.isEmpty()) "common.convert.status.ready" else "common.convert.status.warnings"
        setPreviewStatus(LocalizationBundle.message(statusKey), canCopy = text.isNotBlank())
    }

    fun clearPreviewWarnings() = previewPanel.clearWarnings()

    override fun dispose() {
        previewPanel.dispose()
        editorSplitter.dispose()
    }

    private fun setPreviewStatus(text: String, canCopy: Boolean) {
        statusLabel.text = text
        copyButton.isEnabled = canCopy
    }

    private fun createEditorPane(content: JComponent, title: JBLabel, detail: JBLabel): JComponent {
        val header = JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(12), 0)).apply {
            border = JBUI.Borders.empty(8, 10)
            add(title, BorderLayout.CENTER)
            add(detail, BorderLayout.EAST)
        }
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.customLine(UIUtil.getBoundsColor())
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
        val copyPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineTop(UIUtil.getBoundsColor()),
                JBUI.Borders.emptyTop(12),
            )
            add(copyButton, BorderLayout.CENTER)
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
                    JBUI.Borders.customLineLeft(UIUtil.getBoundsColor()),
                    JBUI.Borders.empty(8, 16, 0, 0),
                ),
            )
            add(JBLabel(LocalizationBundle.message("common.convert.settings")), BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            add(copyPanel, BorderLayout.SOUTH)
        }
    }
}
