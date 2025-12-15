package com.livteam.jsoninja.ui.component.main

import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonHelperService
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.ui.component.tab.JsonTabsPresenter

class JsoninjaPanelPresenter(
    private val project: Project,
    private val view: JsoninjaPanelView,
    private val tabsPresenter: JsonTabsPresenter,
) {
    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val helperService = project.getService(JsonHelperService::class.java)

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
        view.processEditorText { jsonText ->
            val textToFormat = if (formatterService.containsEscapeCharacters(jsonText)) {
                formatterService.fullyUnescapeJson(jsonText)
            } else {
                jsonText
            }
            formatterService.formatJson(textToFormat, state)
        }
    }

    fun escapeJson() {
        view.processEditorText { jsonText -> formatterService.escapeJson(jsonText) }
    }

    fun unescapeJson() {
        view.processEditorText { jsonText -> formatterService.unescapeJson(jsonText) }
    }

    fun setRandomJsonData(data: String, skipFormatting: Boolean = false) {
        val currentEditor = getCurrentEditor() ?: return

        val processedJson = if (skipFormatting) {
            data
        } else {
            formatterService.formatJson(data, getJsonFormatState())
        }

        view.updateEditorText(currentEditor, processedJson)
    }
}
