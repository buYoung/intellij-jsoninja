package com.livteam.jsoninja.ui.component.editor

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField

internal class FoldingAwareEditorTextField(
    document: Document,
    project: Project?,
    fileType: FileType,
    isViewer: Boolean = false,
    oneLineMode: Boolean = false,
) : EditorTextField(document, project, fileType, isViewer, oneLineMode) {
    private val editorProject = project

    override fun onEditorAdded(editor: Editor) {
        super.onEditorAdded(editor)
        refreshFoldRegions(editorProject, editor)
    }
}

internal fun refreshFoldRegionsIfAvailable(
    project: Project?,
    editorTextField: EditorTextField?,
) {
    val editor = editorTextField?.editor ?: return
    refreshFoldRegions(project, editor)
}

private fun refreshFoldRegions(
    project: Project?,
    editor: Editor,
) {
    if (project == null || project.isDisposed || editor.isDisposed) {
        return
    }

    ApplicationManager.getApplication().invokeLater(
        {
            if (project.isDisposed || editor.isDisposed) {
                return@invokeLater
            }

            ReadAction.run<RuntimeException> {
                if (project.isDisposed || editor.isDisposed) {
                    return@run
                }

                CodeFoldingManager.getInstance(project).updateFoldRegions(editor)
            }
        },
        ModalityState.any(),
    )
}
