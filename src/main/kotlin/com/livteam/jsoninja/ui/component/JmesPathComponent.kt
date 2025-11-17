package com.livteam.jsoninja.ui.component

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.SearchTextField
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JMESPathService
import com.livteam.jsoninja.services.JsonFormatterService
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import java.awt.event.KeyEvent.*

/**
 * JMESPath 검색을 위한 컴포넌트
 * 사용자가 JMESPath 표현식을 입력하고 엔터 키를 눌러 검색할 수 있는 기능 제공
 */
class JmesPathComponent(private val project: Project) {
    private val LOG = logger<JmesPathComponent>()
    private val jmesPathField = SearchTextField()
    private var onSearchCallback: ((String, String) -> Unit)? = null
    private var onBeforeSearchCallback: (() -> Unit)? = null
    private var originalJson: String = ""
    private var lastQuery: String = ""
    private var parentPanel: JsonHelperPanel? = null

    private val jmesPathService = project.getService(JMESPathService::class.java)
    private val jsonFormatterService = project.getService(JsonFormatterService::class.java)

    init {
        jmesPathField.textEditor.emptyText.text = LocalizationBundle.message("jmesPathPlaceholder")
        setupKeyListener()
    }

    /**
     * 엔터 키 입력 및 붙여넣기 이벤트 처리를 위한 키 리스너 설정
     */
    private fun setupKeyListener() {
        jmesPathField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                // 엔터 키 처리
                if (e.keyCode == VK_ENTER) {
                    val query = jmesPathField.text.trim()
                    // 쿼리가 비어있으면 원본 JSON으로 돌아감
                    if (query.isEmpty()) {
                        lastQuery = ""

                        invokeLater(ModalityState.any()) {
                            onSearchCallback?.invoke(originalJson, originalJson)
                        }
                    } else {
                        lastQuery = query
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
        val originalJsonTrim = originalJson.trim()
        val isOriginalJsonEmpty = originalJsonTrim.isBlank() || originalJsonTrim.isEmpty()
        // 검색 전 콜백 호출
        onBeforeSearchCallback?.invoke()

        // 원본 JSON이 비어있으면 검색 중단
        if (isOriginalJsonEmpty || !isValidJson(originalJson)) {
            LOG.warn("원본 JSON이 비어있거나 유효하지 않습니다.")
            return
        }

        // 입력값이 없으면 원본 JSON을 보여줌
        if (query.isEmpty()) {
            onSearchCallback?.invoke(originalJson, originalJson)
            return
        }

        // 백그라운드 스레드에서 쿼리 실행
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // 쿼리 유효성 먼저 검사
                if (!jmesPathService.isValidExpression(query)) {
                    LOG.warn("유효하지 않은 JMESPath 표현식: $query")
                    return@executeOnPooledThread
                }

                val result = jmesPathService.query(originalJson, query)

                // UI 업데이트는 EDT에서 수행
                invokeLater(ModalityState.any()) {
                    if (result == null) {
                        return@invokeLater
                    }

                    if (result.isEmpty()) {
                        // 결과가 null인 경우(쿼리 실패) 아무 작업도 수행하지 않음
                        // 이전 상태를 유지하기 위해 콜백을 호출하지 않음
                        LOG.warn("JMESPath 쿼리 결과가 없습니다: $query")
                    }

                    // 결과가 있는 경우만 출력 업데이트
                    onSearchCallback?.invoke(originalJson, result)
                }
            } catch (e: Exception) {
                LOG.error("JMESPath 쿼리 실행 중 오류 발생", e)
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
     * 부모 패널 설정
     * @param panel JsonHelperPanel 인스턴스
     */
    fun setParentPanel(panel: JsonHelperPanel) {
        parentPanel = panel
    }

    /**
     * 원본 JSON 설정
     * @param json 원본 JSON 문자열
     */
    fun setOriginalJson(json: String) {
        // 원본 JSON이 변경되지 않았으면 아무 작업도 하지 않음
        if (originalJson == json) {
            return
        }

        originalJson = json

        // 원본 JSON이 변경되면 현재 쿼리를 다시 실행
        val currentQuery = jmesPathField.text.trim()
        if (currentQuery.isNotEmpty()) {
            lastQuery = currentQuery
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
        return originalJson.isNotEmpty()
    }

    /**
     * JMESPath 필드 컴포넌트 반환
     * @return JMESPath 입력 필드 컴포넌트
     */
    fun getComponent(): JComponent {
        return jmesPathField
    }
}
