package com.livteam.jsoninja.ui.component.jsonQuery

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.JsonQueryService
import com.livteam.jsoninja.settings.JsoninjaSettingsListener
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.component.model.JsonQueryUiState
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * JSON Query 검색 로직을 담당하는 Presenter
 * 사용자 입력을 받아 비즈니스 로직을 처리하고 View를 업데이트
 */
class JsonQueryPresenter(private val project: Project, private val model: JsonQueryUiState) : Disposable {
    private val LOG = logger<JsonQueryPresenter>()
    private val view = JsonQueryView(this)

    private val jsonQueryService = project.getService(JsonQueryService::class.java)
    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()

    @Volatile
    private var isDisposed = false

    private var onSearchCallback: ((String, String) -> Unit)? = null
    private var onBeforeSearchCallback: (() -> Unit)? = null
    private var onRestoreOriginalCallback: ((String) -> Unit)? = null
    private var onFilterAppliedCallback: (() -> Unit)? = null
    private var onFocusEditorRequested: (() -> Unit)? = null
    private val searchSequence = AtomicInteger()

    private val messageBusConnection = project.messageBus.connect(this)

    init {
        setupViewCallbacks()
        setupKeyListener()
        refreshQueryType()
        messageBusConnection.subscribe(JsoninjaSettingsListener.TOPIC, object : JsoninjaSettingsListener {
            override fun onSettingsChanged(settings: JsoninjaSettingsState) {
                if (isDisposed) return
                refreshQueryType()
            }
        })
    }

    private fun setupViewCallbacks() {
        view.setOnFieldClearedCallback {
            restoreOriginalJson(clearField = false)
        }
        view.setOnQueryTypeSelectedCallback { selectedType ->
            if (isDisposed || project.isDisposed) return@setOnQueryTypeSelectedCallback
            val settings = JsoninjaSettingsState.getInstance(project)
            if (settings.jsonQueryType != selectedType.name) {
                settings.jsonQueryType = selectedType.name
                project.messageBus.syncPublisher(JsoninjaSettingsListener.TOPIC).onSettingsChanged(settings)
            }
            view.focusQueryField()
        }
    }

    fun refreshQueryType() {
        invalidatePendingSearch()
        val settings = JsoninjaSettingsState.getInstance(project)
        val queryType = JsonQueryType.fromString(settings.jsonQueryType)
        view.updatePlaceholder(queryType)
    }

    /**
     * 엔터 / ESC 키 입력 처리를 위한 키 리스너 설정
     */
    private fun setupKeyListener() {
        view.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    VK_ENTER -> {
                        val query = view.query
                        if (query.isBlank()) {
                            restoreOriginalJson(clearField = false)
                        } else {
                            performSearch(query)
                        }
                    }

                    VK_ESCAPE -> {
                        e.consume()
                        if (isFiltered() || view.query.isNotEmpty()) {
                            restoreOriginalJson(clearField = true)
                        }
                        onFocusEditorRequested?.invoke()
                    }
                }
            }
        })
    }

    /**
     * JSON Query 검색 실행
     */
    private fun performSearch(query: String) {
        if (isDisposed) return
        view.clearValidationError()
        // 검색 전 콜백 호출
        onBeforeSearchCallback?.invoke()
        val sequenceNumber = searchSequence.incrementAndGet()
        val originalJson = model.originalJson

        // 원본 JSON이 비어있으면 검색 중단
        if (originalJson.isBlank()) {
            LOG.warn("원본 JSON이 비어있거나 유효하지 않습니다.")
            view.showValidationError(LocalizationBundle.message("query.error.invalid.json"))
            return
        }

        coroutineScope.launch {
            try {
                if (isDisposed) return@launch
                val outcome = withContext(Dispatchers.Default) {
                    evaluateQuery(originalJson, query)
                }

                withContext(Dispatchers.EDT) {
                    if (isDisposed) return@withContext
                    if (searchSequence.get() != sequenceNumber) return@withContext

                    when (outcome) {
                        is QueryOutcome.Success -> {
                            model.lastQuery = query
                            view.clearValidationError()
                            view.addCurrentTextToHistory()
                            onSearchCallback?.invoke(originalJson, outcome.resultJson)
                        }

                        QueryOutcome.InvalidSyntax -> {
                            view.showValidationError(LocalizationBundle.message("query.error.invalid.syntax"))
                        }

                        QueryOutcome.InvalidJson -> {
                            view.showValidationError(LocalizationBundle.message("query.error.invalid.json"))
                        }

                        QueryOutcome.NoMatches -> {
                            view.showValidationError(LocalizationBundle.message("query.error.no.matches"))
                        }
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (e: Exception) {
                LOG.error("쿼리 실행 중 오류 발생", e)
                withContext(Dispatchers.EDT) {
                    if (!isDisposed && searchSequence.get() == sequenceNumber) {
                        view.showValidationError(LocalizationBundle.message("query.error.invalid.syntax"))
                    }
                }
            }
        }
    }

    private fun evaluateQuery(originalJson: String, query: String): QueryOutcome {
        if (!jsonQueryService.isValidExpression(query)) {
            return QueryOutcome.InvalidSyntax
        }
        val result = jsonQueryService.query(originalJson, query)
        if (!result.isNullOrEmpty()) {
            return QueryOutcome.Success(result)
        }
        return if (!formatterService.isValidJson(originalJson)) {
            QueryOutcome.InvalidJson
        } else {
            QueryOutcome.NoMatches
        }
    }

    fun restoreOriginalJson(clearField: Boolean = true) {
        if (isDisposed) return
        val shouldRestoreSource = model.lastQuery.isNotEmpty() && hasOriginalJson()
        val originalJson = model.originalJson
        model.lastQuery = ""
        model.originalJson = ""
        invalidatePendingSearch()
        view.clearValidationError()
        if (clearField && view.query.isNotEmpty()) {
            view.query = ""
        }
        if (shouldRestoreSource) {
            val restoreCallback = onRestoreOriginalCallback
            if (restoreCallback != null) {
                restoreCallback.invoke(originalJson)
            } else {
                onSearchCallback?.invoke(originalJson, originalJson)
            }
        }
    }

    fun applyFilteredResult() {
        if (isDisposed) return
        model.lastQuery = ""
        model.originalJson = ""
        invalidatePendingSearch()
        view.clearValidationError()
        if (view.query.isNotEmpty()) {
            view.query = ""
        }
        onFilterAppliedCallback?.invoke()
    }

    fun isFiltered(): Boolean = model.lastQuery.isNotEmpty() && hasOriginalJson()

    fun getActiveQuery(): String = model.lastQuery

    /**
     * 검색 결과 콜백 설정
     * @param callback 검색 결과 콜백 함수
     */
    fun setOnSearchCallback(callback: (String, String) -> Unit) {
        onSearchCallback = callback
    }

    /**
     * 검색 전 실행될 콜백 설정
     * @param callback 검색 전 콜백 함수
     */
    fun setOnBeforeSearchCallback(callback: () -> Unit) {
        onBeforeSearchCallback = callback
    }

    fun setOnRestoreOriginalCallback(callback: (String) -> Unit) {
        onRestoreOriginalCallback = callback
    }

    fun setOnFilterAppliedCallback(callback: () -> Unit) {
        onFilterAppliedCallback = callback
    }

    fun setOnFocusEditorRequested(callback: () -> Unit) {
        onFocusEditorRequested = callback
    }

    fun getCurrentRequestId(): Int = searchSequence.get()

    fun isCurrentRequest(requestId: Int): Boolean = !isDisposed && searchSequence.get() == requestId

    fun invalidatePendingSearch() {
        searchSequence.incrementAndGet()
    }

    /**
     * 원본 JSON 설정
     * @param json 원본 JSON 문자열
     */
    fun setOriginalJson(json: String) {
        if (isDisposed) return
        // Query display writes are excluded by the tab owner. Every other revision becomes the source.
        model.originalJson = json
        model.lastQuery = ""
        invalidatePendingSearch()
    }

    /**
     * 원본 JSON 데이터 비어있는지 확인합니다
     */
    fun hasOriginalJson(): Boolean {
        return model.hasOriginalJson()
    }

    /**
     * View의 컴포넌트 반환
     * @return JMESPath 입력 필드 컴포넌트
     */
    fun getComponent(): JComponent {
        return view.component
    }

    override fun dispose() {
        isDisposed = true
        invalidatePendingSearch()
        coroutineScope.cancel()
        view.dispose()
        messageBusConnection.disconnect()
    }

    private sealed interface QueryOutcome {
        data class Success(val resultJson: String) : QueryOutcome
        data object InvalidSyntax : QueryOutcome
        data object InvalidJson : QueryOutcome
        data object NoMatches : QueryOutcome
    }
}
