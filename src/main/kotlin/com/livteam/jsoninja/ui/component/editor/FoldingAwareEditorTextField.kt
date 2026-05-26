package com.livteam.jsoninja.ui.component.editor

import com.intellij.lang.folding.LanguageFolding
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorTextField
import com.intellij.util.Alarm
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


internal class FoldingAwareEditorTextField(
    document: Document,
    project: Project?,
    fileType: FileType,
    isViewer: Boolean = false,
    oneLineMode: Boolean = false,
) : EditorTextField(document, project, fileType, isViewer, oneLineMode) {
    private val editorProject = project
    private var foldingCoroutineScope: CoroutineScope? = null
    private var foldingRefreshAlarm: Alarm? = null
    private var foldingRefreshJob: Job? = null
    private var cleanupRegistrationEditor: Editor? = null

    override fun onEditorAdded(editor: Editor) {
        super.onEditorAdded(editor)
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
            val newScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
            foldingCoroutineScope = newScope
            foldingRefreshAlarm = Alarm(newScope, Alarm.ThreadToUse.SWING_THREAD)
        }

        if (cleanupRegistrationEditor === editor) {
            return
        }

        val editorDisposable = editor as? Disposable ?: return
        cleanupRegistrationEditor = editor
        Disposer.register(editorDisposable) {
            clearRefreshInfrastructure()
        }
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
        val alarm = foldingRefreshAlarm ?: return
        val expectedModificationStamp = editor.document.modificationStamp
        alarm.cancelAllRequests()
        alarm.addRequest(
            { refreshFoldRegions(editor, expectedModificationStamp) },
            debounceDelayMs,
        )
    }

    private fun refreshFoldRegions(
        editor: Editor,
        expectedModificationStamp: Long,
    ) {
        val project = editorProject ?: return
        val coroutineScope = foldingCoroutineScope ?: return
        val document = editor.document

        foldingRefreshJob?.cancel()
        foldingRefreshJob = coroutineScope.launch {
            withContext(Dispatchers.EDT) {
                if (project.isDisposed || editor.isDisposed) {
                    return@withContext
                }
                if (document.modificationStamp != expectedModificationStamp) {
                    return@withContext
                }

                PsiDocumentManager.getInstance(project).commitDocument(document)
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
                return@launch
            } catch (throwable: Throwable) {
                LOG.warn("Failed to refresh JSONinja folding regions", throwable)
                emptyList()
            }

            withContext(Dispatchers.EDT) {
                if (project.isDisposed || editor.isDisposed) {
                    return@withContext
                }
                if (document.modificationStamp != expectedModificationStamp) {
                    return@withContext
                }

                applyFoldRegions(editor, foldRegions)
            }
        }
    }

    private fun clearRefreshInfrastructure() {
        foldingRefreshAlarm?.cancelAllRequests()
        foldingRefreshJob?.cancel()
        foldingCoroutineScope?.cancel()
        foldingRefreshAlarm = null
        foldingRefreshJob = null
        foldingCoroutineScope = null
        cleanupRegistrationEditor = null
    }
}

private val LOG = logger<FoldingAwareEditorTextField>()
private val JSONINJA_FOLD_REGION_KEY = Key.create<Boolean>("JSONINJA_FOLD_REGION_KEY")
private const val FOLDING_REFRESH_DEBOUNCE_DELAY_MS = 150

private data class ManualFoldRegion(
    val startOffset: Int,
    val endOffset: Int,
    val placeholderText: String,
    val shouldCollapse: Boolean,
    val isGutterMarkEnabledForSingleLine: Boolean,
)

private fun collectFoldRegions(
    project: Project,
    document: Document,
): List<ManualFoldRegion> {
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
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
