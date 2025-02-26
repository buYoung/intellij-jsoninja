package com.livteam.jsonhelper2.toolWindow.component

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.livteam.jsonhelper2.model.JsonFormatState
import com.livteam.jsonhelper2.toolWindow.actions.JsonHelperActionGroup
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter

class JsonHelperPanel(private val project: Project) : SimpleToolWindowPanel(false, true), DataProvider {
    val tabbedPane = JBTabbedPane()
    private val jmesPathComponent = JmesPathComponent(project)
    private var tabCounter = 1
    
    // JSON 포맷 상태 (기본값: PRETTIFY)
    private var jsonFormatState = JsonFormatState.DEFAULT

    init {
        // 초기 포맷 상태를 PRETTIFY로 명시적 설정
        jsonFormatState = JsonFormatState.PRETTIFY
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
        jmesPathComponent.setParentPanel(this)
        jmesPathComponent.setOnSearchCallback { originalJson, resultJson ->
            val currentEditor = getCurrentJsonEditor()
            if (currentEditor != null) {
                val formattedJson = formatJson(resultJson, jsonFormatState)
                // 포맷팅 없이 결과 그대로 표시
                currentEditor.setText(formattedJson)
                // 원본 JSON 저장
                currentEditor.setOriginalJson(originalJson)
            }
        }
    }

    /**
     * JSON 문자열을 지정된 포맷 상태에 따라 포맷팅
     * @param json 포맷팅할 JSON 문자열
     * @param formatState 포맷 상태
     * @return 포맷팅된 JSON 문자열
     */
    private fun formatJson(json: String, formatState: JsonFormatState): String {
        if (json.isEmpty()) return json
        
        return try {
            val mapper = ObjectMapper()
            val jsonNode = mapper.readTree(json)
            
            val result = when (formatState) {
                JsonFormatState.PRETTIFY -> {
                    // 커스텀 PrettyPrinter 생성
                    val prettyPrinter = CustomPrettyPrinter()
                    mapper.writer(prettyPrinter).writeValueAsString(jsonNode)
                }
                JsonFormatState.UGLIFY -> {
                    mapper.writeValueAsString(jsonNode)
                }
            }
            
            result
        } catch (e: Exception) {
            // 포맷팅 실패 시 원본 반환
            json
        }
    }
    
    /**
     * 커스텀 PrettyPrinter 클래스
     * 콜론(:) 주변의 공백을 제거하고 들여쓰기와 줄바꿈을 조정합니다.
     */
    private class CustomPrettyPrinter : DefaultPrettyPrinter() {
        init {
            // 들여쓰기 설정 (2칸 공백)
            val indenter = DefaultIndenter("  ", "\n")
            indentArraysWith(indenter)
            indentObjectsWith(indenter)
            
            // 객체 필드 출력 설정 (콜론 뒤에만 공백)
            _objectFieldValueSeparatorWithSpaces = ": "
        }
        
        // 복제 메서드 (Jackson 내부에서 사용)
        override fun createInstance(): DefaultPrettyPrinter {
            return CustomPrettyPrinter()
        }
    }

    private fun createEditor(): JsonEditor {
        return JsonEditor(project).apply {
            setText("")
            // 에디터 내용 변경 콜백 설정
            setOnContentChangeCallback { content ->
                jmesPathComponent.setOriginalJson(content)
            }
        }
    }

    private fun createToolbar(): JComponent {
        val actionGroup = JsonHelperActionGroup()
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
        val currentEditor = getCurrentJsonEditor()
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
    private fun getCurrentJsonEditor(): JsonEditor? {
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

    companion object {
        val DATA_KEY = DataKey.create<JsonHelperPanel>("JsonHelperPanel")
    }
}
