package com.livteam.jsoninja.ui.component.editor

import com.intellij.json.JsonLanguage
import com.intellij.lang.folding.LanguageFolding
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.EditorTextField
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


internal class FoldingAwareEditorTextField(
    document: Document,
    project: Project?,
    fileType: FileType,
    isViewer: Boolean = false,
    oneLineMode: Boolean = false,
) : EditorTextField(document, project, fileType, isViewer, oneLineMode), Disposable {
    private val editorProject = project
    private var foldingCoroutineScope: CoroutineScope? = null
    private var foldingRefreshJob: Job? = null
    private var cleanupRegistrationEditor: Editor? = null

    override fun onEditorAdded(editor: Editor) {
        super.onEditorAdded(editor)
        if (cleanupRegistrationEditor != null && cleanupRegistrationEditor !== editor) {
            clearRefreshInfrastructure()
        }
        ensureRefreshInfrastructure(editor)
        scheduleFoldRegionsRefresh(editor, debounceDelayMs = 0)
    }

    override fun documentChanged(event: DocumentEvent) {
        super.documentChanged(event)
        val currentEditor = editor ?: return
        scheduleFoldRegionsRefresh(currentEditor, debounceDelayMs = FOLDING_REFRESH_DEBOUNCE_DELAY_MS)
    }

    private fun ensureRefreshInfrastructure(editor: Editor) {
        val project = editorProject ?: return

        val currentScope = foldingCoroutineScope
        if (currentScope == null || currentScope.coroutineContext[Job]?.isCancelled == true) {
            foldingCoroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
        }

        cleanupRegistrationEditor = editor
    }

    private fun scheduleFoldRegionsRefresh(
        editor: Editor,
        debounceDelayMs: Int,
    ) {
        val project = editorProject ?: return
        if (project.isDisposed || editor.isDisposed) {
            return
        }

        ensureRefreshInfrastructure(editor)
        val coroutineScope = foldingCoroutineScope ?: return
        val document = editor.document
        val expectedModificationStamp = document.modificationStamp
        // Capture component modality (write-safe for commitDocument, while still active inside modal dialogs).
        val modalityState = ModalityState.stateForComponent(this)

        foldingRefreshJob?.cancel()
        foldingRefreshJob = coroutineScope.launch {
            if (debounceDelayMs > 0) {
                delay(debounceDelayMs.toLong())
            }
            refreshFoldRegions(project, editor, document, expectedModificationStamp, modalityState)
        }
    }

    private suspend fun refreshFoldRegions(
        project: Project,
        editor: Editor,
        document: Document,
        expectedModificationStamp: Long,
        modalityState: ModalityState,
    ) {
        val edtModalityContext = Dispatchers.EDT + modalityState.asContextElement()
        val canProceed = withContext(edtModalityContext) {
            if (project.isDisposed || editor.isDisposed) {
                return@withContext false
            }
            if (document.modificationStamp != expectedModificationStamp) {
                return@withContext false
            }

            PsiDocumentManager.getInstance(project).commitDocument(document)
            true
        }
        if (!canProceed) {
            return
        }

        val foldRegions = try {
            readAction {
                if (project.isDisposed || editor.isDisposed) {
                    emptyList()
                } else if (document.modificationStamp != expectedModificationStamp) {
                    emptyList()
                } else {
                    collectFoldRegions(project, document)
                }
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (_: ProcessCanceledException) {
            return
        } catch (throwable: Throwable) {
            LOG.warn("Failed to refresh JSONinja folding regions", throwable)
            emptyList()
        }

        withContext(edtModalityContext) {
            if (project.isDisposed || editor.isDisposed) {
                return@withContext
            }
            if (document.modificationStamp != expectedModificationStamp) {
                return@withContext
            }

            applyFoldRegions(editor, foldRegions)
        }
    }

    private fun clearRefreshInfrastructure() {
        foldingRefreshJob?.cancel()
        foldingCoroutineScope?.cancel()
        foldingRefreshJob = null
        foldingCoroutineScope = null
        cleanupRegistrationEditor = null
    }

    override fun dispose() {
        clearRefreshInfrastructure()
    }
}

private val LOG = logger<FoldingAwareEditorTextField>()
private val JSONINJA_FOLD_REGION_KEY = Key.create<Boolean>("JSONINJA_FOLD_REGION_KEY")
private const val FOLDING_REFRESH_DEBOUNCE_DELAY_MS = 150

internal data class ManualFoldRegion(
    val startOffset: Int,
    val endOffset: Int,
    val placeholderText: String,
    val shouldCollapse: Boolean,
    val isGutterMarkEnabledForSingleLine: Boolean,
)

internal fun collectFoldRegions(
    project: Project,
    document: Document,
): List<ManualFoldRegion> {
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val psiFile = psiDocumentManager.getPsiFile(document) ?: return emptyList()
    if (!psiFile.isValid) {
        return emptyList()
    }

    val foldingBuilder = findFoldingBuilder(psiFile) ?: return emptyList()
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

private fun findFoldingBuilder(psiFile: PsiFile) =
    LanguageFolding.INSTANCE.forLanguage(psiFile.language)
        ?: psiFile.language.baseLanguage?.let { LanguageFolding.INSTANCE.forLanguage(it) }
        ?: if (psiFile.language is JsonLanguage) {
            LanguageFolding.INSTANCE.forLanguage(JsonLanguage.INSTANCE)
        } else {
            null
        }

internal fun applyFoldRegions(
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
