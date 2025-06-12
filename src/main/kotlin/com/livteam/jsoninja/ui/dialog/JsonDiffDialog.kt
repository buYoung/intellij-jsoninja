package com.livteam.jsoninja.ui.dialog

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.ui.dialog.component.*
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSplitPane

class JsonDiffDialog(
    private val project: Project,
    private val diffService: JsonDiffService,
    private val currentJson: String? = null
) : DialogWrapper(project), Disposable {

    companion object {
        private const val DIALOG_WIDTH = 800
        private const val DIALOG_HEIGHT = 1200
        private const val SPLIT_DIVIDER_LOCATION = 400
        private const val SPLIT_RESIZE_WEIGHT = 0.4
    }

    private lateinit var editorPanel: JsonDiffEditorPanel
    private lateinit var optionsPanel: JsonDiffOptionsPanel
    private lateinit var diffViewerPanel: JsonDiffViewerPanel
    private lateinit var actionHandler: DefaultEditorActionHandler

    init {
        title = LocalizationBundle.message("dialog.json.diff.title")
        setOKButtonText(LocalizationBundle.message("dialog.json.diff.close"))
        init()

        // Set dialog size
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT)

        // Register for disposal
        Disposer.register(myDisposable, this)
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())

        // Initialize components
        initializeComponents()

        // Create editors container
        val editorsContainer = JPanel(BorderLayout())
        editorsContainer.add(editorPanel, BorderLayout.CENTER)
        editorsContainer.add(optionsPanel, BorderLayout.SOUTH)

        // Create split pane
        val splitPane = createSplitPane(editorsContainer)
        mainPanel.add(splitPane, BorderLayout.CENTER)

        // Setup listeners
        setupListeners()

        // Initial diff if both have content
        if (editorPanel.getLeftContent().isNotBlank() && editorPanel.getRightContent().isNotBlank()) {
            updateDiff()
        }

        return mainPanel
    }

    private fun initializeComponents() {
        // Initialize editor panel
        editorPanel = JsonDiffEditorPanel(
            project,
            initialLeftContent = currentJson
        )

        // Initialize action handler with editorPanel reference
        actionHandler = DefaultEditorActionHandler(project, editorPanel)
        editorPanel.addActionHandler(actionHandler)

        // Initialize options panel
        optionsPanel = JsonDiffOptionsPanel()

        // Initialize diff viewer panel
        diffViewerPanel = JsonDiffViewerPanel(project, this)
    }

    private fun createSplitPane(editorsContainer: JPanel): JSplitPane {
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, true)
        splitPane.topComponent = editorsContainer
        splitPane.bottomComponent = diffViewerPanel
        splitPane.dividerLocation = SPLIT_DIVIDER_LOCATION
        splitPane.resizeWeight = SPLIT_RESIZE_WEIGHT
        return splitPane
    }

    private fun setupListeners() {
        // Editor change listeners
        editorPanel.setLeftEditorChangeCallback { content ->
            WriteCommandAction.runWriteCommandAction(project) {
                updateDiffWithContent(content, editorPanel.getRightContent())
            }
        }

        editorPanel.setRightEditorChangeCallback { content ->
            WriteCommandAction.runWriteCommandAction(project) {
                updateDiffWithContent(editorPanel.getLeftContent(), content)
            }
        }

        // Options change listener
        optionsPanel.addOptionsChangeListener {
            updateDiff()
        }
    }

    private fun updateDiff() {
        val leftJson = editorPanel.getLeftContent()
        val rightJson = editorPanel.getRightContent()
        updateDiffWithContent(leftJson, rightJson)
    }
    
    private fun updateDiffWithContent(leftJson: String, rightJson: String) {
        val leftContent = leftJson.trim()
        val rightContent = rightJson.trim()

        if (leftContent.isEmpty() || rightContent.isEmpty()) {
            diffViewerPanel.clear()
            return
        }

        // Validate JSON
        val leftValidation = diffService.validateJson(leftContent)
        val rightValidation = diffService.validateJson(rightContent)

        if (!leftValidation.first || !rightValidation.first) {
            diffViewerPanel.showValidationError()
            return
        }

        // Create and show diff
        try {
            val request = diffService.createDiffRequest(
                leftContent,
                rightContent,
                semantic = optionsPanel.isSemanticComparisonEnabled()
            )
            diffViewerPanel.showDiff(request)
        } catch (e: Exception) {
            diffViewerPanel.showError("Error: ${e.message}")
        }
    }


    override fun dispose() {
        // Cleanup resources if needed
        super.dispose()
    }

    override fun createActions() = arrayOf(getOKAction())
}