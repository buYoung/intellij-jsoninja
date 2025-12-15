package com.livteam.jsoninja.ui.component

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.actions.*
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonHelperService
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

class JsonHelperPanel(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {
    private val tabbedPane = JsonHelperTabbedPane(project, this, this)
    private var isJmesQueryInProgress = false
    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val helperService = project.getService(JsonHelperService::class.java)

    init {
        setupUI()
    }

    private fun setupUI() {
        // 초기 탭 추가
        tabbedPane.setupInitialTabs()

        // Add content
        val contentPanel = JPanel(BorderLayout()).apply {
            add(JSeparator(SwingConstants.VERTICAL), BorderLayout.WEST)
            add(tabbedPane, BorderLayout.CENTER)
        }

        // Setup toolbar and content
        toolbar = createToolbar()
        setContent(contentPanel)
    }

    private fun createToolbar(): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            isPopup = true
            
            // 기본 액션 추가
            add(AddTabAction())
            add(OpenJsonFileAction())

            addSeparator()

            // JSON 변환 관련 액션 추가
            add(PrettifyJsonAction())
            add(UglifyJsonAction())
            addSeparator()
            add(EscapeJsonAction())
            add(UnescapeJsonAction())
            addSeparator()
            add(GenerateRandomJsonAction())
            // JSON Diff 액션 추가
            add(ShowJsonDiffAction())
        }
        
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("JsonHelperToolbar", actionGroup, true)

        actionToolbar.targetComponent = this

        return actionToolbar.component
    }

    /**
     * 현재 선택된 탭의 에디터 반환
     * @return 현재 선택된 탭의 에디터
     */
    fun getCurrentEditor(): JsonEditorView? {
        return tabbedPane.getCurrentEditor()
    }

    /**
     * TabbedPane 컴포넌트 반환
     * @return JsonHelperTabbedPane 인스턴스
     */
    fun getTabbedPane(): JsonHelperTabbedPane {
        return tabbedPane
    }

    /**
     * 새 탭 추가
     * @param content 초기 내용
     * @param fileExtension 파일 확장자
     */
    fun addNewTab(content: String = "", fileExtension: String? = null) {
        tabbedPane.addNewTabFromPlusTab(content, fileExtension)
    }

    /**
     * JSON 포맷 상태 설정
     * @param state 설정할 JSON 포맷 상태
     */
    fun setJsonFormatState(state: JsonFormatState) {
        helperService.setJsonFormatState(state)
    }

    /**
     * 현재 JSON 포맷 상태 반환
     * @return 현재 JSON 포맷 상태
     */
    fun getJsonFormatState(): JsonFormatState {
        return helperService.getJsonFormatState()
    }

    /**
     * 현재 에디터의 텍스트를 처리하는 공통 메서드
     *
     * @param processor 문자열 처리 함수
     */
    private fun processEditorText(processor: (String) -> String) {
        val currentEditor = getCurrentEditor() ?: return
        val jsonText = currentEditor.getText()
        val trimedJsonText = jsonText.trim()
        val isJsonTextEmpty = trimedJsonText.isBlank() || trimedJsonText.isEmpty()

        if (isJsonTextEmpty) return

        val processedJson = processor(jsonText)

        // 에디터에 처리된 JSON 설정
        WriteCommandAction.runWriteCommandAction(project) {
            currentEditor.setText(processedJson)
        }
    }

    fun setRandomJsonData(data: String, skipFormatting: Boolean = false) {
        val currentEditor = getCurrentEditor() ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            val processedJson = if (skipFormatting) {
                data
            } else {
                formatterService.formatJson(data, getJsonFormatState())
            }
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
     * 현재 선택된 에디터의 JSON을 기본 설정에 맞춰 포맷합니다.
     */
    fun formatJson() {
        formatJson(getJsonFormatState())
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

    override fun dispose() {
        // JsonHelperTabbedPane와 하위 탭 Disposable 들의 부모 역할만 수행하므로 별도 작업 없음
    }
}
