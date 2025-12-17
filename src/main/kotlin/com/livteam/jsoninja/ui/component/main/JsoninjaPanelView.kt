package com.livteam.jsoninja.ui.component.main

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.livteam.jsoninja.actions.*
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.ui.component.tab.JsonTabsPresenter
import com.livteam.jsoninja.ui.component.tab.JsonTabsView
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

class JsoninjaPanelView(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {
    private val tabsView = JsonTabsView()
    private val tabsPresenter = JsonTabsPresenter(project, this, tabsView)

    val presenter = JsoninjaPanelPresenter(project, this, tabsPresenter)

    init {
        setupUI()
    }

    private fun setupUI() {
        // 초기 탭 추가
        tabsPresenter.setupInitialTabs()

        // Add content
        val contentPanel = JPanel(BorderLayout()).apply {
            add(JSeparator(SwingConstants.VERTICAL), BorderLayout.WEST)
            add(tabsView, BorderLayout.CENTER)
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
        return presenter.getCurrentEditor()
    }

    /**
     * tabs Presenter 반환
     * @return JsonTabsPresenter 인스턴스
     */
    fun getTabsPresenter(): JsonTabsPresenter {
        return presenter.getTabsPresenter()
    }

    /**
     * 새 탭 추가
     * @param content 초기 내용
     * @param fileExtension 파일 확장자
     */
    fun addNewTab(content: String = "", fileExtension: String? = null) {
        presenter.addNewTab(content, fileExtension)
    }

    /**
     * JSON 포맷 상태 설정
     * @param state 설정할 JSON 포맷 상태
     */
    fun setJsonFormatState(state: JsonFormatState) {
        presenter.setJsonFormatState(state)
    }

    /**
     * 현재 JSON 포맷 상태 반환
     * @return 현재 JSON 포맷 상태
     */
    fun getJsonFormatState(): JsonFormatState {
        return presenter.getJsonFormatState()
    }

    /**
     * 현재 선택된 에디터의 JSON을 지정된 포맷 상태로 포맷팅합니다.
     *
     * @param formatState 포맷 상태
     */
    fun formatJson(formatState: JsonFormatState) {
        presenter.formatJson(formatState)
    }

    /**
     * 현재 선택된 에디터의 JSON을 기본 설정에 맞춰 포맷합니다.
     */
    fun formatJson() {
        presenter.formatJson()
    }

    /**
     * 현재 선택된 에디터의 JSON을 이스케이프 처리합니다.
     */
    fun escapeJson() {
        presenter.escapeJson()
    }

    /**
     * 현재 선택된 에디터의 이스케이프 처리된 JSON을 원래대로 되돌립니다.
     */
    fun unescapeJson() {
        presenter.unescapeJson()
    }

    fun setRandomJsonData(data: String, skipFormatting: Boolean = false) {
        presenter.setRandomJsonData(data, skipFormatting)
    }

    /**
     * 현재 에디터의 텍스트를 처리하는 공통 메서드 (Presenter에서 호출)
     *
     * @param processor 문자열 처리 함수
     */
    fun processEditorText(processor: (String) -> String) {
        val currentEditor = getCurrentEditor() ?: return
        val jsonText = currentEditor.getText()
        val trimedJsonText = jsonText.trim()
        val isJsonTextEmpty = trimedJsonText.isBlank() || trimedJsonText.isEmpty()

        if (isJsonTextEmpty) return

        val processedJson = processor(jsonText)

        currentEditor.setText(processedJson)
    }

    fun updateEditorText(editor: JsonEditorView, text: String) {
        editor.setText(text)
    }

    override fun dispose() {
        // JsonHelperTabbedPane와 하위 탭 Disposable 들의 부모 역할만 수행하므로 별도 작업 없음
    }
}
