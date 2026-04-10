package com.livteam.jsoninja.ui.component.editor

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.livteam.jsoninja.services.JsoninjaCoroutineService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    project.service<JsoninjaCoroutineService>().coroutineScope.launch(Dispatchers.EDT) {
        if (project.isDisposed || editor.isDisposed) {
            return@launch
        }

        readActionBlocking {
            if (project.isDisposed || editor.isDisposed) {
                return@readActionBlocking
            }

            CodeFoldingManager.getInstance(project).updateFoldRegions(editor)
        }
    }
}
