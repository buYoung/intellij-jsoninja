package com.livteam.jsonhelper2.toolWindow.component

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.livteam.jsonhelper2.toolWindow.actions.JsonHelperActionGroup
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class JsonHelperPanel(private val project: Project) : SimpleToolWindowPanel(false, true), DataProvider {
    private val tabbedPane = JBTabbedPane()
    private val jmesPathField = SearchTextField()
    private var tabCounter = 1

    init {
        jmesPathField.textEditor.emptyText.text = "Enter JMES Path..."
        setupUI()
    }

    private fun setupUI() {
        // 초기 탭 추가
        addNewTab()
        
        // Add content
        val contentPanel = JPanel(BorderLayout()).apply {
            add(jmesPathField, BorderLayout.NORTH)
            add(tabbedPane, BorderLayout.CENTER)
        }
        
        // Setup toolbar and content
        toolbar = createToolbar()
        setContent(contentPanel)
    }

    private fun createEditor(): EditorEx {
        return (EditorFactory.getInstance().createEditor(
            EditorFactory.getInstance().createDocument(""),
            project,
            JSON_FILE_TYPE,
            false
        ) as EditorEx).apply {
            setPlaceholder("JSON 문자열을 입력하세요")
            settings.apply {
                isLineMarkerAreaShown = false
                isIndentGuidesShown = true
                isLineNumbersShown = true
                isWhitespacesShown = false
                isFoldingOutlineShown = true
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

    fun addNewTab() {
        val editor = createEditor()
        val title = "JSON ${tabCounter++}"
        
        tabbedPane.addTab(title, JBScrollPane(editor.component))
        tabbedPane.selectedIndex = tabbedPane.tabCount - 1
    }

    override fun getData(dataId: String): Any? {
        return when {
            DATA_KEY.`is`(dataId) -> this
            else -> null
        }
    }

    companion object {
        private val JSON_FILE_TYPE = com.intellij.json.JsonFileType.INSTANCE
        val DATA_KEY = DataKey.create<JsonHelperPanel>("JsonHelperPanel")
    }
}
