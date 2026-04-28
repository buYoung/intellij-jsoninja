package com.livteam.jsoninja.ui.component.editor

import com.intellij.lang.folding.LanguageFolding
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorTextField
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    override fun documentChanged(event: DocumentEvent) {
        super.documentChanged(event)
        editor?.let { refreshFoldRegions(editorProject, it) }
    }
}

private val JSONINJA_FOLD_REGION_KEY = Key.create<Boolean>("JSONINJA_FOLD_REGION_KEY")

private data class ManualFoldRegion(
    val startOffset: Int,
    val endOffset: Int,
    val placeholderText: String,
    val shouldCollapse: Boolean,
    val isGutterMarkEnabledForSingleLine: Boolean,
)

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

    val application = ApplicationManager.getApplication()
    project.service<JsoninjaCoroutineScopeService>().launch {
        withContext(Dispatchers.EDT) {
            if (project.isDisposed || editor.isDisposed) {
                return@withContext
            }

            val foldRegions = application.runReadAction<List<ManualFoldRegion>> {
                if (project.isDisposed || editor.isDisposed) {
                    return@runReadAction emptyList()
                }

                collectFoldRegions(project, editor)
            }

            if (project.isDisposed || editor.isDisposed) {
                return@withContext
            }

            applyFoldRegions(editor, foldRegions)
        }
    }
}

private fun collectFoldRegions(
    project: Project,
    editor: Editor,
): List<ManualFoldRegion> {
    val document = editor.document
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    psiDocumentManager.commitDocument(document)

    val psiFile = psiDocumentManager.getPsiFile(document) ?: return emptyList()
    if (!psiFile.isValid) {
        return emptyList()
    }

    val foldingBuilder = LanguageFolding.INSTANCE.forLanguage(psiFile.language) ?: return emptyList()
    return LanguageFolding.buildFoldingDescriptors(foldingBuilder, psiFile, document, false)
        .mapNotNull { descriptor ->
            val range = descriptor.range
            if (range.isEmpty || range.endOffset > document.textLength) {
                return@mapNotNull null
            }

            ManualFoldRegion(
                startOffset = range.startOffset,
                endOffset = range.endOffset,
                placeholderText = descriptor.placeholderText
                    ?: foldingBuilder.getPlaceholderText(descriptor.element)
                    ?: "...",
                shouldCollapse = descriptor.isCollapsedByDefault
                    ?: foldingBuilder.isCollapsedByDefault(descriptor.element),
                isGutterMarkEnabledForSingleLine = descriptor.isGutterMarkEnabledForSingleLine,
            )
        }
}

private fun applyFoldRegions(
    editor: Editor,
    foldRegions: List<ManualFoldRegion>,
) {
    val foldingModel = editor.foldingModel
    val expandedStatesByRange = foldingModel.allFoldRegions
        .filter { it.getUserData(JSONINJA_FOLD_REGION_KEY) == true }
        .associate { Pair(it.startOffset, it.endOffset) to it.isExpanded }

    foldingModel.runBatchFoldingOperation {
        foldingModel.allFoldRegions
            .filter { it.getUserData(JSONINJA_FOLD_REGION_KEY) == true }
            .forEach(foldingModel::removeFoldRegion)

        for (foldRegion in foldRegions) {
            val range = Pair(foldRegion.startOffset, foldRegion.endOffset)
            val region = foldingModel.addFoldRegion(
                foldRegion.startOffset,
                foldRegion.endOffset,
                foldRegion.placeholderText,
            ) ?: continue

            region.putUserData(JSONINJA_FOLD_REGION_KEY, true)
            region.isGutterMarkEnabledForSingleLine = foldRegion.isGutterMarkEnabledForSingleLine
            region.isExpanded = expandedStatesByRange[range] ?: !foldRegion.shouldCollapse
        }
    }
}
