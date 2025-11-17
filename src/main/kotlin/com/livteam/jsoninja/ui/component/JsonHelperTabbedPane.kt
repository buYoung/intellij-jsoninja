package com.livteam.jsoninja.ui.component

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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
class JsonHelperTabbedPane(
    private val project: Project,
    private val parentDisposable: Disposable
) : JBTabbedPane() {
    private var tabCounter = 1
    private var onTabSelectedListener: ((JsonEditor?) -> Unit)? = null
    private var onTabContentChangedListener: ((String) -> Unit)? = null
    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val jsonHelperService = project.getService(JsonHelperService::class.java)
    private val tabDisposables = mutableMapOf<Component, Disposable>()

    companion object {
        private const val ADD_NEW_TAB_COMPONENT_NAME = "addNewTab"
        private const val TAB_TITLE_PREFIX = "JSON " // 예시 상수
    }

    private val plusTabMouseAdapter = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
            val selectedComponent = this@JsonHelperTabbedPane.selectedComponent
            if (selectedComponent?.name != ADD_NEW_TAB_COMPONENT_NAME) {
                return;
            }

            ApplicationManager.getApplication().invokeLater({
                runWriteAction {
                    addNewTabFromPlusTab()
                }
            }, ModalityState.defaultModalityState())
        }
    }

    init {
        // 탭 변경 리스너 추가
        addChangeListener {
            onTabSelectedListener?.invoke(getCurrentEditor())
        }
        // TabbedPane에 "+" 탭 기능 마우스 리스너 추가
        addMouseListener(plusTabMouseAdapter)

        Disposer.register(parentDisposable) {
            tabDisposables.clear()
        }
    }

    /**
     * 초기 탭을 설정합니다.
     */
    fun setupInitialTabs() {
        ApplicationManager.getApplication().invokeLater({
            runWriteAction {
                addNewTabInternal(0)
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

        val currentIndex = if (latestJsonEditor != null) {
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
            // 새로 추가된 탭을 선택하는 것은 addNewTabInternal에서 처리
        } else {
            // "+" 탭을 찾지 못한 경우 (예외적 상황), 그냥 새 탭을 마지막에 추가
            addNewTabInternal(tabCount, content)
            // 새로 추가된 탭을 선택하는 것은 addNewTabInternal에서 처리
        }
    }

    // 탭 닫기 버튼의 이벤트를 처리하는 내부 클래스
    private inner class TabCloseButtonListener(
        private val tabContentComponent: JComponent, // 닫을 탭의 메인 컨텐츠 (tabContentPanel)
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
                    val closableTabIndex = indexOfComponent(tabContentComponent)
                    if (closableTabIndex == -1) return@runWriteAction
                    closeTabAt(closableTabIndex)
                }
            }, ModalityState.defaultModalityState())
        }
    }

    /**
     * 탭 제목과 닫기 버튼을 포함하는 커스텀 탭 컴포넌트를 생성합니다.
     * @param title 탭에 표시될 제목
     * @param contentComponent 이 탭 컴포넌트와 연결된 메인 컨텐츠 (보통 JPanel)
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

        // 1. JmesPathComponent와 Editor를 담을 새로운 패널 생성
        val tabContentPanel = JPanel(BorderLayout()).apply {
            // 중요: 탭 컨텐츠 패널에 고유한 이름 부여
            name = title // 탭 제목을 패널 이름으로 사용하여 식별 가능하게 함
        }

        val tabDisposable = Disposer.newDisposable("JsonHelperTab-$title")
        Disposer.register(parentDisposable, tabDisposable)
        Disposer.register(tabDisposable, editor)

        // 2. JmesPathComponent 생성 및 상단에 추가
        val jmesPathComponent = JmesPathComponent(project)
        tabContentPanel.add(jmesPathComponent.getComponent(), BorderLayout.NORTH)

        // 3. JsonEditor를 중앙에 직접 추가 (JBScrollPane 제거)
        tabContentPanel.add(editor, BorderLayout.CENTER)

        setupJmesPathComponent(jmesPathComponent, editor, initialJson = content)

        tabDisposables[tabContentPanel] = tabDisposable

        // 4. 수정된 tabContentPanel을 탭에 추가
        insertTab(title, null, tabContentPanel, null, index)
        setTabComponentAt(index, createTabComponent(title, tabContentPanel))

        // 5. 새로 추가된 탭을 선택
        selectedIndex = index

        return editor
    }

    /**
     * 현재 선택된 탭의 JsonEditor를 반환합니다.
     * 선택된 탭이 없거나 JsonEditor를 포함하지 않으면 null을 반환합니다.
     */
    fun getCurrentEditor(): JsonEditor? {
        val currentSelectedComponent = selectedComponent
        // "+" 탭이거나, null인 경우
        if (currentSelectedComponent == null || currentSelectedComponent.name == ADD_NEW_TAB_COMPONENT_NAME) {
            return null
        }

        // currentSelectedComponent는 이제 tabContentPanel (JPanel)임
        if (currentSelectedComponent is JPanel) {
            // tabContentPanel 내에서 JsonEditor를 찾음
            val editor = currentSelectedComponent.components.find { it is JsonEditor } as? JsonEditor
            // JsonEditor를 반환
            return editor
        }
        return null // JPanel이 아닌 경우 (예상치 못한 상황)
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
    private fun setupJmesPathComponent(
        jmesPathComponent: JmesPathComponent,
        editor: JsonEditor,
        initialJson: String? = null
    ) {
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

    /**
     * 현재 선택된 탭을 닫습니다.
     * Action 시스템에서 호출하기 위한 공개 메서드입니다.
     * @return 탭이 성공적으로 닫혔으면 true, 닫을 수 없으면 false
     */
    fun closeCurrentTab(): Boolean {
        return closeTabAt(selectedIndex)
    }

    private fun closeTabAt(index: Int, enforceMinimumJsonTab: Boolean = true): Boolean {
        if (index < 0 || index >= tabCount) {
            return false
        }

        val component = getComponentAt(index)
        if (component == null || component.name == ADD_NEW_TAB_COMPONENT_NAME) {
            return false
        }

        if (enforceMinimumJsonTab && getJsonTabCount() <= 1) {
            return false
        }

        val nextSelectedIndex = if (index > 0) index - 1 else 0

        disposeTabComponent(component)
        removeTabAt(index)

        if (tabCount > 0) {
            selectedIndex = if (nextSelectedIndex < tabCount) nextSelectedIndex else tabCount - 1
        }

        return true
    }

    private fun disposeTabComponent(component: Component?) {
        if (component == null) return
        tabDisposables.remove(component)?.let { Disposer.dispose(it) }
    }

    /**
     * 탭을 닫을 수 있는지 확인합니다.
     * @return 현재 탭을 닫을 수 있으면 true, 닫을 수 없으면 false
     */
    fun canCloseCurrentTab(): Boolean {
        val selectedComponent = selectedComponent
        
        // "+" 탭이거나 null인 경우
        if (selectedComponent == null || selectedComponent.name == ADD_NEW_TAB_COMPONENT_NAME) {
            return false
        }
        
        // 실제 JSON 탭 개수 계산 (tabCount에서 + 탭 제외)
        var jsonTabCount = 0
        for (i in 0 until tabCount) {
            val component = getComponentAt(i)
            if (component != null && component.name != ADD_NEW_TAB_COMPONENT_NAME) {
                jsonTabCount++
            }
        }
        
        return jsonTabCount > 1
    }
    
    /**
     * 현재 JSON 탭의 개수를 반환합니다.
     * @return JSON 탭 개수 ("+" 탭 제외)
     */
    fun getJsonTabCount(): Int {
        var count = 0
        for (i in 0 until tabCount) {
            val component = getComponentAt(i)
            if (component != null && component.name != ADD_NEW_TAB_COMPONENT_NAME) {
                count++
            }
        }
        return count
    }
}
