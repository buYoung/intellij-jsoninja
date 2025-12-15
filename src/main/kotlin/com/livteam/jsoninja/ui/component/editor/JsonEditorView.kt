package com.livteam.jsoninja.ui.component.editor

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
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
        presenter = JsonEditorPresenter(this, model)
        
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

        return EditorTextField(document, project, fileType, false, false).apply {
            addSettingsProvider { editor ->
                editor.settings.applyEditorSettings()
                editor.colorsScheme = EditorColorsManager.getInstance().globalScheme
                editor.backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground
                editor.highlighter = HighlighterFactory.createHighlighter(project, fileType)

                editor.isEmbeddedIntoDialogWrapper = true
                editor.setHorizontalScrollbarVisible(true)
                editor.setVerticalScrollbarVisible(true)

                // Install context menu handler
                val actionManager = ActionManager.getInstance()
                val group = DefaultActionGroup()

                val copyAction = actionManager.getAction(IdeActions.ACTION_COPY)
                if (copyAction != null) group.add(copyAction)

                val pasteAction = actionManager.getAction(IdeActions.ACTION_PASTE)
                if (pasteAction != null) group.add(pasteAction)

                val copyJsonQueryAction = CopyJsonQueryAction()
                copyJsonQueryAction.templatePresentation.text = LocalizationBundle.message("action.copy.json.query")
                copyJsonQueryAction.templatePresentation.description = LocalizationBundle.message("action.copy.json.query.description")

                if (group.childrenCount > 0) group.addSeparator()
                group.add(copyJsonQueryAction)

                if (group.childrenCount > 0) {
                    PopupHandler.installPopupMenu(editor.contentComponent, group, "com.livteam.jsoninja.action.group.EditorPopup")
                }
            }
            setPlaceholder(PLACEHOLDER_TEXT)
            putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)
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

    fun setText(text: String) {
        presenter.isSettingText = true
        WriteCommandAction.runWriteCommandAction(project) {
            editor.text = text
            presenter.isSettingText = false
        }
    }

    fun getText(): String = editor.text

    fun setOriginalJson(json: String) = presenter.setOriginalJson(json)
    fun getOriginalJson(): String = presenter.getOriginalJson()
    fun setOnContentChangeCallback(callback: (String) -> Unit) = presenter.setOnContentChangeCallback(callback)

    override fun dispose() {
        removeAll()
    }
}
