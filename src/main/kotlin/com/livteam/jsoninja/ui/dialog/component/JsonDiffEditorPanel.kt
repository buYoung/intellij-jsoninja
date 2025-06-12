package com.livteam.jsoninja.ui.dialog.component

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.JsonEditor
import java.awt.*
import javax.swing.*

/**
 * Panel that manages left and right JSON editors for diff comparison
 */
class JsonDiffEditorPanel(
    private val project: Project,
    private val leftTitle: String = LocalizationBundle.message("dialog.json.diff.left"),
    private val rightTitle: String = LocalizationBundle.message("dialog.json.diff.right"),
    private val initialLeftContent: String? = null
) : JPanel(GridLayout(1, 2, EDITOR_GAP, 0)) {

    companion object {
        private const val EDITOR_GAP = 10
        private const val EDITOR_WIDTH = 400
        private const val EDITOR_HEIGHT = 300
        private const val HEADER_PADDING = 5
        private const val HEADER_HORIZONTAL_PADDING = 10
        private const val BUTTON_GAP = 5
        private const val TITLE_FONT_SIZE = 14f
    }

    val leftEditor: JsonEditor = createEditor()
    val rightEditor: JsonEditor = createEditor()

    private val actionHandlers = mutableListOf<EditorActionHandler>()
    private var leftEditorCallback: ((String) -> Unit)? = null
    private var rightEditorCallback: ((String) -> Unit)? = null

    init {
        border = JBUI.Borders.empty(10)
        setupEditors()
    }

    private fun setupEditors() {
        add(createEditorPanel(leftEditor, leftTitle, isLeft = true))
        add(createEditorPanel(rightEditor, rightTitle, isLeft = false))

        // Set initial content if provided
        initialLeftContent?.let {
            leftEditor.setText(it)
        }
    }

    private fun createEditor(): JsonEditor {
        return JsonEditor(project).apply {
            preferredSize = Dimension(EDITOR_WIDTH, EDITOR_HEIGHT)
        }
    }

    private fun createEditorPanel(editor: JsonEditor, title: String, isLeft: Boolean): JComponent {
        val panel = JPanel(BorderLayout())

        // Create header
        val headerPanel = createHeaderPanel(title, editor, isLeft)
        
        // Add components
        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(editor, BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty()

        return panel
    }

    private fun createHeaderPanel(title: String, editor: JsonEditor, isLeft: Boolean): JPanel {
        val headerPanel = JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(HEADER_PADDING, HEADER_HORIZONTAL_PADDING)
        }

        // Title
        val titleLabel = JBLabel(title).apply {
            font = font.deriveFont(Font.BOLD, TITLE_FONT_SIZE)
        }
        headerPanel.add(titleLabel, BorderLayout.WEST)

        // Buttons for right panel only
        if (!isLeft) {
            val buttonPanel = createButtonPanel(editor)
            headerPanel.add(buttonPanel, BorderLayout.EAST)
        }

        // Clear button for both panels
        if (isLeft) {
            val clearButtonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, BUTTON_GAP, 0)).apply {
                isOpaque = false
                add(createClearButton(editor))
            }
            headerPanel.add(clearButtonPanel, BorderLayout.EAST)
        }

        return headerPanel
    }

    private fun createButtonPanel(editor: JsonEditor): JPanel {
        return JPanel(FlowLayout(FlowLayout.RIGHT, BUTTON_GAP, 0)).apply {
            isOpaque = false
            
            // Add action buttons
            add(createButton(LocalizationBundle.message("openJsonFile")) {
                notifyActionHandlers { it.loadFromFile(editor) }
            })
            
            add(createButton(LocalizationBundle.message("dialog.json.diff.paste")) {
                notifyActionHandlers { it.pasteFromClipboard(editor) }
            })
            
            add(createClearButton(editor))
        }
    }

    private fun createClearButton(editor: JsonEditor): JButton {
        return createButton(LocalizationBundle.message("dialog.json.diff.clear")) {
            editor.setText("")
            // Manually trigger the callback since setText doesn't trigger it
            val callback = if (editor == leftEditor) leftEditorCallback else rightEditorCallback
            callback?.invoke("")
        }
    }

    private fun createButton(text: String, action: () -> Unit): JButton {
        return JButton(text).apply {
            addActionListener { action() }
        }
    }

    fun addActionHandler(handler: EditorActionHandler) {
        actionHandlers.add(handler)
    }

    private fun notifyActionHandlers(action: (EditorActionHandler) -> Unit) {
        actionHandlers.forEach(action)
    }
    
    fun triggerCallbackForEditor(editor: JsonEditor, content: String) {
        val callback = if (editor == leftEditor) leftEditorCallback else rightEditorCallback
        callback?.invoke(content)
    }

    fun setLeftEditorChangeCallback(callback: (String) -> Unit) {
        leftEditorCallback = callback
        leftEditor.setOnContentChangeCallback(callback)
    }

    fun setRightEditorChangeCallback(callback: (String) -> Unit) {
        rightEditorCallback = callback
        rightEditor.setOnContentChangeCallback(callback)
    }

    fun getLeftContent(): String = leftEditor.getText().trim()
    fun getRightContent(): String = rightEditor.getText().trim()

    interface EditorActionHandler {
        fun loadFromFile(editor: JsonEditor)
        fun pasteFromClipboard(editor: JsonEditor)
    }
}