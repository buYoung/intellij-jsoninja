package com.livteam.jsoninja.ui.component.editor

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.PopupHandler
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.actions.CopyJsonQueryAction
import com.livteam.jsoninja.ui.component.model.JsonQueryModel
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * JSON 편집을 위한 커스텀 에디터 뷰
 */
class JsonEditorView(
    private val project: Project,
    private val model: JsonQueryModel,
    private val fileExtension: String? = null,
    private val documentCreator: JsonDocumentCreator = SimpleJsonDocumentCreator()
) : JPanel(), Disposable {

    companion object {
        private const val EMPTY_TEXT = ""
        private var PLACEHOLDER_TEXT = LocalizationBundle.message("enterJsonHere")
    }

    val editor: EditorTextField
    val presenter: JsonEditorPresenter

    init {
        editor = createJsonEditor()
        presenter = JsonEditorPresenter(project, this, model)

        initializeUI()
        presenter.setupContentChangeListener()
        setupMouseListener()
    }

    private fun setupMouseListener() {
        EditorFactory.getInstance().eventMulticaster.addEditorMouseMotionListener(
            JsonEditorTooltipListener(project, this),
            this
        )
    }

    private fun createJsonEditor(): EditorTextField {
        val document = documentCreator.createDocument(EMPTY_TEXT, project, fileExtension)
        val extensionToUse = fileExtension ?: "json5"
        var fileType = FileTypeManager.getInstance().getFileTypeByExtension(extensionToUse)

        if (fileType is UnknownFileType) {
            fileType = JsonLanguage.INSTANCE.associatedFileType ?: JsonFileType.INSTANCE
        }

        return EditorTextField(document, project, fileType, false, false).also { editorField ->
            configureEditor(editorField, fileType)
        }
    }

    private fun configureEditor(editorField: EditorTextField, fileType: com.intellij.openapi.fileTypes.FileType) {
        editorField.addSettingsProvider { editor ->
            editor.settings.applyEditorSettings()
            applyEditorAppearance(editor, fileType)
            applyEditorScrollbars(editor)
            installEditorContextMenu(editor)
        }
        editorField.setPlaceholder(PLACEHOLDER_TEXT)
        editorField.putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)
    }

    private fun applyEditorAppearance(
        editor: EditorEx,
        fileType: com.intellij.openapi.fileTypes.FileType
    ) {
        val globalScheme = EditorColorsManager.getInstance().globalScheme
        editor.colorsScheme = globalScheme
        editor.backgroundColor = globalScheme.defaultBackground
        editor.highlighter = HighlighterFactory.createHighlighter(project, fileType)
        editor.isEmbeddedIntoDialogWrapper = true
    }

    private fun applyEditorScrollbars(editor: EditorEx) {
        editor.setHorizontalScrollbarVisible(true)
        editor.setVerticalScrollbarVisible(true)
    }

    private fun installEditorContextMenu(editor: EditorEx) {
        val actionManager = ActionManager.getInstance()
        val group = DefaultActionGroup()

        val copyAction = actionManager.getAction(IdeActions.ACTION_COPY)
        if (copyAction != null) group.add(copyAction)

        val pasteAction = actionManager.getAction(IdeActions.ACTION_PASTE)
        if (pasteAction != null) group.add(pasteAction)

        val copyJsonQueryAction = CopyJsonQueryAction()
        copyJsonQueryAction.templatePresentation.text = LocalizationBundle.message("action.copy.json.query")
        copyJsonQueryAction.templatePresentation.description =
            LocalizationBundle.message("action.copy.json.query.description")

        if (group.childrenCount > 0) group.addSeparator()
        group.add(copyJsonQueryAction)

        if (group.childrenCount > 0) {
            PopupHandler.installPopupMenu(
                editor.contentComponent,
                group,
                "com.livteam.jsoninja.action.group.EditorPopup"
            )
        }
    }

    private fun EditorSettings.applyEditorSettings() {
        isLineNumbersShown = true
        isWhitespacesShown = true
        isCaretRowShown = true
        isRightMarginShown = true
        isUseSoftWraps = true
        isIndentGuidesShown = true
        isFoldingOutlineShown = true
    }

    private fun initializeUI() {
        layout = BorderLayout()
        add(editor, BorderLayout.CENTER)
    }

    fun setText(text: String) = presenter.setText(text)

    fun getText(): String = editor.text

    fun setOnContentChangeCallback(callback: (String) -> Unit) = presenter.setOnContentChangeCallback(callback)

    override fun dispose() {
        (editor as? Disposable)?.let { Disposer.dispose(it) }
        removeAll()
    }
}
