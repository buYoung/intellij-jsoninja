package com.livteam.jsoninja.ui.dialog.component

import com.intellij.diff.DiffManager
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.livteam.jsoninja.LocalizationBundle
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * Panel that displays the diff results between two JSON contents
 */
class JsonDiffViewerPanel(
    private val project: Project,
    private val parentDisposable: Disposable
) : JPanel(BorderLayout()) {

    companion object {
        private const val MIN_WIDTH = 800
        private const val MIN_HEIGHT = 200
    }

    private var currentDiffPanel: JComponent? = null

    init {
        border = BorderFactory.createTitledBorder(LocalizationBundle.message("dialog.json.diff.result"))
        minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
    }

    fun showDiff(diffRequest: DiffRequest) {
        try {
            val diffPanel = DiffManager.getInstance().createRequestPanel(
                project,
                parentDisposable,
                null
            )

            diffPanel.setRequest(diffRequest)

            updateContent(diffPanel.component)
        } catch (e: Exception) {
            showError("Error: ${e.message}")
        }
    }

    fun showError(message: String) {
        val errorLabel = JLabel(message).apply {
            foreground = JBColor.RED
            horizontalAlignment = SwingConstants.CENTER
        }
        updateContent(errorLabel)
    }

    fun clear() {
        removeAll()
        currentDiffPanel = null
        revalidate()
        repaint()
    }

    private fun updateContent(component: JComponent) {
        removeAll()
        currentDiffPanel = component
        add(component, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
}