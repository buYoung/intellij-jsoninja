package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager

@Service(Service.Level.APP)
internal class JsoninjaFoldingActionGuardService : Disposable {
    private val actionManager = EditorActionManager.getInstance()
    private val actionId = IdeActions.ACTION_EXPAND_COLLAPSE_TOGGLE_REGION
    private val originalHandler = actionManager.getActionHandler(actionId)
    private val guardedHandler = JsoninjaExpandCollapseToggleHandler(originalHandler)

    init {
        actionManager.setActionHandler(actionId, guardedHandler)
    }

    override fun dispose() {
        if (actionManager.getActionHandler(actionId) === guardedHandler) {
            actionManager.setActionHandler(actionId, originalHandler)
        }
    }
}

private class JsoninjaExpandCollapseToggleHandler(
    private val originalHandler: EditorActionHandler
) : EditorActionHandler(originalHandler.runForAllCarets()) {
    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
        if (editor.isJsoninjaEditor() && !hasToggleableFoldRegion(editor, caret)) {
            return false
        }

        return originalHandler.isEnabled(editor, caret, dataContext)
    }

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (editor.isJsoninjaEditor() && !hasToggleableFoldRegion(editor, caret)) {
            return
        }

        originalHandler.execute(editor, caret, dataContext)
    }

    override fun executeInCommand(editor: Editor, dataContext: DataContext): Boolean =
        originalHandler.executeInCommand(editor, dataContext)

    override fun getCommandGroupId(editor: Editor): DocCommandGroupId? =
        originalHandler.getCommandGroupId(editor)
}

private fun Editor.isJsoninjaEditor(): Boolean =
    document.getUserData(JsonDocumentFactory.JSONINJA_EDITOR_KEY) == true

internal fun hasToggleableFoldRegion(editor: Editor, caret: Caret? = null): Boolean {
    val activeCaret = caret ?: editor.caretModel.primaryCaret
    val line = activeCaret.logicalPosition.line

    if (findFoldRegionStartingAtLine(editor, line)) {
        return true
    }

    return editor.foldingModel.allFoldRegions.any { foldRegion ->
        activeCaret.offset in foldRegion.startOffset..foldRegion.endOffset
    }
}

private fun findFoldRegionStartingAtLine(editor: Editor, line: Int): Boolean {
    if (line < 0 || line >= editor.document.lineCount) {
        return false
    }

    var hasRegionStartingAtLine = false
    for (foldRegion in editor.foldingModel.allFoldRegions) {
        if (!foldRegion.isValid) {
            continue
        }

        if (foldRegion.document.getLineNumber(foldRegion.startOffset) != line) {
            continue
        }

        if (hasRegionStartingAtLine) {
            return false
        }

        hasRegionStartingAtLine = true
    }

    return hasRegionStartingAtLine
}
