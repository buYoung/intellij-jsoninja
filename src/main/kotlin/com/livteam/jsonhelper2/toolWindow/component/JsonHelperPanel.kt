package com.livteam.jsonhelper2.toolWindow.component

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.livteam.jsonhelper2.toolWindow.actions.JsonHelperActionGroup
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.json.JsonFileType

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
       runWriteAction {
            addNewTab()
        }
        
        // Add content
        val contentPanel = JPanel(BorderLayout()).apply {
            add(jmesPathField, BorderLayout.NORTH)
            add(tabbedPane, BorderLayout.CENTER)
        }
        
        // Setup toolbar and content
        toolbar = createToolbar()
        setContent(contentPanel)
    }

    private fun createEditor(): JComponent {
        return JsonEditor(project).apply {
            setText("")
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
            (editor as JsonEditor).setText(content)
        }
        
        val title = "JSON ${tabCounter++}"
        val scrollPane = JBScrollPane(editor)
        tabbedPane.addTab(title, scrollPane)
        tabbedPane.selectedIndex = tabbedPane.tabCount - 1
    }

    override fun getData(dataId: String): Any? {
        return when {
            DATA_KEY.`is`(dataId) -> this
            else -> null
        }
    }

    companion object {
        private val JSON_FILE_TYPE = JsonFileType.INSTANCE
        val DATA_KEY = DataKey.create<JsonHelperPanel>("JsonHelperPanel")
    }
}
