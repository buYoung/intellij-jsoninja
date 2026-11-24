package com.livteam.jsoninja.ui.component.convertType

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.ui.component.convertType.highlighting.TypeCodeHighlighterResolver
import com.livteam.jsoninja.ui.component.editor.EditorTextFieldFactory
import com.livteam.jsoninja.ui.component.editor.setEditorTextAndRefreshCodeFolding
import java.awt.BorderLayout
import javax.swing.JComponent

class CodeInputPanel(
    private val project: Project,
) : JBPanel<CodeInputPanel>(BorderLayout()), Disposable {
    private var editorField: EditorTextField? = null
    private var currentFileExtension: String = "txt"
    private var currentPlaceholderText: String = ""
    private var onTextChanged: ((String) -> Unit)? = null
    @Volatile
    private var isDisposed = false

    init {
        border = JBUI.Borders.empty()
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { refreshEditorColors() })
        createInitialEditor()
    }

    fun updateLanguage(
        fileExtension: String,
        placeholderText: String,
    ) {
        if (currentFileExtension == fileExtension && currentPlaceholderText == placeholderText) {
            return
        }
        currentFileExtension = fileExtension
        currentPlaceholderText = placeholderText

        val field = editorField ?: return
        field.setPlaceholder(placeholderText)
        applyCurrentLanguageAppearance(field)
    }

    fun setText(text: String) {
        setEditorTextAndRefreshCodeFolding(project, editorField, text)
    }

    fun getText(): String = editorField?.text.orEmpty()

    fun getPreferredFocusedComponent(): JComponent = editorField ?: this

    fun setOnTextChanged(callback: (String) -> Unit) {
        onTextChanged = callback
    }

    private fun createInitialEditor() {
        editorField = EditorTextFieldFactory.createCodeField(
            project = project,
            fileExtension = currentFileExtension,
            initialText = "",
            placeholderText = currentPlaceholderText,
            shouldApplyEditorColors = true,
            shouldApplyHighlighter = true,
            highlighterProvider = { owner, _, scheme ->
                TypeCodeHighlighterResolver.create(
                    requestedExtension = currentFileExtension,
                    project = owner,
                    fileType = resolveCodeFileType(currentFileExtension),
                    scheme = scheme,
                )
            },
            shouldShowHorizontalScrollbar = true,
            shouldShowVerticalScrollbar = true,
            configureEditorSettings = {
                isLineNumbersShown = true
                isWhitespacesShown = false
                isUseSoftWraps = true
                isRightMarginShown = false
                isIndentGuidesShown = true
                isCaretRowShown = true
            },
        ).also { createdEditorField ->
            createdEditorField.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    onTextChanged?.invoke(createdEditorField.text)
                }
            })
        }

        editorField?.let { add(it, BorderLayout.CENTER) }
        revalidate()
        repaint()
    }

    override fun dispose() {
        isDisposed = true
        (editorField as? Disposable)?.let(Disposer::dispose)
        editorField = null
    }

    private fun refreshEditorColors() {
        if (isDisposed || project.isDisposed) return
        val field = editorField ?: return
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            applyCurrentLanguageAppearance(field)
        } else {
            app.invokeLater({
                if (!isDisposed && !project.isDisposed && field === editorField) {
                    applyCurrentLanguageAppearance(field)
                }
            }, ModalityState.stateForComponent(this))
        }
    }

    /**
     * Updates syntax highlighting and colors on the existing EditorEx in place so switching
     * languages preserves caret position, selection, scroll state, and Undo history.
     */
    private fun applyCurrentLanguageAppearance(field: EditorTextField) {
        if (isDisposed || project.isDisposed) return
        val editor = field.editor as? EditorEx ?: return
        if (editor.isDisposed) return
        val scheme = EditorColorsManager.getInstance().globalScheme
        editor.colorsScheme = editor.createBoundColorSchemeDelegate(scheme)
        editor.backgroundColor = scheme.defaultBackground
        editor.highlighter = TypeCodeHighlighterResolver.create(
            requestedExtension = currentFileExtension,
            project = project,
            fileType = resolveCodeFileType(currentFileExtension),
            scheme = editor.colorsScheme,
        )
        editor.component.repaint()
    }

    private fun resolveCodeFileType(fileExtension: String): FileType {
        val resolvedFileType = FileTypeManager.getInstance().getFileTypeByExtension(fileExtension)
        return if (resolvedFileType is UnknownFileType) PlainTextFileType.INSTANCE else resolvedFileType
    }
}
