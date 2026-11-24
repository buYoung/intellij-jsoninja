package com.livteam.jsoninja.ui.component.editor

import com.intellij.json.JsonFileType
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
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
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
    private val editorFileType = fileType
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
        if (isJsonFileType(editorFileType)) {
            val textLength = document.textLength
            val lineCount = document.lineCount
            val textSnapshot = document.immutableCharSequence
            foldingRefreshJob = coroutineScope.launch {
                if (debounceDelayMs > 0) {
                    delay(debounceDelayMs.toLong())
                }
                refreshJsonFoldRegions(
                    project = project,
                    editor = editor,
                    document = document,
                    textSnapshot = textSnapshot,
                    textLength = textLength,
                    lineCount = lineCount,
                    expectedModificationStamp = expectedModificationStamp,
                    modalityState = modalityState,
                )
            }
            return
        }

        foldingRefreshJob = coroutineScope.launch {
            if (debounceDelayMs > 0) {
                delay(debounceDelayMs.toLong())
            }
            refreshPsiFoldRegions(project, editor, document, expectedModificationStamp, modalityState)
        }
    }

    private suspend fun refreshJsonFoldRegions(
        project: Project,
        editor: Editor,
        document: Document,
        textSnapshot: CharSequence,
        textLength: Int,
        lineCount: Int,
        expectedModificationStamp: Long,
        modalityState: ModalityState,
    ) {
        if (project.isDisposed || editor.isDisposed || document.modificationStamp != expectedModificationStamp) {
            return
        }

        val foldRegions = if (lineCount <= 1 || textLength > MAX_AUTO_FOLD_DOCUMENT_LENGTH) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                collectJsonFoldRegions(textSnapshot)
            }
        }

        val edtModalityContext = Dispatchers.EDT + modalityState.asContextElement()
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

    private suspend fun refreshPsiFoldRegions(
        project: Project,
        editor: Editor,
        document: Document,
        expectedModificationStamp: Long,
        modalityState: ModalityState,
    ) {
        val edtModalityContext = Dispatchers.EDT + modalityState.asContextElement()
        if (document.textLength > MAX_AUTO_FOLD_DOCUMENT_LENGTH) {
            withContext(edtModalityContext) {
                if (!project.isDisposed && !editor.isDisposed && document.modificationStamp == expectedModificationStamp) {
                    applyFoldRegions(editor, emptyList())
                }
            }
            return
        }

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
private const val MAX_AUTO_FOLD_DOCUMENT_LENGTH = 2_000_000
private const val MAX_FOLD_REGIONS = 15_000
private const val INITIAL_STACK_CAPACITY = 64
private const val FOLD_KIND_OBJECT: Byte = 0
private const val FOLD_KIND_ARRAY: Byte = 1

internal data class ManualFoldRegion(
    val startOffset: Int,
    val endOffset: Int,
    val placeholderText: String,
    val shouldCollapse: Boolean,
    val isGutterMarkEnabledForSingleLine: Boolean,
)

internal fun isJsonFileType(fileType: FileType?): Boolean {
    if (fileType == null) {
        return false
    }
    if (fileType is JsonFileType) {
        return true
    }
    val extension = fileType.defaultExtension
    return extension.equals("json", ignoreCase = true) ||
        extension.equals("json5", ignoreCase = true) ||
        fileType.name.equals("JSON", ignoreCase = true) ||
        fileType.name.equals("JSON5", ignoreCase = true)
}

internal fun collectFoldRegions(
    project: Project,
    document: Document,
): List<ManualFoldRegion> {
    if (document.lineCount <= 1 || document.textLength > MAX_AUTO_FOLD_DOCUMENT_LENGTH) {
        return emptyList()
    }

    val cachedPsiFile = PsiDocumentManager.getInstance(project).getCachedPsiFile(document)
    val fileType = cachedPsiFile?.fileType ?: FileDocumentManager.getInstance().getFile(document)?.fileType
    if (isJsonFileType(fileType)) {
        return collectJsonFoldRegions(document.immutableCharSequence)
    }

    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val psiFile = cachedPsiFile ?: psiDocumentManager.getPsiFile(document) ?: return emptyList()
    if (!psiFile.isValid) {
        return emptyList()
    }

    if (isJsonFileType(psiFile.fileType)) {
        return collectJsonFoldRegions(document.immutableCharSequence)
    }

    val foldingBuilder = findFoldingBuilder(psiFile) ?: return emptyList()
    return LanguageFolding.buildFoldingDescriptors(foldingBuilder, psiFile, document, false)
        .asSequence()
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
        .take(MAX_FOLD_REGIONS)
        .toList()
}

internal fun collectJsonFoldRegions(text: CharSequence): List<ManualFoldRegion> {
    val length = text.length
    if (length < 3 || length > MAX_AUTO_FOLD_DOCUMENT_LENGTH) {
        return emptyList()
    }

    var stackCapacity = INITIAL_STACK_CAPACITY
    var openOffsets = IntArray(stackCapacity)
    var openLines = IntArray(stackCapacity)
    var openKinds = ByteArray(stackCapacity)
    var stackSize = 0

    val regions = ArrayList<ManualFoldRegion>()
    var currentLine = 0
    var index = 0

    while (index < length) {
        val ch = text[index]
        when (ch) {
            '\n' -> {
                currentLine += 1
                index += 1
            }
            '"', '\'' -> {
                index += 1
                while (index < length) {
                    val c = text[index]
                    if (c == '\\') {
                        index += 1
                        if (index < length) {
                            if (text[index] == '\n') {
                                currentLine += 1
                            } else if (text[index] == '\r' && index + 1 < length && text[index + 1] == '\n') {
                                currentLine += 1
                                index += 1
                            }
                            index += 1
                        }
                        continue
                    }
                    if (c == '\n') {
                        currentLine += 1
                    } else if (c == ch) {
                        index += 1
                        break
                    }
                    index += 1
                }
            }
            '/' -> {
                if (index + 1 < length) {
                    val next = text[index + 1]
                    if (next == '/') {
                        index += 2
                        while (index < length) {
                            if (text[index] == '\n') {
                                currentLine += 1
                                index += 1
                                break
                            }
                            index += 1
                        }
                        continue
                    } else if (next == '*') {
                        index += 2
                        while (index < length) {
                            val c = text[index]
                            if (c == '\n') {
                                currentLine += 1
                                index += 1
                            } else if (c == '*' && index + 1 < length && text[index + 1] == '/') {
                                index += 2
                                break
                            } else {
                                index += 1
                            }
                        }
                        continue
                    }
                }
                index += 1
            }
            '{', '[' -> {
                if (stackSize == stackCapacity) {
                    stackCapacity *= 2
                    openOffsets = openOffsets.copyOf(stackCapacity)
                    openLines = openLines.copyOf(stackCapacity)
                    openKinds = openKinds.copyOf(stackCapacity)
                }
                openOffsets[stackSize] = index
                openLines[stackSize] = currentLine
                openKinds[stackSize] = if (ch == '{') FOLD_KIND_OBJECT else FOLD_KIND_ARRAY
                stackSize += 1
                index += 1
            }
            '}', ']' -> {
                val expectedKind = if (ch == '}') FOLD_KIND_OBJECT else FOLD_KIND_ARRAY
                var matchIndex = stackSize - 1
                while (matchIndex >= 0 && openKinds[matchIndex] != expectedKind) {
                    matchIndex -= 1
                }
                if (matchIndex >= 0) {
                    val openOffset = openOffsets[matchIndex]
                    val openLine = openLines[matchIndex]
                    stackSize = matchIndex
                    if (currentLine > openLine) {
                        regions.add(
                            ManualFoldRegion(
                                startOffset = openOffset,
                                endOffset = index + 1,
                                placeholderText = if (expectedKind == FOLD_KIND_OBJECT) "{...}" else "[...]",
                                shouldCollapse = false,
                                isGutterMarkEnabledForSingleLine = false,
                            )
                        )
                        if (regions.size >= MAX_FOLD_REGIONS) {
                            return regions
                        }
                    }
                }
                index += 1
            }
            else -> {
                index += 1
            }
        }
    }

    return regions
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
    val existingManagedRegions = foldingModel.allFoldRegions
        .filter { it.getUserData(JSONINJA_FOLD_REGION_KEY) == true }

    if (existingManagedRegions.isEmpty() && foldRegions.isEmpty()) {
        return
    }

    val desiredByRange = HashMap<Long, ManualFoldRegion>(foldRegions.size * 2)
    for (foldRegion in foldRegions) {
        val rangeKey = packFoldRange(foldRegion.startOffset, foldRegion.endOffset)
        desiredByRange.putIfAbsent(rangeKey, foldRegion)
    }

    val keptRangeKeys = HashSet<Long>(existingManagedRegions.size * 2)
    val regionsToRemove = ArrayList<FoldRegion>()
    val removedExpandedStates = HashMap<Long, Boolean>()

    for (existing in existingManagedRegions) {
        if (!existing.isValid) {
            regionsToRemove.add(existing)
            continue
        }

        val rangeKey = packFoldRange(existing.startOffset, existing.endOffset)
        val desired = desiredByRange[rangeKey]
        if (desired != null &&
            existing.placeholderText == desired.placeholderText &&
            existing.isGutterMarkEnabledForSingleLine == desired.isGutterMarkEnabledForSingleLine &&
            keptRangeKeys.add(rangeKey)
        ) {
            continue
        }

        removedExpandedStates[rangeKey] = existing.isExpanded
        regionsToRemove.add(existing)
    }

    val regionsToAdd = ArrayList<ManualFoldRegion>()
    for (foldRegion in foldRegions) {
        val rangeKey = packFoldRange(foldRegion.startOffset, foldRegion.endOffset)
        if (!keptRangeKeys.contains(rangeKey) && desiredByRange[rangeKey] === foldRegion) {
            regionsToAdd.add(foldRegion)
        }
    }

    if (regionsToRemove.isEmpty() && regionsToAdd.isEmpty()) {
        return
    }

    foldingModel.runBatchFoldingOperation {
        for (region in regionsToRemove) {
            foldingModel.removeFoldRegion(region)
        }

        for (foldRegion in regionsToAdd) {
            val rangeKey = packFoldRange(foldRegion.startOffset, foldRegion.endOffset)
            val region = foldingModel.addFoldRegion(
                foldRegion.startOffset,
                foldRegion.endOffset,
                foldRegion.placeholderText,
            ) ?: continue

            region.putUserData(JSONINJA_FOLD_REGION_KEY, true)
            region.isGutterMarkEnabledForSingleLine = foldRegion.isGutterMarkEnabledForSingleLine
            region.isExpanded = removedExpandedStates[rangeKey] ?: !foldRegion.shouldCollapse
        }
    }
}

private fun packFoldRange(startOffset: Int, endOffset: Int): Long =
    (startOffset.toLong() shl 32) or (endOffset.toLong() and 0xFFFFFFFFL)
