package com.livteam.jsoninja.diff

import com.fasterxml.jackson.core.JsonProcessingException
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.json.JsonFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.dialogs.LargeFileWarningDialog
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * 자동 JSON 포맷팅을 제공하는 JSON diff viewer extension.
 *
 * 이 extension은 diff viewer에서 JSON content를 감지하고 다음 성능 최적화를 포함한
 * 자동 포맷팅을 적용합니다:
 * - 조기 종료를 통한 빠른 JSON content 감지
 * - memory leak 방지를 위한 document별 state 추적
 * - 과도한 처리를 방지하는 debounced 포맷팅
 * - 재진입 update 보호
 *
 * Threading: 모든 document 작업은 EDT에서 수행됩니다. 무거운 JSON parsing은
 * 가능한 경우 background thread로 이동됩니다.
 */
class JsonDiffExtension : DiffExtension() {

    private object Constants {
        const val DEBOUNCE_DELAY = 300 // milliseconds
        const val SMALL_EDIT_THRESHOLD = 3 // characters
        val CHANGE_GUARD_KEY: Key<Boolean> = Key.create("JSONINJA_DIFF_CHANGE_GUARD")
    }

    /**
     * 처리를 최적화하고 memory leak을 방지하기 위한 경량 document별 state.
     * documentStates map을 통해 동기화된 access가 이루어져야 합니다.
     */
    private data class DocumentState(
        var lastContentHash: Int = 0,
        var lastChangeTime: Long = 0L,
        var lastEditSize: Int = 0,
        val isSelfUpdate: AtomicBoolean = AtomicBoolean(false),
        var detectionResult: JsonDetectionResult = JsonDetectionResult.UNKNOWN
    )

    private enum class JsonDetectionResult {
        YES, NO, UNKNOWN
    }

    companion object {
        private val LOG = Logger.getInstance(JsonDiffExtension::class.java)

        // 강한 참조 없이 document별 state를 추적하는 동기화된 WeakHashMap
        private val documentStates = Collections.synchronizedMap(WeakHashMap<Document, DocumentState>())

        /**
         * 주어진 document에 대한 document state를 가져오거나 생성합니다.
         * document별 state에 대한 thread-safe access.
         */
        private fun getDocumentState(document: Document): DocumentState {
            return documentStates.computeIfAbsent(document) { DocumentState() }
        }
    }

    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        // EditorDiffViewer(text diff)에만 적용
        if (viewer !is EditorDiffViewer) {
            LOG.debug("Skipping non-EditorDiffViewer: ${viewer.javaClass.simpleName}")
            return
        }

        if (request.getUserData(JsonDiffKeys.JSON_DIFF_REQUEST_MARKER) != true) {
            return
        }

        val editors = viewer.editors
        if (editors.size != 2) {
            LOG.debug("Skipping diff with ${editors.size} editors (expected 2)")
            return
        }

        // project와 service들 가져오기
        val project = context.project
        if (project == null) {
            LOG.debug("No project available in DiffContext, skipping JSON diff extension")
            return
        }

        val formatterService = project.service<JsonFormatterService>()
        val settings = JsoninjaSettingsState.getInstance(project)
        val projectName = project.name

        // 양쪽 모두 JSON인지 판단; 최종 검증은 JsonFormatterService.isValidJson을 통해 진행
        val startTime = System.currentTimeMillis()
        val jsonContentResults = editors.map { editor ->
            isJsonContent(editor, formatterService, projectName, settings) to editor
        }
        val detectionTime = System.currentTimeMillis() - startTime

        if (LOG.isDebugEnabled) {
            LOG.debug("JSON detection completed in ${detectionTime}ms for project '$projectName'")
        }

        val isJsonDiff = jsonContentResults.all { it.first }
        if (!isJsonDiff) {
            LOG.debug("Not all editors contain JSON content, skipping JSON diff extension")
            return
        }

        // 대용량 파일 확인 및 필요시 경고 표시 (warning이 활성된 경우에만)
        if (settings.showLargeFileWarning) {
            val thresholdBytes = settings.largeFileThresholdMB * 1024 * 1024L
            val largeFileDetected = jsonContentResults.any { (_, editor) ->
                editor.document.textLength.toLong() >= thresholdBytes
            }

            if (largeFileDetected) {
                val largestFile = jsonContentResults.maxByOrNull { (_, editor) -> editor.document.textLength }
                val largestEditor = largestFile?.second
                val largestFileSize = largestEditor?.document?.textLength?.toLong() ?: 0L
                val fileName = largestEditor?.virtualFile?.name

                // 경고 dialog를 표시하고 사용자의 선택을 존중
                val shouldProceed = LargeFileWarningDialog.showWarningIfNeeded(
                    project,
                    largestFileSize,
                    fileName
                )

                if (!shouldProceed) {
                    LOG.debug("User cancelled large file JSON diff processing for '$fileName'")
                    return
                }

                LOG.debug("User confirmed large file JSON diff processing for '$fileName' (${largestFileSize / (1024 * 1024)} MB)")
            }
        }

        // 양쪽 editor에 listener 설치
        editors.forEach { editor ->
            installAutoFormatter(project, editor, viewer, formatterService, settings)
        }

        LOG.debug("JSON diff extension activated for project '$projectName' with ${editors.size} editors")
    }

    /**
     * 빠르고 다단계 접근 방식을 사용하여 editor content가 JSON인지 판단합니다.
     * 최종 검증은 항상 요구사항대로 JsonFormatterService.isValidJson을 통해 진행됩니다.
     *
     * @param editor 확인할 editor
     * @param formatterService JSON 검증을 위한 service
     * @param projectName logging context를 위한 project 이름
     * @return content가 JSON으로 감지되면 true
     */
    private fun isJsonContent(
        editor: Editor,
        formatterService: JsonFormatterService,
        projectName: String,
        settings: JsoninjaSettingsState
    ): Boolean {
        val document = editor.document
        val state = getDocumentState(document)
        val fileName = editor.virtualFile?.name ?: "<unknown>"

        // cached 감지 결과 먼저 확인
        if (state.detectionResult != JsonDetectionResult.UNKNOWN) {
            LOG.debug("Using cached JSON detection result for '$fileName': ${state.detectionResult}")
            return state.detectionResult == JsonDetectionResult.YES
        }

        try {
            // 1단계: 빠른 경로 - 파일 타입 확인
            if (editor.virtualFile?.fileType == JsonFileType.INSTANCE) {
                LOG.debug("File '$fileName' detected as JSON via file type")
                state.detectionResult = JsonDetectionResult.YES
                return true
            }

            val text = document.text
            if (text.isBlank()) {
                LOG.debug("File '$fileName' is blank, not JSON")
                state.detectionResult = JsonDetectionResult.NO
                return false
            }

            // 2단계: 크기 확인 - warning이 활성된 경우에만
            if (settings.showLargeFileWarning) {
                val thresholdBytes = settings.largeFileThresholdMB * 1024 * 1024L
                if (text.length > thresholdBytes) {
                    LOG.debug("File '$fileName' larger than threshold (${text.length / (1024 * 1024)} MB vs ${settings.largeFileThresholdMB} MB), will show warning later")
                    // 여기서 거절하지 말고 - 나중에 warning dialog이 처리하도록 함
                    // 지금은 JSON 감지를 계속 진행
                }
            }

            // 3단계: 휴리스틱 확인
            val trimmed = text.trim()
            if (!(trimmed.startsWith('{') || trimmed.startsWith('['))) {
                LOG.debug("File '$fileName' does not start with JSON delimiters")
                state.detectionResult = JsonDetectionResult.NO
                return false
            }

            // 4단계: JsonFormatterService를 통한 필수 검증
            val isValid = formatterService.isValidJson(trimmed)
            state.detectionResult = if (isValid) JsonDetectionResult.YES else JsonDetectionResult.NO

            if (LOG.isDebugEnabled) {
                LOG.debug("File '$fileName' JSON validation result: $isValid (project: '$projectName')")
            }

            return isValid

        } catch (e: OutOfMemoryError) {
            LOG.error("OutOfMemoryError during JSON detection for file '$fileName' (${document.textLength} chars)", e)
            state.detectionResult = JsonDetectionResult.NO
            return false
        } catch (e: Exception) {
            LOG.warn("Error during JSON detection for file '$fileName'", e)
            state.detectionResult = JsonDetectionResult.NO
            return false
        }
    }

    /**
     * 최적화된 debouncing과 소규모 편집 감지를 통해 document 변경 listener를 설치합니다.
     *
     * @param project project context
     * @param editor 모니터링할 editor
     * @param viewer disposal 등록을 위한 diff viewer
     * @param formatterService JSON 포맷팅을 위한 service
     */
    private fun installAutoFormatter(
        project: Project,
        editor: Editor,
        viewer: FrameDiffTool.DiffViewer,
        formatterService: JsonFormatterService,
        settings: JsoninjaSettingsState
    ) {
        val document = editor.document
        val state = getDocumentState(document)
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, viewer)
        val fileName = editor.virtualFile?.name ?: "<unknown>"

        val documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                // 이 변경이 우리 자체 formatter에서 발생한 경우 스킵
                if (document.getUserData(Constants.CHANGE_GUARD_KEY) == true) {
                    LOG.debug("Skipping self-update for '$fileName'")
                    return
                }

                if (state.isSelfUpdate.get()) {
                    LOG.debug("Skipping self-update (atomic flag) for '$fileName'")
                    return
                }

                val currentTime = System.currentTimeMillis()
                val editSize = abs(event.newLength - event.oldLength)
                state.lastEditSize = editSize
                state.lastChangeTime = currentTime

                // 소규모 공백 전용 편집 스킵
                if (editSize <= Constants.SMALL_EDIT_THRESHOLD) {
                    val changedText = event.newFragment.toString()
                    if (changedText.isBlank() || changedText.all { it.isWhitespace() }) {
                        LOG.debug("Skipping small whitespace edit ($editSize chars) for '$fileName'")
                        return
                    }
                }

                // 대용량 파일은 viewer 생성 시점에서 warning dialog이 처리
                // 여기서 하드 리미트가 필요 없음 - 사용자가 이미 동의했거나 warning이 비활성화됨

                // 대기 중인 포맷팅 취소
                alarm.cancelAllRequests()

                // debounce로 새로운 포맷팅 예약
                alarm.addRequest({
                    if (!project.isDisposed) {
                        scheduleJsonFormatting(project, document, formatterService, fileName)
                    }
                }, Constants.DEBOUNCE_DELAY)
            }
        }

        document.addDocumentListener(documentListener)

        // viewer가 dispose될 때 listener 제거 및 정리
        Disposer.register(viewer) {
            document.removeDocumentListener(documentListener)
            alarm.cancelAllRequests()
            // WeakHashMap이 자연스럽게 document state를 정리하도록 함
            LOG.debug("Disposed JSON diff extension for '$fileName'")
        }

        // content가 있으면 초기 포맷팅 수행
        if (document.text.isNotBlank()) {
            scheduleJsonFormatting(project, document, formatterService, fileName)
        }
    }

    /**
     * background thread에서 JSON 포맷팅 작업을 예약하고, 다음 EDT에서 변경사항을 적용합니다.
     * 안전한 document access를 위해 ReadAction을, 변경을 위해 WriteCommandAction을 사용합니다.
     */
    private fun scheduleJsonFormatting(
        project: Project,
        document: Document,
        formatterService: JsonFormatterService,
        fileName: String
    ) {
        if (project.isDisposed) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val formattedResult = formatJsonInBackground(document, formatterService, fileName)
            if (formattedResult != null && !project.isDisposed) {
                ApplicationManager.getApplication().invokeLater({
                    applyJsonFormatting(project, document, formattedResult, fileName)
                }, ModalityState.defaultModalityState())
            }
        }
    }

    /**
     * background thread에서 JSON 포맷팅 연산을 수행합니다.
     * 포맷팅을 스킵하거나 실패한 경우 null을 반환합니다.
     */
    private fun formatJsonInBackground(
        document: Document,
        formatterService: JsonFormatterService,
        fileName: String
    ): String? {
        // 재진입 update 방지
        if (document.getUserData(Constants.CHANGE_GUARD_KEY) == true) {
            LOG.debug("Skipping formatting due to change guard for '$fileName'")
            return null
        }

        val state = getDocumentState(document)
        if (state.isSelfUpdate.get()) {
            LOG.debug("Skipping formatting due to self-update flag for '$fileName'")
            return null
        }

        val text = document.text
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            LOG.debug("Document '$fileName' is empty, skipping formatting")
            return null
        }

        // 불필요한 작업을 방지하기 위해 content hash 확인
        val contentHash = trimmed.hashCode()
        if (state.lastContentHash == contentHash) {
            LOG.debug("Document '$fileName' content unchanged, skipping formatting")
            return null
        }

        return try {
            val startTime = System.currentTimeMillis()
            // JsonFormatterService는 요구사항대로 내부적으로 isValidJson을 통해 검증
            val formatted = formatterService.formatJson(trimmed, JsonFormatState.PRETTIFY)
            val formatTime = System.currentTimeMillis() - startTime

            state.lastContentHash = contentHash

            if (LOG.isDebugEnabled) {
                LOG.debug("JSON formatting completed in ${formatTime}ms for '$fileName'")
            }

            if (formatted != trimmed) formatted else null

        } catch (e: ProcessCanceledException) {
            // background task 중단을 적절히 처리하기 위해 취소 예외를 다시 던짐
            throw e
        } catch (e: JsonProcessingException) {
            LOG.debug("JSON processing failed for '$fileName': ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            LOG.error("OutOfMemoryError during JSON formatting for '$fileName' (${document.textLength} chars)", e)
            // 이 document에 대한 재시도를 방지하는 flag 설정
            state.detectionResult = JsonDetectionResult.NO
            null
        } catch (e: IOException) {
            LOG.warn("IO error during JSON formatting for '$fileName'", e)
            null
        } catch (e: Exception) {
            LOG.warn("Unexpected error during JSON formatting for '$fileName'", e)
            null
        }
    }

    /**
     * 적절한 write action과 guard와 함께 EDT에서 포맷팅된 JSON 결과를 document에 적용합니다.
     */
    private fun applyJsonFormatting(
        project: Project,
        document: Document,
        formatted: String,
        fileName: String
    ) {
        if (project.isDisposed) return

        val state = getDocumentState(document)

        // EDT에서의 최종 guard 확인
        if (document.getUserData(Constants.CHANGE_GUARD_KEY) == true || state.isSelfUpdate.get()) {
            LOG.debug("Skipping document update due to guard for '$fileName'")
            return
        }

        try {
            document.putUserData(Constants.CHANGE_GUARD_KEY, true)
            state.isSelfUpdate.set(true)

            WriteCommandAction.runWriteCommandAction(project) {
                document.setText(formatted)
            }

            LOG.debug("Applied JSON formatting to '$fileName'")

        } finally {
            document.putUserData(Constants.CHANGE_GUARD_KEY, false)
            state.isSelfUpdate.set(false)
        }
    }
}
