package com.livteam.jsoninja.ui.dialog

import com.intellij.diff.DiffManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.ui.component.JsonEditor
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.swing.*

class JsonDiffDialog(
    private val project: Project,
    private val diffService: JsonDiffService,
    private val currentJson: String? = null
) : DialogWrapper(project), Disposable {

    private lateinit var leftEditor: JsonEditor
    private lateinit var rightEditor: JsonEditor
    private lateinit var semanticCheckbox: JBCheckBox
    private lateinit var diffViewerPanel: JPanel

    init {
        title = LocalizationBundle.message("dialog.json.diff.title")
        setOKButtonText(LocalizationBundle.message("dialog.json.diff.close"))
        init()

        // Set dialog size
        setSize(800, 1200)

        // Register for disposal
        Disposer.register(myDisposable, this)
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())

        // Create editors container
        val editorsContainer = JPanel(BorderLayout())

        // Create editors panel with horizontal split
        val editorsPanel = JPanel(GridLayout(1, 2, 10, 0))
        editorsPanel.border = JBUI.Borders.empty(10)

        // Initialize editors
        leftEditor = createEditor()
        rightEditor = createEditor()

        // Add editor panels
        editorsPanel.add(createEditorPanel(leftEditor, LocalizationBundle.message("dialog.json.diff.left"), true))
        editorsPanel.add(createEditorPanel(rightEditor, LocalizationBundle.message("dialog.json.diff.right"), false))

        editorsContainer.add(editorsPanel, BorderLayout.CENTER)

        // Options panel at the bottom of editors
        editorsContainer.add(createOptionsPanel(), BorderLayout.SOUTH)

        // Create split pane for editors and diff viewer
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, true)
        splitPane.topComponent = editorsContainer

        // Diff viewer panel
        diffViewerPanel = JPanel(BorderLayout())
        diffViewerPanel.border = BorderFactory.createTitledBorder(LocalizationBundle.message("dialog.json.diff.result"))
        diffViewerPanel.minimumSize = Dimension(800, 200)
        splitPane.bottomComponent = diffViewerPanel

        splitPane.dividerLocation = 400
        splitPane.resizeWeight = 0.4

        mainPanel.add(splitPane, BorderLayout.CENTER)

        // Add document listeners for real-time diff
        addDocumentListeners()

        // Set initial JSON if provided
        currentJson?.let {
            leftEditor.setText(it)
        }

        // Initial diff if both have content
        if (leftEditor.getText().isNotBlank() && rightEditor.getText().isNotBlank()) {
            updateDiff()
        }

        return mainPanel
    }

    private fun createEditor(): JsonEditor {
        return JsonEditor(project).apply {
            preferredSize = Dimension(400, 300)
        }
    }

    private fun createEditorPanel(editor: JsonEditor, title: String, isLeft: Boolean): JComponent {
        val panel = JPanel(BorderLayout())

        // Create header panel with title and buttons
        val headerPanel = JPanel(BorderLayout())
        headerPanel.background = JBColor.PanelBackground
        headerPanel.border = JBUI.Borders.empty(5, 10)

        // Title
        val titleLabel = JLabel(title)
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        // Button panel
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
        buttonPanel.isOpaque = false

        if (!isLeft) {
            buttonPanel.add(createButton(LocalizationBundle.message("openJsonFile")) {
                loadFromFile(editor)
            })
            buttonPanel.add(createButton(LocalizationBundle.message("dialog.json.diff.paste")) {
                pasteFromClipboard(editor)
            })
        }
        buttonPanel.add(createButton(LocalizationBundle.message("dialog.json.diff.clear")) {
            editor.setText("")
        })

        headerPanel.add(buttonPanel, BorderLayout.EAST)

        // Add components to panel
        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(editor, BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty()

        return panel
    }

    private fun createOptionsPanel(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))

        semanticCheckbox = JBCheckBox(LocalizationBundle.message("dialog.json.diff.semantic"))
        semanticCheckbox.addActionListener { updateDiff() }

        panel.add(semanticCheckbox)

        return panel
    }

    private fun createButton(text: String, action: () -> Unit): JButton {
        return JButton(text).apply {
            addActionListener { action() }
        }
    }

    private fun addDocumentListeners() {
        leftEditor.setOnContentChangeCallback { content ->
            WriteCommandAction.runWriteCommandAction(project) {
                updateDiff()
            }
        }

        rightEditor.setOnContentChangeCallback { content ->
            WriteCommandAction.runWriteCommandAction(project) {
                updateDiff()
            }
        }
    }

    private fun updateDiff() {
        val leftJson = leftEditor.getText().trim()
        val rightJson = rightEditor.getText().trim()

        if (leftJson.isEmpty() || rightJson.isEmpty()) {
            diffViewerPanel.removeAll()
            diffViewerPanel.revalidate()
            diffViewerPanel.repaint()
            return
        }

        // Validate JSON
        val leftValidation = diffService.validateJson(leftJson)
        val rightValidation = diffService.validateJson(rightJson)

        if (!leftValidation.first || !rightValidation.first) {
            diffViewerPanel.removeAll()
            val errorLabel = JLabel(LocalizationBundle.message("dialog.json.diff.invalid.json.format"))
            errorLabel.foreground = JBColor.RED
            errorLabel.horizontalAlignment = SwingConstants.CENTER
            diffViewerPanel.add(errorLabel, BorderLayout.CENTER)
            diffViewerPanel.revalidate()
            diffViewerPanel.repaint()
            return
        }

        // Create embedded diff viewer
        try {
            val request = diffService.createDiffRequest(
                leftJson,
                rightJson,
                semantic = semanticCheckbox.isSelected
            )

            val diffPanel = DiffManager.getInstance().createRequestPanel(
                project,
                this,
                null
            )

            diffPanel.setRequest(request)

            diffViewerPanel.removeAll()
            diffViewerPanel.add(diffPanel.component, BorderLayout.CENTER)
            diffViewerPanel.revalidate()
            diffViewerPanel.repaint()
        } catch (e: Exception) {
            // Handle error
            diffViewerPanel.removeAll()
            val errorLabel = JLabel("Error: ${e.message}")
            errorLabel.foreground = JBColor.RED
            errorLabel.horizontalAlignment = SwingConstants.CENTER
            diffViewerPanel.add(errorLabel, BorderLayout.CENTER)
            diffViewerPanel.revalidate()
            diffViewerPanel.repaint()
        }
    }

    private fun loadFromFile(editor: JsonEditor) {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.extension == "json" }

        val file = FileChooser.chooseFile(descriptor, project, null)
        if (file != null) {
            try {
                val content = File(file.path).readText()
                editor.setText(content)
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    LocalizationBundle.message("dialog.json.diff.file.read.error", e.message ?: ""),
                    LocalizationBundle.message("dialog.json.diff.error")
                )
            }
        }
    }

    private fun pasteFromClipboard(editor: JsonEditor) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val data = clipboard.getData(DataFlavor.stringFlavor) as? String
            if (data != null) {
                editor.setText(data)
            }
        } catch (e: Exception) {
            // Ignore clipboard errors
        }
    }

    override fun doOKAction() {
        super.doOKAction()
    }

    override fun dispose() {
        super.dispose()
    }

    override fun createActions() = arrayOf(getOKAction())
}