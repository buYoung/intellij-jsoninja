package com.livteam.jsoninja.ui.component.main

import com.intellij.openapi.project.Project
import com.intellij.openapi.Disposable
import com.livteam.jsoninja.ui.component.tab.JsonTabsPresenter
import com.livteam.jsoninja.ui.component.tab.JsonTabsView
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonHelperService
import com.livteam.jsoninja.ui.component.editor.JsonEditorView

class JsoninjaPanelPresenter(
    private val project: Project,
    parentDisposable: Disposable,
    tabsView: JsonTabsView
) {
    private val tabsPresenter = JsonTabsPresenter(project, parentDisposable, tabsView)
    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val helperService = project.getService(JsonHelperService::class.java)

    init {
        tabsPresenter.setOnLastJsonTabClosedListener {
            ToolWindowManager.getInstance(project).getToolWindow("JSONinja")?.hide()
        }
    }

    fun initialize() {
        tabsPresenter.setupInitialTabs()
    }

    fun getCurrentEditor(): JsonEditorView? = tabsPresenter.getCurrentEditor()

    fun getTabsPresenter(): JsonTabsPresenter = tabsPresenter

    fun addNewTab(content: String = "", fileExtension: String? = null) {
        tabsPresenter.addNewTabFromPlusTab(content, fileExtension)
    }

    fun setJsonFormatState(state: JsonFormatState) {
        helperService.setJsonFormatState(state)
    }

    fun getJsonFormatState(): JsonFormatState = helperService.getJsonFormatState()

    fun formatJson(formatState: JsonFormatState? = null) {
        val state = formatState ?: getJsonFormatState()
        processCurrentEditorText { jsonText ->
            val textToFormat = if (formatterService.containsEscapeCharacters(jsonText)) {
                formatterService.fullyUnescapeJson(jsonText)
            } else {
                jsonText
            }
            formatterService.formatJson(textToFormat, state)
        }
    }

    fun escapeJson() {
        processCurrentEditorText { jsonText -> formatterService.escapeJson(jsonText) }
    }

    fun unescapeJson() {
        processCurrentEditorText { jsonText -> formatterService.unescapeJson(jsonText) }
    }

    fun setRandomJsonData(data: String, skipFormatting: Boolean = false) {
        val currentEditor = getCurrentEditor() ?: return

        val processedJson = if (skipFormatting) {
            data
        } else {
            formatterService.formatJson(data, getJsonFormatState())
        }

        currentEditor.setText(processedJson)
    }

    private fun processCurrentEditorText(processor: (String) -> String) {
        val currentEditor = getCurrentEditor() ?: return
        val jsonText = currentEditor.getText()
        val trimmedJsonText = jsonText.trim()
        val isJsonTextEmpty = trimmedJsonText.isBlank() || trimmedJsonText.isEmpty()

        if (isJsonTextEmpty) return

        val processedJson = processor(jsonText)

        currentEditor.setText(processedJson)
    }
}
