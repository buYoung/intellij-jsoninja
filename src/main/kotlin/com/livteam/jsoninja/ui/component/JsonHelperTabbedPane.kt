package com.livteam.jsoninja.ui.component

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonHelperService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ChangeListener

/**
 * JSON Helper 플러그인의 탭 컴포넌트를 관리하는 클래스입니다.
 * 탭의 생성, 삭제, 이벤트 처리를 담당합니다.
 */
class JsonHelperTabbedPane(private val project: Project) : JBTabbedPane() {
    private var tabCounter = 1
    private var onTabSelectedListener: ((JsonEditor?) -> Unit)? = null
    private var onTabContentChangedListener: ((String) -> Unit)? = null
    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val jsonHelperService = project.getService(JsonHelperService::class.java)

    companion object {
        private const val ADD_NEW_TAB_COMPONENT_NAME = "addNewTab"
        private const val TAB_TITLE_PREFIX = "JSON " // 예시 상수
    }

    private val plusTabMouseAdapter = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
            val selectedComponent = this@JsonHelperTabbedPane.selectedComponent
            if (selectedComponent?.name == ADD_NEW_TAB_COMPONENT_NAME) {
                ApplicationManager.getApplication().invokeLater({
                    runWriteAction {
                        addNewTabFromPlusTab()
                    }
                }, ModalityState.defaultModalityState())
            }
        }
    }

    init {
        // 탭 변경 리스너 추가
        addChangeListener {
            onTabSelectedListener?.invoke(getCurrentEditor())
        }
        // TabbedPane에 "+" 탭 기능 마우스 리스너 추가
        addMouseListener(plusTabMouseAdapter)
    }
    
    /**
     * 초기 탭을 설정합니다.
     */
    fun setupInitialTabs() {
        ApplicationManager.getApplication().invokeLater({
            runWriteAction {
                addNewTabFromPlusTab()
                addPlusTab()
            }
        }, ModalityState.defaultModalityState())
    }
    
    /**
     * + 버튼 탭을 추가합니다.
     */
    private fun addPlusTab() {
        val plusPanel = JPanel().apply {
            name = ADD_NEW_TAB_COMPONENT_NAME // 상수 사용
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = LocalizationBundle.message("addTab")
        }
        addTab("", AllIcons.General.Add, plusPanel)

        val latestJsonEditor = components.findLast {
            if (it.name == null) return@findLast false
            if (it.name.isEmpty()) return@findLast false

            it.name.startsWith(TAB_TITLE_PREFIX)
        }

        val currentIndex =  if (latestJsonEditor != null) {
            indexOfComponent(latestJsonEditor)
        } else {
            // 새 탭을 찾지 못한 경우 json editor의 다음 탭이므로 1로 설정
            1
        }

        setToolTipTextAt(currentIndex, LocalizationBundle.message("addTab"))
    }
    
    /**
     * + 탭을 클릭했을 때 새 탭을 추가하는 로직
     */
    fun addNewTabFromPlusTab(content: String = "") {
        val plusTabIndex = indexOfComponent(components.find { it.name == ADD_NEW_TAB_COMPONENT_NAME })

        if (plusTabIndex != -1) {
            // 새 탭을 "+" 탭 바로 앞에 추가하고 선택
            addNewTabInternal(plusTabIndex, content)
            selectedIndex = indexOfComponent(components.findLast { it.name.startsWith(TAB_TITLE_PREFIX) } ?: return)
        } else {
            // "+" 탭을 찾지 못한 경우 (예외적 상황), 그냥 새 탭을 마지막에 추가
            addNewTabInternal(tabCount, content)

            val latestJsonEditor = components.findLast {
                if (it.name == null) return@findLast false
                if (it.name.isEmpty()) return@findLast false

                it.name.startsWith(TAB_TITLE_PREFIX)
            }
            if (latestJsonEditor != null) {
                selectedIndex = indexOfComponent(latestJsonEditor)
            } else {
                selectedIndex = 0
            }
        }
    }

    // 탭 닫기 버튼의 이벤트를 처리하는 내부 클래스
    private inner class TabCloseButtonListener(
        private val tabContentComponent: JComponent, // 닫을 탭의 메인 컨텐츠 (예: JBScrollPane)
        private val closeButtonLabel: JLabel
    ) : MouseAdapter() {
        private val originalIcon = closeButtonLabel.icon
        private val hoverIcon = AllIcons.Actions.CloseHovered

        override fun mouseEntered(e: MouseEvent) {
            closeButtonLabel.icon = hoverIcon
        }

        override fun mouseExited(e: MouseEvent) {
            closeButtonLabel.icon = originalIcon
        }

        override fun mouseClicked(e: MouseEvent) {
            ApplicationManager.getApplication().invokeLater({
                runWriteAction {
                    // "+" 탭을 제외하고 최소 1개의 JSON 탭은 유지
                    val closableTabIndex = indexOfComponent(tabContentComponent)
                    if (closableTabIndex == -1) return@runWriteAction

                    // "+" 탭은 닫을 수 없도록 하고, 실제 JSON 탭이 1개만 남는 경우에도 닫지 않음
                    val jsonTabCount = components.count { it.name != ADD_NEW_TAB_COMPONENT_NAME && it is JBScrollPane }

                    if (jsonTabCount <= 1 && getComponentAt(closableTabIndex)?.name != ADD_NEW_TAB_COMPONENT_NAME) {
                        // 마지막 남은 실제 탭이거나, 실수로 "+"탭을 닫으려는 경우 방지
                        if (getComponentAt(closableTabIndex)?.name == ADD_NEW_TAB_COMPONENT_NAME) return@runWriteAction // "+" 탭은 닫지 않음
                        return@runWriteAction // 마지막 JSON 탭은 닫지 않음
                    }

                    removeTabAt(closableTabIndex)

                    // 탭 제거 후 선택 로직:
                    // 만약 제거된 탭의 인덱스에 "+" 탭이 위치하게 되면 (즉, "+" 탭 바로 앞 탭이 닫힌 경우)
                    // 그리고 그 이전 탭이 존재하면 그 이전 탭을 선택한다.
                    // 그렇지 않으면 JBTabbedPane의 기본 동작 또는 ChangeListener가 선택을 처리.
                    if (closableTabIndex < tabCount && getComponentAt(closableTabIndex)?.name == ADD_NEW_TAB_COMPONENT_NAME) {
                        if (closableTabIndex > 0) {
                            selectedIndex = closableTabIndex - 1
                        }
                        // closableTabIndex가 0이고, 해당 위치에 ADD_NEW_TAB_COMPONENT_NAME이 있다면,
                        // 이는 모든 실제 탭이 닫혔고 "+" 탭만 남았음을 의미. 이 경우 "+" 탭이 선택된 상태로 둔다.
                    }
                    // 다른 경우에는 selectedIndex가 자동으로 조정되거나 changeListener에 의해 처리됨.
                }
            }, ModalityState.defaultModalityState())
        }
    }

    /**
     * 탭 제목과 닫기 버튼을 포함하는 커스텀 탭 컴포넌트를 생성합니다.
     * @param title 탭에 표시될 제목
     * @param contentComponent 이 탭 컴포넌트와 연결된 메인 컨텐츠 (보통 JBScrollPane)
     * @return 생성된 JPanel 형태의 탭 컴포넌트
     */
    private fun createTabComponent(title: String, contentComponent: JComponent): JPanel {
        val panel = JPanel(BorderLayout(5, 0)).apply {
            isOpaque = false // 배경 투명 처리로 TabbedPane의 테마와 어울리도록 함
        }

        val titleLabel = JLabel(title)
        panel.add(titleLabel, BorderLayout.CENTER) // 제목을 중앙에 배치하여 공간 활용

        val closeLabel = JLabel(AllIcons.Actions.Close).apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(TabCloseButtonListener(contentComponent, this))
        }
        panel.add(closeLabel, BorderLayout.EAST)

        return panel
    }

    /**
     * 새로운 JsonEditor 인스턴스를 생성하고 초기화합니다.
     * 내용 변경 시 onTabContentChangedListener를 호출하도록 콜백을 설정합니다.
     */
    private fun createEditor(): JsonEditor {
        return JsonEditor(project).apply {
            // JsonEditor 내부에서 내용 변경 시 onTabContentChangedListener가 호출되도록 설정
            setOnContentChangeCallback { newContent ->
                onTabContentChangedListener?.invoke(newContent)
                // JsonEditor 자체의 원본 JSON도 업데이트 (필요하다면)
                // setOriginalJson(newContent) // 이 부분은 JmesPath 사용 시 originalJson과 혼동될 수 있으므로,
                // JmesPath 로직에서 명확히 관리하는 것이 좋음.
                // 여기서는 단순히 content change 이벤트만 전달.
            }
        }
    }

    /**
     * 지정된 인덱스에 새 JSON 에디터 탭을 내부적으로 추가합니다.
     * @param index 탭이 추가될 위치
     * @param content 탭에 초기에 표시될 JSON 문자열 (기본값: 빈 문자열)
     * @return 생성된 JsonEditor 인스턴스
     */
    private fun addNewTabInternal(index: Int, content: String = ""): JsonEditor {
        val editor = createEditor()
        // setText는 editor의 onContentChangeCallback을 트리거할 수 있으므로,
        // 여기서 onTabContentChangedListener를 직접 호출하지 않도록 주의합니다.
        // createEditor에서 설정된 콜백이 이를 처리해야 합니다.
        if (content.isNotEmpty()) {
            editor.setText(content) // 이 호출이 JsonEditor의 onContentChangeCallback을 발동시키는지 확인 필요
            // 만약 발동시키지 않는다면, 아래 주석 해제 또는 createEditor의 콜백 호출 방식 변경
            // onTabContentChangedListener?.invoke(content)
        }

        val title = "$TAB_TITLE_PREFIX${tabCounter++}"

        val editorPanel = JPanel(BorderLayout())
        val jmesPathComponent = JmesPathComponent(project)
        editorPanel.add(jmesPathComponent.getComponent(), BorderLayout.NORTH)
        editorPanel.add(editor, BorderLayout.CENTER)

        setupJmesPathComponent(jmesPathComponent, editor, initialJson = content)

        val scrollPane = JBScrollPane(editorPanel).apply {
            // 스크롤 패널에 이름을 부여하여 탭 컴포넌트와 컨텐츠를 식별할 수 있게 함 (선택 사항)
            name = title // 또는 고유 ID
        }

        insertTab(title, null, scrollPane, null, index)
        setTabComponentAt(index, createTabComponent(title, scrollPane))

        return editor
    }

    /**
     * 현재 선택된 탭의 JsonEditor를 반환합니다.
     * 선택된 탭이 없거나 JsonEditor를 포함하지 않으면 null을 반환합니다.
     */
    fun getCurrentEditor(): JsonEditor? {
        val currentSelectedComponent = selectedComponent
        // "+" 탭이거나, 유효한 JBScrollPane이 아닌 경우 null 반환
        if (currentSelectedComponent == null || currentSelectedComponent.name == ADD_NEW_TAB_COMPONENT_NAME || currentSelectedComponent !is JBScrollPane) {
            return null
        }
        // JBScrollPane -> JPanel (editorPanel) -> JsonEditor 순으로 탐색
        val editorPanel = currentSelectedComponent.viewport?.view as? JPanel
        return editorPanel?.components?.find { it is JsonEditor } as? JsonEditor
    }
    
    /**
     * 탭 선택 리스너 설정
     * @param listener 선택된 탭의 에디터를 매개변수로 받는 함수
     */
    fun setOnTabSelectedListener(listener: (JsonEditor?) -> Unit) {
        this.onTabSelectedListener = listener
    }
    
    /**
     * 탭 내용 변경 리스너 설정
     * @param listener 변경된 내용을 매개변수로 받는 함수
     */
    fun setOnTabContentChangedListener(listener: (String) -> Unit) {
        this.onTabContentChangedListener = listener
    }

    /**
     * JMESPath 컴포넌트의 콜백을 설정하고 초기 JSON을 제공합니다.
     * @param jmesPathComponent 설정할 JmesPathComponent 인스턴스
     * @param editor 연결된 JsonEditor 인스턴스
     * @param initialJson JMESPath 컴포넌트의 초기 원본 JSON (선택 사항)
     */
    private fun setupJmesPathComponent(jmesPathComponent: JmesPathComponent, editor: JsonEditor, initialJson: String? = null) {
        // 초기 원본 JSON 설정 (탭 생성 시 내용이 있다면)
        initialJson?.takeIf { it.isNotBlank() }?.let {
            jmesPathComponent.setOriginalJson(it)
            editor.setOriginalJson(it) // 에디터의 원본 JSON도 동기화
        }

        jmesPathComponent.setOnBeforeSearchCallback {
            // JMESPath 검색 전, 원본 JSON이 없으면 현재 에디터에서 가져와 설정
            if (!jmesPathComponent.hasOriginalJson()) {
                val editorText = editor.getText()
                if (editorText.isNotBlank()) { // isBlank()는 isEmpty()와 공백 문자열 모두 처리
                    jmesPathComponent.setOriginalJson(editorText)
                    editor.setOriginalJson(editorText) // 에디터의 원본 JSON도 업데이트
                } else {
                    // 에디터 내용이 비어있으면 검색하지 않도록 하거나, 사용자에게 알림 (선택)
                    // 여기서는 그냥 return하여 검색 중단
                    return@setOnBeforeSearchCallback
                }
            }
        }

        jmesPathComponent.setOnSearchCallback { originalJson, resultJson ->
            // JMESPath 검색 결과 처리
            val jsonFormatState = jsonHelperService.getJsonFormatState()
            val formattedJson = formatterService.formatJson(resultJson, jsonFormatState)

            // 결과를 에디터에 표시 (이때 에디터의 onContentChangeCallback이 호출될 수 있음)
            editor.setText(formattedJson)
            // 검색 시 사용된 원본 JSON을 에디터에 다시 한 번 명시적으로 설정 (필요 시)
            // editor.setOriginalJson(originalJson) // setText 후 originalJson이 유지되는지 확인 필요
            // JmesPath 결과 표시는 원본을 바꾸는 것이 아닐 수 있음.
            // 만약 formattedJson이 원본을 대체하는 것이라면, setOriginalJson도 formattedJson으로 해야 할 수 있음.
            // 여기서는 JmesPath 결과를 보여주는 것이므로 originalJson은 그대로 두는 것이 맞을 수 있습니다.
            // JsonEditor의 setOriginalJson의 역할과 시점에 따라 조정이 필요합니다.
            // 현재는 검색 결과 표시 후, 해당 결과에 대한 원본(originalJson 파라미터)을 에디터에 저장합니다.
            editor.setOriginalJson(originalJson)

        }
    }
}
