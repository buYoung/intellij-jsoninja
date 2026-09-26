package com.livteam.jsoninja.services

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.Side
import com.intellij.json.JsonFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.actions.RenameJsonDiffTitlesAction
import com.livteam.jsoninja.actions.RestoreJsonDiffSortAction
import com.livteam.jsoninja.actions.SelectJsonDiffSortModeAction
import com.livteam.jsoninja.actions.SortJsonDiffKeysOnceAction
import com.livteam.jsoninja.actions.ToggleJsonDiffArrayOrderAction
import com.livteam.jsoninja.actions.ToggleJsonDiffAutoSortAction
import com.livteam.jsoninja.diff.JsonDiffKeys
import com.livteam.jsoninja.diff.JsonDiffSession
import com.livteam.jsoninja.model.JsonDiffAlignmentOptions
import com.livteam.jsoninja.model.JsonDiffAlignmentStatus
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.model.JsonDiffSortMode
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.ui.diff.JsonDiffVirtualFile
import com.livteam.jsoninja.ui.diff.JsonDiffWindowDialog
import com.livteam.jsoninja.ui.dialog.LargeFileWarningDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class JsonDiffService(private val project: Project) {

    private val LOG = logger<JsonDiffService>()
    private val formatterService = project.service<JsonFormatterService>()
    private val alignmentService = project.service<JsonDiffAlignmentService>()
    private val coroutineScopeService = project.service<JsoninjaCoroutineScopeService>()

    private data class ActiveDiffContext(
        var displayMode: JsonDiffDisplayMode,
        val session: JsonDiffSession,
        var editorTabFile: JsonDiffVirtualFile? = null,
        var windowDialog: JsonDiffWindowDialog? = null
    )

    /**
     * 같은 시점에 읽은 양쪽 텍스트와 Document stamp
     */
    private data class DiffPairCapture(
        val leftText: String,
        val rightText: String,
        val leftStamp: Long,
        val rightStamp: Long
    )

    private var activeDiffContext: ActiveDiffContext? = null

    fun openDiff(
        displayMode: JsonDiffDisplayMode,
        currentJson: String?,
        defaultSortKeys: Boolean
    ) {
        val leftJson = currentJson ?: "{}"
        val existingContext = activeDiffContext
        val hasExistingOpenHost = existingContext != null && hasOpenHost(existingContext)
        val sortKeys = if (hasExistingOpenHost) usesSortedInput(existingContext.session) else defaultSortKeys

        coroutineScopeService.launch {
            try {
                val leftDiffText = withContext(Dispatchers.Default) {
                    prepareDiffText(leftJson, sortKeys)
                }
                val rightDiffText = if (hasExistingOpenHost) {
                    null
                } else {
                    withContext(Dispatchers.Default) {
                        prepareDiffText("{}", defaultSortKeys)
                    }
                }

                withContext(Dispatchers.EDT) {
                    if (project.isDisposed) return@withContext
                    val diffContext = getOrCreateContext(leftDiffText, rightDiffText, displayMode, defaultSortKeys)
                    val currentDisplayMode = getCurrentDisplayMode(diffContext)

                    if (currentDisplayMode != displayMode) {
                        closeOpenHosts(diffContext)
                        diffContext.displayMode = displayMode
                    }

                    openCurrentHost(diffContext)
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            }
        }
    }

    /**
     * Validates and formats JSON in a single operation to improve performance
     * @param json The JSON string to validate and format
     * @param semantic Whether to use semantic comparison (sorted keys)
     * @return Pair of (isValid, formattedJson) - formattedJson is null if invalid
     */
    fun validateAndFormat(json: String, semantic: Boolean): Pair<Boolean, String?> {
        return try {
            if (!formatterService.isValidJson(json)) {
                return Pair(false, null)
            }

            val formatState = if (semantic) JsonFormatState.PRETTIFY_SORTED else JsonFormatState.PRETTIFY
            val formatted = formatterService.formatJson(json, formatState, semantic)
            Pair(true, formatted)
        } catch (e: Exception) {
            Pair(false, null)
        }
    }

    fun createDiffRequest(
        leftJson: String,
        rightJson: String,
        title: String? = null,
        semantic: Boolean = false
    ): SimpleDiffRequest {
        val leftFinal = prepareDiffText(leftJson, semantic)
        val rightFinal = prepareDiffText(rightJson, semantic)

        return createDiffRequest(
            leftDocument = createDiffDocument(leftFinal),
            rightDocument = createDiffDocument(rightFinal),
            title = title,
            semantic = semantic
        )
    }

    fun createDiffRequest(
        leftDocument: Document,
        rightDocument: Document,
        title: String? = null,
        semantic: Boolean = false
    ): SimpleDiffRequest {
        val session = JsonDiffSession(leftDocument, rightDocument, shouldAutoSort = semantic)
        return createDiffRequest(session, title)
    }

    internal fun createDiffRequest(
        session: JsonDiffSession,
        title: String? = null
    ): SimpleDiffRequest {
        val diffTitle = title ?: LocalizationBundle.message("dialog.json.diff.title")
        val leftContent = createDiffContent(session.leftDocument)
        val rightContent = createDiffContent(session.rightDocument)

        val request = SimpleDiffRequest(
            diffTitle,
            leftContent,
            rightContent,
            session.getTitle(Side.LEFT),
            session.getTitle(Side.RIGHT)
        )

        request.putUserData(JsonDiffKeys.JSON_DIFF_REQUEST_MARKER, true)
        request.putUserData(JsonDiffKeys.JSON_DIFF_SORT_KEYS, session.shouldAutoSort)
        request.putUserData(JsonDiffKeys.JSON_DIFF_SESSION, session)
        // 양쪽 이름 변경은 viewer를 다시 만들지 않고 이 라벨에 반영해 Document와 정렬 상태를 유지한다.
        request.putUserData(DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER, session.createTitleCustomizers())
        request.putUserData(
            DiffUserDataKeys.CONTEXT_ACTIONS,
            listOf(
                SortJsonDiffKeysOnceAction(),
                SelectJsonDiffSortModeAction(),
                ToggleJsonDiffAutoSortAction(),
                ToggleJsonDiffArrayOrderAction(),
                RestoreJsonDiffSortAction(),
                RenameJsonDiffTitlesAction()
            )
        )

        return request
    }

    /**
     * 현재 선택된 정렬 기준을 한 번 적용합니다.
     */
    fun applySortOnce(
        session: JsonDiffSession,
        isHostActive: () -> Boolean,
        onUnavailable: () -> Unit
    ) {
        startManualSort(session, isHostActive, onUnavailable)
    }

    fun changeSortMode(
        session: JsonDiffSession,
        sortMode: JsonDiffSortMode,
        isHostActive: () -> Boolean,
        onUnavailable: () -> Unit
    ) {
        session.changeSortMode(sortMode)
        startManualSort(session, isHostActive, onUnavailable)
    }

    /**
     * 켜면 현재 선택을 한 번 적용한 뒤 이후 편집을 따라가고, 끄면 원본을 복원하지 않고 이후 정렬만 멈춥니다.
     */
    fun changeAutoSort(
        session: JsonDiffSession,
        isEnabled: Boolean,
        isHostActive: () -> Boolean,
        onUnavailable: () -> Unit
    ) {
        session.changeAutoSort(isEnabled)
        if (isEnabled) {
            startManualSort(session, isHostActive, onUnavailable)
        }
    }

    fun changeIgnoreArrayOrder(
        session: JsonDiffSession,
        isEnabled: Boolean,
        isHostActive: () -> Boolean,
        onUnavailable: () -> Unit
    ) {
        session.changeIgnoreArrayOrder(isEnabled)
        if (isEnabled) {
            startManualSort(session, isHostActive, onUnavailable)
        }
    }

    fun canRestoreSort(session: JsonDiffSession): Boolean {
        return session.getRestorableSnapshot() != null
    }

    /**
     * 마지막 수동 정렬 직전 텍스트를 양쪽에 한 번에 복원합니다.
     * 정렬 이후 편집이 있었으면 복원하지 않고, 복원 쓰기는 자동 정렬을 다시 일으키지 않습니다.
     */
    fun restoreSort(session: JsonDiffSession, isHostActive: () -> Boolean): Boolean {
        if (project.isDisposed || !isHostActive()) return false
        val snapshot = session.getRestorableSnapshot() ?: return false

        session.invalidatePendingWork()
        var isRestored = false
        WriteCommandAction.runWriteCommandAction(project, LocalizationBundle.message("action.diff.sort.restore"), null, {
            if (session.getRestorableSnapshot() !== snapshot) return@runWriteCommandAction

            if (!session.leftDocument.charsSequence.contentEquals(snapshot.leftText)) {
                setGuardedText(session.leftDocument, snapshot.leftText)
            }
            if (!session.rightDocument.charsSequence.contentEquals(snapshot.rightText)) {
                setGuardedText(session.rightDocument, snapshot.rightText)
            }
            isRestored = true
        })
        session.clearRestoreSnapshot()
        return isRestored
    }

    /**
     * diff viewer의 편집 이후 세션의 자동 정렬 정책을 최신 양쪽 텍스트에 적용합니다.
     *
     * @param changedSides 마지막 적용 이후 사용자가 편집한 쪽
     */
    suspend fun formatAutomatically(
        session: JsonDiffSession,
        changedSides: Set<Side>,
        isHostActive: () -> Boolean
    ) {
        if (changedSides.isEmpty()) return

        // generation을 먼저 읽어야 이후 옵션 변경이 적용 시점에 거부된다.
        val generation = session.currentGeneration
        val shouldAutoSort = session.shouldAutoSort
        val options = session.alignmentOptions
        val capture = readAction { capturePair(session) }

        val formattedPair = try {
            withContext(Dispatchers.Default) {
                formatAutomaticPair(capture, changedSides, shouldAutoSort, options)
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: Exception) {
            LOG.warn("Automatic JSON diff formatting failed (${exception.javaClass.simpleName})")
            return
        }

        withContext(Dispatchers.EDT) {
            if (!canApply(session, generation, capture, isHostActive)) return@withContext
            applyPair(session, capture, formattedPair.first, formattedPair.second, null)
        }
    }

    private fun startManualSort(
        session: JsonDiffSession,
        isHostActive: () -> Boolean,
        onUnavailable: () -> Unit
    ) {
        if (project.isDisposed || session.isClosed || !isHostActive()) return
        if (!confirmLargeInput(session)) return

        val generation = session.invalidatePendingWork()
        val options = session.alignmentOptions

        coroutineScopeService.launch {
            val capture = readAction { capturePair(session) }
            val result = withContext(Dispatchers.Default) {
                alignmentService.align(capture.leftText, capture.rightText, options)
            }

            withContext(Dispatchers.EDT) {
                if (!canApply(session, generation, capture, isHostActive)) return@withContext

                when (result.status) {
                    JsonDiffAlignmentStatus.INVALID_INPUT -> onUnavailable()
                    JsonDiffAlignmentStatus.NO_CHANGE -> Unit
                    JsonDiffAlignmentStatus.APPLIED -> {
                        val isApplied = applyPair(
                            session = session,
                            capture = capture,
                            leftText = result.leftText,
                            rightText = result.rightText,
                            commandName = LocalizationBundle.message("action.diff.sort.keys.once")
                        )
                        if (isApplied) {
                            session.replaceRestoreSnapshot(
                                JsonDiffSession.RestoreSnapshot(
                                    leftText = capture.leftText,
                                    rightText = capture.rightText,
                                    sortedLeftStamp = session.leftDocument.modificationStamp,
                                    sortedRightStamp = session.rightDocument.modificationStamp
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 자동 정렬 정책별 결과를 계산합니다. 짝 정렬이 불가능하면 조용히 각 쪽의 기존 포맷팅으로 돌아갑니다.
     */
    private suspend fun formatAutomaticPair(
        capture: DiffPairCapture,
        changedSides: Set<Side>,
        shouldAutoSort: Boolean,
        options: JsonDiffAlignmentOptions
    ): Pair<String, String> {
        val isLeftChanged = Side.LEFT in changedSides
        val isRightChanged = Side.RIGHT in changedSides
        val usesIndependentFormatting = !shouldAutoSort ||
            (options.sortMode == JsonDiffSortMode.KEY_ASCENDING && !options.shouldIgnoreArrayOrder)

        fun formatChangedSides(leftText: String, sortKeys: Boolean): Pair<String, String> {
            return Pair(
                if (isLeftChanged) formatSide(leftText, sortKeys) else leftText,
                if (isRightChanged) formatSide(capture.rightText, sortKeys) else capture.rightText
            )
        }

        if (usesIndependentFormatting) {
            return formatChangedSides(capture.leftText, shouldAutoSort)
        }

        if (options.sortMode == JsonDiffSortMode.LEFT_ORDER) {
            // 기준 문서는 사전순으로 바꾸지 않고 일반 포맷팅만 적용한다.
            val leftText = if (isLeftChanged) formatSide(capture.leftText, false) else capture.leftText
            val result = alignmentService.align(leftText, capture.rightText, options)
            if (result.status == JsonDiffAlignmentStatus.INVALID_INPUT) {
                return Pair(leftText, if (isRightChanged) formatSide(capture.rightText, false) else capture.rightText)
            }
            return Pair(leftText, result.rightText)
        }

        val result = alignmentService.align(capture.leftText, capture.rightText, options)
        if (result.status == JsonDiffAlignmentStatus.INVALID_INPUT) {
            return formatChangedSides(capture.leftText, true)
        }
        return Pair(result.leftText, result.rightText)
    }

    private fun formatSide(text: String, sortKeys: Boolean): String {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return text

        val formattedText = formatterService.formatJson(trimmedText, JsonFormatState.PRETTIFY, sortKeys)
        return if (formattedText == trimmedText) text else formattedText
    }

    private fun capturePair(session: JsonDiffSession): DiffPairCapture {
        return DiffPairCapture(
            leftText = session.leftDocument.text,
            rightText = session.rightDocument.text,
            leftStamp = session.leftDocument.modificationStamp,
            rightStamp = session.rightDocument.modificationStamp
        )
    }

    private fun isCapturedPairCurrent(session: JsonDiffSession, capture: DiffPairCapture): Boolean {
        return session.leftDocument.modificationStamp == capture.leftStamp &&
            session.rightDocument.modificationStamp == capture.rightStamp
    }

    private fun canApply(
        session: JsonDiffSession,
        generation: Long,
        capture: DiffPairCapture,
        isHostActive: () -> Boolean
    ): Boolean {
        return !project.isDisposed &&
            isHostActive() &&
            session.isCurrent(generation) &&
            isCapturedPairCurrent(session, capture)
    }

    /**
     * 양쪽 결과를 하나의 WriteCommandAction으로 적용합니다. 자기 쓰기가 자동 정렬을 다시 일으키지 않도록 guard를 둡니다.
     */
    private fun applyPair(
        session: JsonDiffSession,
        capture: DiffPairCapture,
        leftText: String,
        rightText: String,
        commandName: String?
    ): Boolean {
        val leftChange = leftText.takeIf { it != capture.leftText }
        val rightChange = rightText.takeIf { it != capture.rightText }
        if (leftChange == null && rightChange == null) return false

        var isApplied = false
        WriteCommandAction.runWriteCommandAction(project, commandName, null, {
            if (!isCapturedPairCurrent(session, capture)) return@runWriteCommandAction

            leftChange?.let { setGuardedText(session.leftDocument, it) }
            rightChange?.let { setGuardedText(session.rightDocument, it) }
            isApplied = true
        })
        return isApplied
    }

    private fun setGuardedText(document: Document, text: String) {
        document.putUserData(JsonDiffKeys.JSON_DIFF_CHANGE_GUARD, true)
        try {
            document.setText(text)
        } finally {
            document.putUserData(JsonDiffKeys.JSON_DIFF_CHANGE_GUARD, false)
        }
    }

    private fun confirmLargeInput(session: JsonDiffSession): Boolean {
        val largestTextLength = maxOf(session.leftDocument.textLength, session.rightDocument.textLength)
        return LargeFileWarningDialog.showWarningIfNeeded(project, largestTextLength.toLong())
    }

    private fun usesSortedInput(session: JsonDiffSession): Boolean {
        return session.shouldAutoSort && session.sortMode == JsonDiffSortMode.KEY_ASCENDING
    }

    private fun getOrCreateContext(
        preparedLeftJson: String,
        preparedRightJson: String?,
        displayMode: JsonDiffDisplayMode,
        defaultSortKeys: Boolean
    ): ActiveDiffContext {
        val existingContext = activeDiffContext

        if (existingContext != null && hasOpenHost(existingContext)) {
            replaceLeftInput(existingContext.session, preparedLeftJson)
            return existingContext
        }

        existingContext?.session?.close()
        val session = JsonDiffSession(
            leftDocument = createDiffDocument(preparedLeftJson),
            rightDocument = createDiffDocument(preparedRightJson ?: "{}"),
            shouldAutoSort = defaultSortKeys
        )
        return ActiveDiffContext(
            displayMode = displayMode,
            session = session
        ).also { activeDiffContext = it }
    }

    /**
     * 열린 비교의 왼쪽 입력만 교체합니다. 오른쪽 기준과 세션 선택은 유지하고 이전 입력 쌍의 복원 스냅샷은 버립니다.
     */
    private fun replaceLeftInput(session: JsonDiffSession, preparedLeftJson: String) {
        session.invalidatePendingWork()
        session.clearRestoreSnapshot()
        replaceDocumentText(session.leftDocument, preparedLeftJson)
    }

    private fun hasOpenHost(diffContext: ActiveDiffContext): Boolean {
        return isEditorTabOpen(diffContext) || isWindowOpen(diffContext)
    }

    private fun getCurrentDisplayMode(diffContext: ActiveDiffContext): JsonDiffDisplayMode {
        return getOpenDisplayMode(diffContext) ?: diffContext.displayMode
    }

    private fun getOpenDisplayMode(diffContext: ActiveDiffContext): JsonDiffDisplayMode? {
        val isEditorTabOpen = isEditorTabOpen(diffContext)
        val isWindowOpen = isWindowOpen(diffContext)

        return when {
            isEditorTabOpen && !isWindowOpen -> JsonDiffDisplayMode.EDITOR_TAB
            isWindowOpen && !isEditorTabOpen -> JsonDiffDisplayMode.WINDOW
            isEditorTabOpen && isWindowOpen -> diffContext.displayMode
            else -> null
        }
    }

    private fun isEditorTabOpen(diffContext: ActiveDiffContext): Boolean {
        val diffFile = diffContext.editorTabFile ?: return false
        return FileEditorManager.getInstance(project).isFileOpen(diffFile)
    }

    private fun isWindowOpen(diffContext: ActiveDiffContext): Boolean {
        return diffContext.windowDialog?.isOpen() == true
    }

    private fun openCurrentHost(diffContext: ActiveDiffContext) {
        when (diffContext.displayMode) {
            JsonDiffDisplayMode.EDITOR_TAB -> openEditorTab(diffContext)
            JsonDiffDisplayMode.WINDOW -> openWindowDialog(diffContext)
        }
    }

    private fun closeOpenHosts(diffContext: ActiveDiffContext) {
        if (isEditorTabOpen(diffContext)) {
            diffContext.editorTabFile?.let { FileEditorManager.getInstance(project).closeFile(it) }
        }

        if (isWindowOpen(diffContext)) {
            diffContext.windowDialog?.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    private fun openEditorTab(diffContext: ActiveDiffContext) {
        val diffFile = diffContext.editorTabFile ?: JsonDiffVirtualFile(
            project = project,
            diffService = this,
            session = diffContext.session
        ).also { diffContext.editorTabFile = it }

        DiffEditorTabFilesManager.getInstance(project).showDiffFile(diffFile, true)
    }

    private fun openWindowDialog(diffContext: ActiveDiffContext) {
        val existingDialog = diffContext.windowDialog
        if (existingDialog != null && existingDialog.isOpen()) {
            existingDialog.showOrFocus()
            return
        }

        val dialog = JsonDiffWindowDialog(
            project = project,
            diffRequest = createDiffRequest(diffContext.session),
            onClosed = {
                if (activeDiffContext === diffContext) {
                    diffContext.windowDialog = null
                }
            }
        )

        diffContext.windowDialog = dialog
        dialog.showOrFocus()
    }

    private fun createDiffDocument(initialText: String): Document {
        return EditorFactory.getInstance().createDocument(initialText)
    }

    private fun replaceDocumentText(document: Document, newText: String) {
        if (document.text == newText) return

        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(newText)
        }
    }

    private fun prepareDiffText(json: String, semantic: Boolean): String {
        val (isValid, formattedJson) = validateAndFormat(json, semantic)
        return if (isValid && formattedJson != null) formattedJson else json
    }

    private fun createDiffContent(document: Document): com.intellij.diff.contents.DocumentContent {
        val json5FileType = FileTypeManager.getInstance().getFileTypeByExtension("json5")
        val fileType = if (json5FileType is UnknownFileType) JsonFileType.INSTANCE else json5FileType
        return DiffContentFactory.getInstance().create(project, document, fileType)
    }
}
