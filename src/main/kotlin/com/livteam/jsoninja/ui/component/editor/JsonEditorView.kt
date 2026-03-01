package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLayeredPane
import javax.swing.JPanel

/**
 * JSON 편집을 위한 커스텀 에디터 뷰
 */
class JsonEditorView(
    private val project: Project,
    fileExtension: String? = null,
    documentCreator: JsonDocumentCreator = SimpleJsonDocumentCreator()
) : JPanel(), Disposable {

    private enum class EditorDisplayMode {
        TEXT,
        TREE
    }

    companion object {
        private const val TEXT_CARD = "TEXT_CARD"
        private const val TREE_CARD = "TREE_CARD"
    }

    val presenter: JsonEditorTextPresenter
    val editor: EditorTextField
        get() = jsonEditorTextView.editor

    private lateinit var floatingTogglePanel: JPanel
    private val contentContainer = JPanel(CardLayout())
    private val jsonEditorTextView = JsonEditorTextView(project, fileExtension, documentCreator)
    private val jsonEditorTreeView = JsonEditorTreeView()
    private lateinit var textModeLabel: JBLabel
    private lateinit var treeModeLabel: JBLabel
    private val treePresenter = JsonEditorTreePresenter(jsonEditorTreeView)
    private var currentDisplayMode = EditorDisplayMode.TEXT

    init {
        presenter = JsonEditorTextPresenter(project, jsonEditorTextView)

        initializeUI()
        presenter.setupContentChangeListener()
        setupMouseListener()
    }

    private fun setupMouseListener() {
        EditorFactory.getInstance().eventMulticaster.addEditorMouseMotionListener(
            JsonEditorTooltipListener(project, this, this),
            this
        )
    }

    private fun initializeUI() {
        layout = BorderLayout()
        val backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground
        background = backgroundColor

        contentContainer.background = backgroundColor
        contentContainer.add(jsonEditorTextView, TEXT_CARD)
        contentContainer.add(jsonEditorTreeView, TREE_CARD)

        floatingTogglePanel = createFloatingTogglePanel()
        updateToggleLabelStyles()

        val layeredPane = object : JLayeredPane() {
            override fun doLayout() {
                super.doLayout()
                contentContainer.setBounds(0, 0, width, height)

                val floatingPanelSize = floatingTogglePanel.preferredSize
                val paddingRight = JBUI.scale(24)
                val paddingBottom = JBUI.scale(12)
                floatingTogglePanel.setBounds(
                    width - floatingPanelSize.width - paddingRight,
                    height - floatingPanelSize.height - paddingBottom,
                    floatingPanelSize.width,
                    floatingPanelSize.height
                )
            }

            override fun getPreferredSize(): java.awt.Dimension {
                return contentContainer.preferredSize
            }
        }

        layeredPane.setLayer(contentContainer, JLayeredPane.DEFAULT_LAYER)
        layeredPane.add(contentContainer)
        layeredPane.setLayer(floatingTogglePanel, JLayeredPane.PALETTE_LAYER)
        layeredPane.add(floatingTogglePanel)

        add(layeredPane, BorderLayout.CENTER)
    }

    private fun createFloatingTogglePanel(): JPanel {
        val editorBackground = EditorColorsManager.getInstance().globalScheme.defaultBackground
        val scheme = EditorColorsManager.getInstance().globalScheme
        val dividerColor = scheme.getColor(com.intellij.openapi.editor.colors.EditorColors.LINE_NUMBERS_COLOR)
            ?: UIUtil.getBoundsColor()

        val togglePanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.CENTER, 4, 2))
        togglePanel.border = JBUI.Borders.compound(
            RoundedLineBorder(dividerColor, 10),
            JBUI.Borders.empty(4, 8)
        )
        togglePanel.background = editorBackground
        togglePanel.isOpaque = true

        val textLabel = JBLabel("TEXT").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = UIUtil.getLabelForeground()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        textModeLabel = textLabel

        val treeLabel = JBLabel("TREE").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = UIUtil.getContextHelpForeground()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        treeModeLabel = treeLabel

        val separator = JBLabel("|").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            foreground = dividerColor
            border = JBUI.Borders.empty(0, 4)
        }

        togglePanel.add(textLabel)
        togglePanel.add(separator)
        togglePanel.add(treeLabel)

        textLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent?) {
                switchToTextMode()
            }
        })

        treeLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent?) {
                switchToTreeMode()
            }
        })

        return togglePanel
    }

    private fun switchToTextMode() {
        currentDisplayMode = EditorDisplayMode.TEXT
        (contentContainer.layout as CardLayout).show(contentContainer, TEXT_CARD)
        updateToggleLabelStyles()
    }

    private fun switchToTreeMode() {
        treePresenter.refreshTreeFromJson(getText())
        currentDisplayMode = EditorDisplayMode.TREE
        (contentContainer.layout as CardLayout).show(contentContainer, TREE_CARD)
        updateToggleLabelStyles()
    }

    private fun updateToggleLabelStyles() {
        val isTextMode = currentDisplayMode == EditorDisplayMode.TEXT
        textModeLabel.font = textModeLabel.font.deriveFont(if (isTextMode) Font.BOLD else Font.PLAIN, 11f)
        treeModeLabel.font = treeModeLabel.font.deriveFont(if (isTextMode) Font.PLAIN else Font.BOLD, 11f)
        textModeLabel.foreground = if (isTextMode) UIUtil.getLabelForeground() else UIUtil.getContextHelpForeground()
        treeModeLabel.foreground = if (isTextMode) UIUtil.getContextHelpForeground() else UIUtil.getLabelForeground()
    }

    fun setText(text: String) = presenter.setText(text)

    fun getText(): String = presenter.getText()

    fun setOnContentChangeCallback(callback: (String) -> Unit) = presenter.setOnContentChangeCallback(callback)

    override fun dispose() {
        Disposer.dispose(jsonEditorTextView)
        Disposer.dispose(jsonEditorTreeView)
        removeAll()
    }
}
