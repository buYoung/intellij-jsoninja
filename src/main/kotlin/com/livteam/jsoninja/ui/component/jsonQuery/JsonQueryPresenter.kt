package com.livteam.jsoninja.ui.component.jsonQuery

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
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
import javax.swing.JComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * JMESPath 검색 로직을 담당하는 Presenter
 * 사용자 입력을 받아 비즈니스 로직을 처리하고 View를 업데이트
 */
class JsonQueryPresenter(private val project: Project, private val model: JsonQueryUiState) : Disposable {
    private val LOG = logger<JsonQueryPresenter>()
    private val view = JsonQueryView()

    private val jsonQueryService = project.getService(JsonQueryService::class.java)
    private val jsonFormatterService = project.getService(JsonFormatterService::class.java)
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()

    @Volatile
    private var isDisposed = false

    private var onSearchCallback: ((String, String) -> Unit)? = null
    private var onBeforeSearchCallback: (() -> Unit)? = null

    private val messageBusConnection = project.messageBus.connect()

    init {
        setupKeyListener()
        refreshQueryType()
        messageBusConnection.subscribe(JsoninjaSettingsListener.TOPIC, object : JsoninjaSettingsListener {
            override fun onSettingsChanged(settings: JsoninjaSettingsState) {
                if (isDisposed) return
                refreshQueryType()
            }
        })
    }

    fun refreshQueryType() {
        val settings = JsoninjaSettingsState.getInstance(project)
        val queryType = JsonQueryType.fromString(settings.jsonQueryType)
        view.updatePlaceholder(queryType)
    }

    /**
     * 엔터 키 입력 처리를 위한 키 리스너 설정
     */
    private fun setupKeyListener() {
        view.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                // 엔터 키 처리
                if (e.keyCode == VK_ENTER) {
                    val query = view.query
                    // 쿼리가 비어있으면 원본 JSON으로 돌아감
                    if (query.isEmpty()) {
                        model.lastQuery = ""

                        coroutineScope.launch {
                            withContext(Dispatchers.EDT) {
                                if (isDisposed) return@withContext
                                onSearchCallback?.invoke(model.originalJson, model.originalJson)
                            }
                        }
                    } else {
                        model.lastQuery = query
                        performSearch(query)
                    }
                }
            }
        })
    }

    /**
     * JMESPath 검색 실행
     */
    private fun performSearch(query: String) {
        if (isDisposed) return
        val originalJsonTrim = model.originalJson.trim()
        val isOriginalJsonEmpty = originalJsonTrim.isBlank() || originalJsonTrim.isEmpty()
        // 검색 전 콜백 호출
        onBeforeSearchCallback?.invoke()

        // 원본 JSON이 비어있으면 검색 중단
        if (isOriginalJsonEmpty || !isValidJson(model.originalJson)) {
            LOG.warn("원본 JSON이 비어있거나 유효하지 않습니다.")
            return
        }

        // 입력값이 없으면 원본 JSON을 보여줌
        if (query.isEmpty()) {
            onSearchCallback?.invoke(model.originalJson, model.originalJson)
            return
        }

        val originalJson = model.originalJson
        coroutineScope.launch {
            try {
                if (isDisposed) return@launch
                // 쿼리 유효성 먼저 검사
                if (!jsonQueryService.isValidExpression(query)) {
                    LOG.warn("유효하지 않은 쿼리 표현식: $query")
                    return@launch
                }

                val result = withContext(Dispatchers.Default) {
                    jsonQueryService.query(originalJson, query)
                }

                withContext(Dispatchers.EDT) {
                    if (isDisposed) return@withContext
                    if (result == null) {
                        return@withContext
                    }

                    if (result.isEmpty()) {
                        // 결과가 비어있어도 콜백은 호출하여 UI가 현재 결과를 반영하도록 함
                        LOG.warn("쿼리 결과가 없습니다: $query")
                    }

                    onSearchCallback?.invoke(originalJson, result)
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (e: Exception) {
                LOG.error("쿼리 실행 중 오류 발생", e)
                // 예외 발생 시에도 이전 상태 유지
            }
        }
    }

    /**
     * JSON 문자열이 유효한지 확인합니다.
     * @param json 검사할 JSON 문자열
     * @return 유효성 여부
     */
    private fun isValidJson(json: String): Boolean {
        return jsonFormatterService.isValidJson(json)
    }

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

    /**
     * 원본 JSON 설정
     * @param json 원본 JSON 문자열
     */
    fun setOriginalJson(json: String) {
        if (isDisposed) return
        // 원본 JSON이 변경되지 않았으면 아무 작업도 하지 않음
        if (model.originalJson == json) {
            return
        }

        model.originalJson = json

        // 원본 JSON이 변경되면 현재 쿼리를 다시 실행
        val currentQuery = view.query
        if (currentQuery.isNotEmpty()) {
            model.lastQuery = currentQuery
            performSearch(currentQuery)
        } else if (json.isNotEmpty() && isValidJson(json)) {
            // 쿼리가 비어있으면 원본 JSON을 표시
            onSearchCallback?.invoke(json, json)
        }
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
        coroutineScope.cancel()
        messageBusConnection.disconnect()
    }
}
