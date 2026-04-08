package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.PopupHandler
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.actions.CopyJsonQueryAction
import com.livteam.jsoninja.ui.onboarding.OnboardingTutorialTargetIds
import java.awt.BorderLayout
import javax.swing.JPanel

class JsonEditorTextView(
    private val project: Project,
    private val fileExtension: String? = null,
    private val documentCreator: JsonDocumentCreator = SimpleJsonDocumentCreator()
) : JPanel(), Disposable {

    companion object {
        private const val EMPTY_TEXT = ""
        private var PLACEHOLDER_TEXT = LocalizationBundle.message("enterJsonHere")
    }

    val editor: EditorTextField

    init {
        layout = BorderLayout()
        editor = createJsonEditor()
        add(editor, BorderLayout.CENTER)
    }

    private fun createJsonEditor(): EditorTextField {
        return EditorTextFieldFactory.createJsonField(
            project = project,
            fileExtension = fileExtension,
            initialText = EMPTY_TEXT,
            documentCreator = documentCreator,
            placeholderText = PLACEHOLDER_TEXT,
            shouldApplyEditorColors = true,
            shouldApplyHighlighter = true,
            shouldShowHorizontalScrollbar = true,
            shouldShowVerticalScrollbar = true,
            configureEditorSettings = {
                applyEditorSettings()
            },
            customizeEditor = { _ ->
                contentComponent.name = OnboardingTutorialTargetIds.JSON_EDITOR
                installEditorContextMenu(this)
            },
        )
    }

    private fun installEditorContextMenu(editor: EditorEx) {
        val actionManager = ActionManager.getInstance()
        val group = DefaultActionGroup()

        val copyAction = actionManager.getAction(IdeActions.ACTION_COPY)
        if (copyAction != null) {
            group.add(copyAction)
        }

        val pasteAction = actionManager.getAction(IdeActions.ACTION_PASTE)
        if (pasteAction != null) {
            group.add(pasteAction)
        }

        val copyJsonQueryAction = CopyJsonQueryAction()
        copyJsonQueryAction.templatePresentation.text = LocalizationBundle.message("action.copy.json.query")
        copyJsonQueryAction.templatePresentation.description =
            LocalizationBundle.message("action.copy.json.query.description")

        if (group.childrenCount > 0) {
            group.addSeparator()
        }
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
    }

    fun getText(): String = editor.text

    fun setText(text: String) {
        setEditorTextAndRefreshCodeFolding(project, editor, text)
    }

    override fun dispose() {
        (editor as? Disposable)?.let { Disposer.dispose(it) }
        removeAll()
    }
}
