package com.livteam.jsoninja.ui.component.jsonQuery

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonQueryService
import com.livteam.jsoninja.ui.component.model.JsonQueryModel
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import javax.swing.JComponent

/**
 * JMESPath 검색 로직을 담당하는 Presenter
 * 사용자 입력을 받아 비즈니스 로직을 처리하고 View를 업데이트
 */
class JsonQueryPresenter(private val project: Project, private val model: JsonQueryModel) {
    private val LOG = logger<JsonQueryPresenter>()
    private val view = JsonQueryView()

    private val jsonQueryService = project.getService(JsonQueryService::class.java)
    private val jsonFormatterService = project.getService(JsonFormatterService::class.java)

    private var onSearchCallback: ((String, String) -> Unit)? = null
    private var onBeforeSearchCallback: (() -> Unit)? = null

    init {
        setupKeyListener()
    }

    /**
     * 엔터 키 입력 및 붙여넣기 이벤트 처리를 위한 키 리스너 설정
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

                        invokeLater(ModalityState.any()) {
                            onSearchCallback?.invoke(model.originalJson, model.originalJson)
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

        // 백그라운드 스레드에서 쿼리 실행
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // 쿼리 유효성 먼저 검사
                if (!jsonQueryService.isValidExpression(query)) {
                    LOG.warn("유효하지 않은 쿼리 표현식: $query")
                    return@executeOnPooledThread
                }

                val result = jsonQueryService.query(model.originalJson, query)

                // UI 업데이트는 EDT에서 수행
                invokeLater(ModalityState.any()) {
                    if (result == null) {
                        return@invokeLater
                    }

                    if (result.isEmpty()) {
                        // 결과가 null인 경우(쿼리 실패) 아무 작업도 수행하지 않음
                        // 이전 상태를 유지하기 위해 콜백을 호출하지 않음
                        LOG.warn("쿼리 결과가 없습니다: $query")
                    }

                    // 결과가 있는 경우만 출력 업데이트
                    onSearchCallback?.invoke(model.originalJson, result)
                }
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
}
