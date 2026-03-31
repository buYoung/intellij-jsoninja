package com.livteam.jsoninja.ui.component.convertType

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.utils.ConvertResultUtils
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Font
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class CodePreviewPanel(
    private val project: Project,
) : Disposable {
    private lateinit var rootPanel: JPanel
    private lateinit var contentTextArea: JBTextArea
    private lateinit var copyButton: JButton
    private lateinit var statePanel: JPanel
    private lateinit var emptyStateLabel: JBLabel
    private lateinit var loadingStateLabel: JBLabel
    private lateinit var errorStateLabel: JBLabel

    private var currentContent = ""

    val component: JComponent by lazy { createComponent() }

    fun getContent(): String = currentContent

    fun setContent(text: String) {
        currentContent = text
        contentTextArea.text = text
        copyButton.isEnabled = text.isNotBlank()
        showState(SUCCESS_STATE)
    }

    fun setLoading() {
        currentContent = ""
        copyButton.isEnabled = false
        loadingStateLabel.text = LocalizationBundle.message("common.convert.generating")
        showState(LOADING_STATE)
    }

    fun setError(message: String) {
        currentContent = ""
        copyButton.isEnabled = false
        errorStateLabel.text = LocalizationBundle.message("common.convert.error", message)
        showState(ERROR_STATE)
    }

    fun clear() {
        currentContent = ""
        contentTextArea.text = ""
        copyButton.isEnabled = false
        emptyStateLabel.text = LocalizationBundle.message("common.convert.empty.preview")
        showState(EMPTY_STATE)
    }

    override fun dispose() {
        if (::rootPanel.isInitialized) {
            rootPanel.removeAll()
        }
    }

    private fun createComponent(): JComponent {
        copyButton = JButton(LocalizationBundle.message("common.convert.copy")).apply {
            isEnabled = false
            addActionListener {
                if (currentContent.isNotBlank()) {
                    ConvertResultUtils.copyToClipboard(currentContent, project)
                }
            }
        }

        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(6)
            add(JBLabel(LocalizationBundle.message("common.convert.preview")), BorderLayout.WEST)
            add(copyButton, BorderLayout.EAST)
        }

        contentTextArea = JBTextArea().apply {
            isEditable = false
            lineWrap = false
            wrapStyleWord = false
            border = JBUI.Borders.empty(8)
            font = createEditorFont()
        }

        emptyStateLabel = JBLabel(LocalizationBundle.message("common.convert.empty.preview"), SwingConstants.CENTER).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = font.deriveFont(Font.ITALIC)
        }

        loadingStateLabel = JBLabel(LocalizationBundle.message("common.convert.generating"), AnimatedIcon.Default(), SwingConstants.CENTER)

        errorStateLabel = JBLabel("", SwingConstants.CENTER).apply {
            foreground = JBColor.RED
        }

        statePanel = JPanel(CardLayout()).apply {
            add(wrapState(emptyStateLabel), EMPTY_STATE)
            add(wrapState(loadingStateLabel), LOADING_STATE)
            add(wrapState(errorStateLabel), ERROR_STATE)
            add(JBScrollPane(contentTextArea), SUCCESS_STATE)
        }

        rootPanel = JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(620, 220)
            minimumSize = JBUI.size(320, 200)
            add(headerPanel, BorderLayout.NORTH)
            add(statePanel, BorderLayout.CENTER)
        }

        clear()
        return rootPanel
    }

    private fun wrapState(component: JComponent): JPanel {
        return JPanel(BorderLayout()).apply {
            add(component, BorderLayout.CENTER)
        }
    }

    private fun showState(stateName: String) {
        val layout = statePanel.layout as CardLayout
        layout.show(statePanel, stateName)
    }

    private fun createEditorFont(): Font {
        val scheme = EditorColorsManager.getInstance().globalScheme
        return Font(Font.MONOSPACED, Font.PLAIN, scheme.editorFontSize)
    }

    companion object {
        private const val EMPTY_STATE = "empty"
        private const val LOADING_STATE = "loading"
        private const val ERROR_STATE = "error"
        private const val SUCCESS_STATE = "success"
    }
}
