package com.livteam.jsoninja.ui.component.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.EditorTextField
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel

/**
 * JSON 편집을 위한 커스텀 에디터 뷰
 */
class JsonEditorView(
    private val project: Project,
    fileExtension: String? = null,
    documentCreator: JsonDocumentCreator = SimpleJsonDocumentCreator(),
    isEmbeddedInDialog: Boolean = false,
) : JPanel(), Disposable {

    private enum class EditorDisplayMode {
        TEXT,
        TREE
    }

    companion object {
        private const val TEXT_CARD = "TEXT_CARD"
        private const val TREE_CARD = "TREE_CARD"
        private const val NOTIFICATION_GROUP_ID = "JSONinja"
    }

    val presenter: JsonEditorTextPresenter
    val editor: EditorTextField
        get() = jsonEditorTextView.editor

    private val contentContainer = JPanel(CardLayout())
    private val jsonEditorTextView = JsonEditorTextView(project, fileExtension, documentCreator, isEmbeddedInDialog)
    private val jsonEditorTreeView = JsonEditorTreeView(project)
    private val treePresenter = JsonEditorTreePresenter(project, jsonEditorTreeView)
    private var currentDisplayMode = EditorDisplayMode.TEXT
    private var onDisplayModeChangedListener: ((Boolean) -> Unit)? = null
    @Volatile
    private var isDisposed = false
    private val treeDocumentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (currentDisplayMode == EditorDisplayMode.TREE) refreshVisibleTree()
        }
    }

    init {
        presenter = JsonEditorTextPresenter(project, jsonEditorTextView)

        initializeUI()
        jsonEditorTreeView.setOnSwitchToTextRequested { line, column ->
            switchToTextMode(line = line, column = column)
        }
        subscribeThemeChanges()
        presenter.setupContentChangeListener()
        editor.addDocumentListener(treeDocumentListener)
        setupMouseListener()
    }

    private fun subscribeThemeChanges() {
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { scheduleThemeRefresh() })
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { scheduleThemeRefresh() })
    }

    private fun scheduleThemeRefresh() {
        if (isDisposed || project.isDisposed) return
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            refreshThemeColors()
        } else {
            app.invokeLater({
                if (!isDisposed && !project.isDisposed) {
                    refreshThemeColors()
                }
            }, ModalityState.stateForComponent(this))
        }
    }

    private fun refreshThemeColors() {
        val backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground
        background = backgroundColor
        contentContainer.background = backgroundColor
        revalidate()
        repaint()
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
        add(contentContainer, BorderLayout.CENTER)
    }

    fun switchToTextMode(line: Int? = null, column: Int? = null) {
        if (isDisposed || project.isDisposed) return
        treePresenter.cancelRefresh()
        currentDisplayMode = EditorDisplayMode.TEXT
        (contentContainer.layout as CardLayout).show(contentContainer, TEXT_CARD)
        onDisplayModeChangedListener?.invoke(false)

        if (line != null && line > 0) {
            val activeEditor = editor.editor
            if (activeEditor != null && !activeEditor.isDisposed) {
                val document = activeEditor.document
                val targetLine = (line - 1).coerceIn(0, maxOf(0, document.lineCount - 1))
                val lineLength = if (document.lineCount > 0) {
                    document.getLineEndOffset(targetLine) - document.getLineStartOffset(targetLine)
                } else {
                    0
                }
                val targetColumn = ((column ?: 1) - 1).coerceIn(0, lineLength)
                activeEditor.caretModel.moveToLogicalPosition(LogicalPosition(targetLine, targetColumn))
                activeEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            }
        }

        focusContent()
    }

    fun switchToTreeMode() {
        if (isDisposed || project.isDisposed) return
        currentDisplayMode = EditorDisplayMode.TREE
        (contentContainer.layout as CardLayout).show(contentContainer, TREE_CARD)
        onDisplayModeChangedListener?.invoke(true)
        refreshVisibleTree()
        focusContent()
    }

    fun toggleDisplayMode() {
        if (isTextMode()) {
            switchToTreeMode()
        } else {
            switchToTextMode()
        }
    }

    fun setOnDisplayModeChangedListener(listener: (Boolean) -> Unit) {
        onDisplayModeChangedListener = listener
    }

    fun expandAllTreeNodes() {
        if (isTreeMode()) {
            jsonEditorTreeView.expandAllBounded()
        }
    }

    fun collapseAllTreeNodes() {
        if (isTreeMode()) {
            jsonEditorTreeView.collapseAll()
        }
    }

    private fun refreshVisibleTree() {
        val document = editor.document
        val modificationStamp = document.modificationStamp
        treePresenter.refreshTreeFromJson(document.text) {
            currentDisplayMode == EditorDisplayMode.TREE && editor.document === document &&
                document.modificationStamp == modificationStamp
        }
    }

    fun isTextMode(): Boolean = currentDisplayMode == EditorDisplayMode.TEXT

    fun isTreeMode(): Boolean = currentDisplayMode == EditorDisplayMode.TREE

    fun focusContent() {
        if (isDisposed || project.isDisposed) return
        val focusTarget = if (currentDisplayMode == EditorDisplayMode.TREE) {
            jsonEditorTreeView.focusTarget
        } else {
            editor.editor?.contentComponent ?: editor
        }
        IdeFocusManager.getInstance(project).requestFocus(focusTarget, true)
    }

    fun showErrorHint(message: String) {
        if (isDisposed || project.isDisposed) return
        val activeEditor = editor.editor
        if (isTextMode() && activeEditor != null && !activeEditor.isDisposed && activeEditor.contentComponent.isShowing) {
            HintManager.getInstance().showErrorHint(activeEditor, "JSONinja: $message")
            return
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?.createNotification("JSONinja", message, NotificationType.ERROR)
            ?.notify(project)
    }

    fun setText(text: String) = presenter.setText(text)

    fun getText(): String = presenter.getText()

    fun setOnContentChangeCallback(callback: (String) -> Unit) = presenter.setOnContentChangeCallback(callback)

    override fun dispose() {
        isDisposed = true
        editor.removeDocumentListener(treeDocumentListener)
        treePresenter.dispose()
        Disposer.dispose(jsonEditorTextView)
        Disposer.dispose(jsonEditorTreeView)
        removeAll()
    }
}
