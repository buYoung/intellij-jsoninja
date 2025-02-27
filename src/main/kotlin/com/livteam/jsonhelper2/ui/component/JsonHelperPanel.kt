package com.livteam.jsonhelper2.ui.component

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.livteam.jsonhelper2.model.JsonFormatState
import com.livteam.jsonhelper2.ui.component.JsonHelperActionBar
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.livteam.jsonhelper2.services.JsonFormatterService

class JsonHelperPanel(private val project: Project) : SimpleToolWindowPanel(false, true), DataProvider {
    val tabbedPane = JBTabbedPane()
    private val jmesPathComponent = JmesPathComponent(project)
    private var tabCounter = 1
    
    // JSON 포맷 상태
    private var jsonFormatState = JsonFormatState.PRETTIFY

    // JMES 쿼리 진행 중인지 여부
    private var isJmesQueryInProgress = false

    // JsonFormatterService 인스턴스 (한 번만 가져와서 재사용)
    private val formatterService = project.getService(JsonFormatterService::class.java)

    init {
        setupUI()
        setupJmesPathComponent()
    }

    private fun setupUI() {
        // 초기 탭 추가
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().invokeLater({
                runWriteAction {
                    addNewTab()
                }
            }, ModalityState.any())
        }
        
        // 탭 변경 리스너 추가
        tabbedPane.addChangeListener { e ->
            updateJmesPathOriginalJson()
        }
        
        // Add content
        val contentPanel = JPanel(BorderLayout()).apply {
            add(jmesPathComponent.getComponent(), BorderLayout.NORTH)
            add(tabbedPane, BorderLayout.CENTER)
        }
        
        // Setup toolbar and content
        toolbar = createToolbar()
        setContent(contentPanel)
    }

    /**
     * JMESPath 컴포넌트 설정
     */
    private fun setupJmesPathComponent() {
        // 쿼리 수행 전 이벤트
        jmesPathComponent.setOnBeforeSearchCallback {
            // 원본 JSON이 없으면 현재 에디터에서 가져옴
            if (jmesPathComponent.hasOriginalJson()) return@setOnBeforeSearchCallback


            val currentEditor = getCurrentEditor() ?: return@setOnBeforeSearchCallback

            val editorText = currentEditor.getText()
            if (editorText.isNotEmpty()) {
                jmesPathComponent.setOriginalJson(editorText)
                // 원본 JSON도 설정
                currentEditor.setOriginalJson(editorText)
            }
        }

        // 쿼리 결과 처리 이벤트
        jmesPathComponent.setOnSearchCallback { originalJson, resultJson ->
            val currentEditor = getCurrentEditor()
            if (currentEditor != null) {
                // JMES 쿼리 진행 중 상태로 설정
                isJmesQueryInProgress = true
                try {
                    val formattedJson = formatterService.formatJson(resultJson, jsonFormatState)
                    // 포맷팅 없이 결과 그대로 표시
                    currentEditor.setText(formattedJson)
                    // 원본 JSON 저장
                    currentEditor.setOriginalJson(originalJson)
                } finally {
                    // 쿼리 처리 완료 후 상태 복원
                    isJmesQueryInProgress = false
                }
            }
        }
    }

    private fun createEditor(): JsonEditor {
        return JsonEditor(project).apply {
            setText("")
        }
    }

    private fun createToolbar(): JComponent {
        val actionGroup = JsonHelperActionBar()
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("JsonHelperToolbar", actionGroup, true)
        actionToolbar.targetComponent = this
        return actionToolbar.component
    }

    fun addNewTab(content: String = "") {
        val editor = createEditor()
        if (content.isNotEmpty()) {
            editor.setText(content)
            // 새 탭이 추가될 때 JMESPath 컴포넌트에 원본 JSON 설정
            jmesPathComponent.setOriginalJson(content)
        }
        
        val title = "JSON ${tabCounter++}"
        val scrollPane = JBScrollPane(editor)
        tabbedPane.addTab(title, scrollPane)
        tabbedPane.selectedIndex = tabbedPane.tabCount - 1
    }

    /**
     * JMESPath 컴포넌트에 현재 에디터의 원본 JSON 업데이트
     */
    private fun updateJmesPathOriginalJson() {
        // JMES 쿼리 진행 중에는 원본 JSON 업데이트하지 않음
        if (isJmesQueryInProgress) return

        val currentEditor = getCurrentEditor()
        if (currentEditor != null) {
            val json = currentEditor.getText()
            if (json.isNotEmpty()) {
                // 원본 JSON 저장
                currentEditor.setOriginalJson(json)
                // JMESPath 컴포넌트에 원본 JSON 설정
                jmesPathComponent.setOriginalJson(json)
            }
        }
    }

    /**
     * 현재 선택된 탭의 에디터 반환
     * @return 현재 선택된 탭의 에디터
     */
    fun getCurrentEditor(): JsonEditor? {
        val currentIndex = tabbedPane.selectedIndex
        if (currentIndex >= 0) {
            val scrollPane = tabbedPane.getComponentAt(currentIndex) as? JBScrollPane
            return scrollPane?.viewport?.view as? JsonEditor
        }
        return null
    }

    /**
     * JSON 포맷 상태 설정
     * @param state 설정할 JSON 포맷 상태
     */
    fun setJsonFormatState(state: JsonFormatState) {
        jsonFormatState = state
    }

    /**
     * 현재 JSON 포맷 상태 반환
     * @return 현재 JSON 포맷 상태
     */
    fun getJsonFormatState(): JsonFormatState {
        return jsonFormatState
    }

    override fun getData(dataId: String): Any? {
        return when {
            DATA_KEY.`is`(dataId) -> this
            else -> null
        }
    }

    /**
     * 현재 에디터의 텍스트를 처리하는 공통 메서드
     *
     * @param processor 문자열 처리 함수
     */
    private fun processEditorText(processor: (String) -> String) {
        val currentEditor = getCurrentEditor() ?: return
        val jsonText = currentEditor.getText()

        if (jsonText.isBlank()) return

        val processedJson = processor(jsonText)

        // 에디터에 처리된 JSON 설정
        ApplicationManager.getApplication().runWriteAction {
            currentEditor.setText(processedJson)
        }
    }

    /**
     * 현재 선택된 에디터의 JSON을 지정된 포맷 상태로 포맷팅합니다.
     *
     * @param formatState 포맷 상태
     */
    fun formatJson(formatState: JsonFormatState) {
        processEditorText { jsonText ->
            // 텍스트에 이스케이프 문자열이 있는지 확인
            val textToFormat = if (formatterService.containsEscapeCharacters(jsonText)) {
                // 이스케이프 문자열이 있으면 완전히 언이스케이프 수행 (다중 이스케이프 처리 가능)
                formatterService.fullyUnescapeJson(jsonText)
            } else {
                jsonText
            }

            // 언이스케이프된 텍스트로 포맷팅 수행
            formatterService.formatJson(textToFormat, formatState)
        }
    }

    /**
     * 현재 선택된 에디터의 JSON을 이스케이프 처리합니다.
     */
    fun escapeJson() {
        processEditorText { jsonText ->
            formatterService.escapeJson(jsonText)
        }
    }

    /**
     * 현재 선택된 에디터의 이스케이프 처리된 JSON을 원래대로 되돌립니다.
     */
    fun unescapeJson() {
        processEditorText { jsonText ->
            formatterService.unescapeJson(jsonText)
        }
    }

    companion object {
        val DATA_KEY = DataKey.create<JsonHelperPanel>("JsonHelperPanel")
    }
}
