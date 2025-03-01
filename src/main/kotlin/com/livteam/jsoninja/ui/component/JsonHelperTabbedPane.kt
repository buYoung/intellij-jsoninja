package com.livteam.jsoninja.ui.component

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
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
    
    init {
        // 탭 변경 리스너 추가
        addChangeListener { _ ->
            val editor = getCurrentEditor()
            onTabSelectedListener?.invoke(editor)
        }
    }
    
    /**
     * 초기 탭을 설정합니다.
     */
    fun setupInitialTabs() {
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().invokeLater({
                runWriteAction {
                    addNewTab()
                    addPlusTab()
                }
            }, ModalityState.any())
        }
    }
    
    /**
     * + 버튼 탭을 추가합니다.
     */
    private fun addPlusTab() {
        val emptyPanel = JPanel().apply {
            name = "addNewTab"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        addTab("", AllIcons.General.Add, emptyPanel)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val clickedTab = e.component as? JBTabbedPane ?: return
                val clickedTabName = clickedTab.selectedComponent.name

                if (clickedTabName != "addNewTab") return

                ApplicationManager.getApplication().executeOnPooledThread {
                    ApplicationManager.getApplication().invokeLater({
                        runWriteAction {
                            addNewTabFromPlusTab()
                        }
                    }, ModalityState.any())
                }
            }
        })
    }
    
    /**
     * + 탭을 클릭했을 때 새 탭을 추가하는 로직
     */
    private fun addNewTabFromPlusTab() {
        val newTabIndex = tabCount - 1
        addNewTab()
        selectedIndex = newTabIndex
    }
    
    /**
     * 닫을 수 있는 탭 컴포넌트를 생성합니다.
     * @param title 탭 제목
     * @param component 탭 컴포넌트 (JScrollPane)
     * @return 탭 컴포넌트
     */
    private fun createTabComponent(title: String, component: JComponent): JPanel {
        val panel = JPanel(BorderLayout(5, 0))
        panel.isOpaque = false
        
        // 탭 제목 라벨
        val titleLabel = JLabel(title)
        panel.add(titleLabel, BorderLayout.WEST)
        
        // X 아이콘 라벨
        val closeLabel = JLabel(AllIcons.Actions.Close)
        closeLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        
        // 호버 효과를 위한 변수
        val originalIcon = closeLabel.icon
        val hoverIcon = AllIcons.Actions.CloseHovered
        
        // 마우스 이벤트 처리
        closeLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                closeLabel.icon = hoverIcon  // 호버 효과
            }
            
            override fun mouseExited(e: MouseEvent) {
                closeLabel.icon = originalIcon  // 원래 아이콘으로 복원
            }
            
            override fun mouseClicked(e: MouseEvent) {
                // 탭 닫기 기능
                ApplicationManager.getApplication().executeOnPooledThread {
                    ApplicationManager.getApplication().invokeLater({
                        runWriteAction {
                            // 컴포넌트로 탭 인덱스 찾기
                            for (i in 0 until tabCount) {
                                if (getComponentAt(i) == component) {
                                    removeTabAt(i)

                                    val currentTabName = if (i < tabCount) getComponentAt(i).name else null
                                    if (currentTabName == "addNewTab") {
                                        selectedIndex = i - 1
                                    }
                                    break
                                }
                            }
                        }
                    }, ModalityState.any())
                }
            }
        })
        
        panel.add(closeLabel, BorderLayout.EAST)
        return panel
    }
    
    /**
     * 에디터를 생성합니다.
     */
    private fun createEditor(): JsonEditor {
        return JsonEditor(project).apply {
            setText("")
            setOnContentChangeCallback { content ->
                onTabContentChangedListener?.invoke(content)
            }
        }
    }
    
    /**
     * 새 탭을 추가합니다.
     * @param content 초기 내용
     * @return 추가된 탭의 에디터
     */
    fun addNewTab(content: String = ""): JsonEditor {
        val editor = createEditor()
        if (content.isNotEmpty()) {
            editor.setText(content)
            onTabContentChangedListener?.invoke(content)
        }
        
        val title = "JSON ${tabCounter++}"
        
        // 에디터와 JMESPath 컴포넌트를 포함할 패널 생성
        val editorPanel = JPanel(BorderLayout())
        val jmesPathComponent = JmesPathComponent(project)
        editorPanel.add(jmesPathComponent.getComponent(), BorderLayout.NORTH)
        editorPanel.add(editor, BorderLayout.CENTER)
        
        // JMES 컴포넌트 설정
        setupJmesPathComponent(jmesPathComponent, editor)
        
        val scrollPane = JBScrollPane(editorPanel).apply {
            name = title
        }
        
        // + 버튼 탭이 있는 경우, 그 앞에 새 탭 추가
        val plusTabIndex = tabCount - 1
        if (plusTabIndex >= 0 && getComponentAt(plusTabIndex).name == "addNewTab") {
            insertTab(title, null, scrollPane, null, plusTabIndex)
            // 생성한 탭에 닫기 버튼 추가
            setTabComponentAt(plusTabIndex, createTabComponent(title, scrollPane))
        } else {
            // + 버튼 탭이 없는 경우 일반적으로 탭 추가
            addTab(title, scrollPane)
            // 생성한 탭에 닫기 버튼 추가
            setTabComponentAt(tabCount - 1, createTabComponent(title, scrollPane))
        }
        
        return editor
    }
    
    /**
     * 현재 선택된 탭의 에디터 반환
     * @return 현재 선택된 탭의 에디터
     */
    fun getCurrentEditor(): JsonEditor? {
        val currentIndex = selectedIndex
        // + 버튼 탭이 아닌 경우에만 에디터 반환
        if (currentIndex >= 0 && currentIndex < tabCount && getComponentAt(currentIndex).name != "addNewTab") {
            val scrollPane = getComponentAt(currentIndex) as? JBScrollPane
            val editorPanel = scrollPane?.viewport?.view as? JPanel
            return editorPanel?.getComponent(1) as? JsonEditor
        }
        return null
    }
    
    /**
     * 탭 선택 리스너 설정
     * @param listener 선택된 탭의 에디터를 매개변수로 받는 함수
     */
    fun setOnTabSelectedListener(listener: (JsonEditor?) -> Unit) {
        onTabSelectedListener = listener
    }
    
    /**
     * 탭 내용 변경 리스너 설정
     * @param listener 변경된 내용을 매개변수로 받는 함수
     */
    fun setOnTabContentChangedListener(listener: (String) -> Unit) {
        onTabContentChangedListener = listener
    }
    
    /**
     * JMESPath 컴포넌트 설정
     * @param jmesPathComponent JMESPath 컴포넌트
     * @param editor 연결할 JSON 에디터
     */
    private fun setupJmesPathComponent(jmesPathComponent: JmesPathComponent, editor: JsonEditor) {
        // 쿼리 수행 전 이벤트
        jmesPathComponent.setOnBeforeSearchCallback {
            // 원본 JSON이 없으면 현재 에디터에서 가져옴
            if (jmesPathComponent.hasOriginalJson()) return@setOnBeforeSearchCallback

            val editorText = editor.getText()
            if (editorText.isNotEmpty()) {
                jmesPathComponent.setOriginalJson(editorText)
                // 원본 JSON도 설정
                editor.setOriginalJson(editorText)
            }
        }

        // 쿼리 결과 처리 이벤트
        jmesPathComponent.setOnSearchCallback { originalJson, resultJson ->
            // JMES 쿼리 결과 처리
            val formatterService = project.getService(JsonFormatterService::class.java)
            val formattedJson = formatterService.formatJson(resultJson, project.getService(JsonHelperService::class.java).getJsonFormatState())
            // 포맷팅 없이 결과 그대로 표시
            editor.setText(formattedJson)
            // 원본 JSON 저장
            editor.setOriginalJson(originalJson)
        }
    }
}
