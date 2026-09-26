package com.livteam.jsoninja.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.Side
import com.intellij.json.JsonFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.dialog.LargeFileWarningDialog
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 자동 JSON 포맷팅을 제공하는 JSON diff viewer extension.
 *
 * 이 extension은 diff viewer에서 JSON content를 감지하고 다음 성능 최적화를 포함한
 * 자동 포맷팅을 적용합니다:
 * - 조기 종료를 통한 빠른 JSON content 감지
 * - memory leak 방지를 위한 document별 감지 결과 cache
 * - 과도한 처리를 방지하는 debounced 포맷팅
 * - 재진입 update 보호
 *
 * 정렬 선택은 viewer 생성 시점 값이 아니라 request의 [JsonDiffSession]에서 매번 읽습니다.
 *
 * Threading: document 변경은 EDT의 WriteCommandAction에서 수행합니다.
 * 무거운 JSON parsing은 background coroutine으로 이동됩니다.
 */
class JsonDiffExtension : DiffExtension() {

    private object Constants {
        const val DEBOUNCE_DELAY = 300 // milliseconds
        const val SMALL_EDIT_THRESHOLD = 3 // characters
        val CHANGE_GUARD_KEY = JsonDiffKeys.JSON_DIFF_CHANGE_GUARD
    }

    /**
     * memory leak을 방지하기 위한 경량 document별 state.
     * documentStates map을 통해 동기화된 access가 이루어져야 합니다.
     */
    private data class DocumentState(
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
        val session = resolveSession(request, editors, settings)
        val projectName = project.name
        val isViewerDisposed = AtomicBoolean(false)
        Disposer.register(viewer) { isViewerDisposed.set(true) }

        project.service<JsoninjaCoroutineScopeService>().launch {
            if (project.isDisposed || isViewerDisposed.get()) return@launch

            val startTime = System.currentTimeMillis()
            val jsonContentResults = withContext(Dispatchers.Default) {
                editors.map { editor ->
                    isJsonContent(editor, formatterService, projectName, settings) to editor
                }
            }
            val detectionTime = System.currentTimeMillis() - startTime

            withContext(Dispatchers.EDT) {
                if (project.isDisposed || isViewerDisposed.get() || editors.any { it.isDisposed }) return@withContext

                if (LOG.isDebugEnabled) {
                    LOG.debug("JSON detection completed in ${detectionTime}ms for project '$projectName'")
                }

                val isJsonDiff = jsonContentResults.all { it.first }
                if (!isJsonDiff) {
                    LOG.debug("Not all editors contain JSON content, skipping JSON diff extension")
                    return@withContext
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
                            return@withContext
                        }

                        LOG.debug("User confirmed large file JSON diff processing for '$fileName' (${largestFileSize / (1024 * 1024)} MB)")
                    }
                }

                // 양쪽 document를 한 쌍으로 따라가는 listener 설치
                installSessionAutoFormatter(project, session, editors, viewer, isViewerDisposed)

                LOG.debug("JSON diff extension activated for project '$projectName' with ${editors.size} editors")
            }
        }
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
            val fileType = editor.virtualFile?.fileType
            // JSON5 파일 타입 이름은 플러그인마다 다를 수 있으므로 확장자도 확인
            val isJson5 = fileType?.name?.equals("JSON5", ignoreCase = true) == true ||
                    fileType?.defaultExtension == "json5" ||
                    editor.virtualFile?.extension?.equals("json5", ignoreCase = true) == true

            if (fileType == JsonFileType.INSTANCE || isJson5) {
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
     * request에 담긴 세션을 사용합니다. 세션 없이 표시된 JSONinja request는 기존 Boolean 설정으로 임시 세션을 만듭니다.
     */
    private fun resolveSession(
        request: DiffRequest,
        editors: List<Editor>,
        settings: JsoninjaSettingsState
    ): JsonDiffSession {
        val leftDocument = editors[0].document
        val rightDocument = editors[1].document
        val requestSession = request.getUserData(JsonDiffKeys.JSON_DIFF_SESSION)
        if (requestSession != null &&
            requestSession.leftDocument === leftDocument &&
            requestSession.rightDocument === rightDocument
        ) {
            return requestSession
        }

        val shouldAutoSort = request.getUserData(JsonDiffKeys.JSON_DIFF_SORT_KEYS) ?: settings.diffSortKeys
        return JsonDiffSession(leftDocument, rightDocument, shouldAutoSort)
    }

    /**
     * 양쪽 document 변경을 하나의 debounced 작업으로 모아 세션의 현재 정렬 정책을 적용합니다.
     * 한쪽 편집이 다른 쪽 정렬 결과를 바꿀 수 있으므로 쪽별 독립 listener를 두지 않습니다.
     *
     * @param project project context
     * @param session 현재 정렬 선택과 작업 세대를 가진 비교 세션
     * @param editors 왼쪽/오른쪽 diff editor
     * @param viewer disposal 등록을 위한 diff viewer
     */
    private fun installSessionAutoFormatter(
        project: Project,
        session: JsonDiffSession,
        editors: List<Editor>,
        viewer: FrameDiffTool.DiffViewer,
        isViewerDisposed: AtomicBoolean
    ) {
        val diffService = project.service<JsonDiffService>()
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, viewer)
        val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
        val pendingSides = EnumSet.noneOf(Side::class.java)
        var formattingJob: Job? = null
        val isHostActive = { !project.isDisposed && !isViewerDisposed.get() && editors.none { it.isDisposed } }

        // EDT에서만 호출된다.
        fun launchFormatting() {
            if (project.isDisposed || pendingSides.isEmpty()) return
            val changedSides = EnumSet.copyOf(pendingSides)
            pendingSides.clear()

            formattingJob?.cancel()
            formattingJob = coroutineScope.launch {
                diffService.formatAutomatically(session, changedSides, isHostActive)
            }
        }

        fun installDocumentListener(document: Document, side: Side) {
            val fileName = editors[side.index].virtualFile?.name ?: "<${side.name.lowercase()}>"
            val documentListener = object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    // 이 변경이 JSONinja 정렬/복원 쓰기에서 발생한 경우 스킵
                    if (document.getUserData(Constants.CHANGE_GUARD_KEY) == true) {
                        LOG.debug("Skipping self-update for '$fileName'")
                        return
                    }

                    // 소규모 공백 전용 편집 스킵
                    val editSize = abs(event.newLength - event.oldLength)
                    if (editSize <= Constants.SMALL_EDIT_THRESHOLD) {
                        val changedText = event.newFragment.toString()
                        if (changedText.isBlank() || changedText.all { it.isWhitespace() }) {
                            LOG.debug("Skipping small whitespace edit ($editSize chars) for '$fileName'")
                            return
                        }
                    }

                    // 대용량 파일은 viewer 생성 시점에서 warning dialog이 처리
                    pendingSides.add(side)

                    // 대기 중인 포맷팅 취소 후 debounce로 새로운 포맷팅 예약
                    alarm.cancelAllRequests()
                    alarm.addRequest({ launchFormatting() }, Constants.DEBOUNCE_DELAY)
                }
            }
            document.addDocumentListener(documentListener, viewer)
        }

        installDocumentListener(session.leftDocument, Side.LEFT)
        installDocumentListener(session.rightDocument, Side.RIGHT)

        // 옵션 변경, 수동 정렬, 복원, 입력 교체는 대기 중인 이전 정책 작업을 버린다.
        session.addPendingWorkListener(viewer) {
            alarm.cancelAllRequests()
            pendingSides.clear()
            formattingJob?.cancel()
        }

        // viewer가 dispose될 때 대기 작업 정리
        Disposer.register(viewer) {
            alarm.cancelAllRequests()
            coroutineScope.cancel()
            LOG.debug("Disposed JSON diff extension")
        }

        // content가 있으면 초기 포맷팅 수행
        if (session.leftDocument.text.isNotBlank()) pendingSides.add(Side.LEFT)
        if (session.rightDocument.text.isNotBlank()) pendingSides.add(Side.RIGHT)
        launchFormatting()
    }
}
