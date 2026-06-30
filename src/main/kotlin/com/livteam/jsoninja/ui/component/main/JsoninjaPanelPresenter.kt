package com.livteam.jsoninja.ui.component.main

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.livteam.jsoninja.ui.component.tab.JsonTabsPresenter
import com.livteam.jsoninja.ui.component.tab.JsonTabsView
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonHelperService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JsoninjaPanelPresenter(
    private val project: Project,
    parentDisposable: Disposable,
    tabsView: JsonTabsView
) {
    private val tabsPresenter = JsonTabsPresenter(project, parentDisposable, tabsView)
    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val helperService = project.getService(JsonHelperService::class.java)
    private val coroutineScope: CoroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    private var textProcessingJob: Job? = null

    init {
        Disposer.register(parentDisposable) {
            textProcessingJob?.cancel()
            coroutineScope.cancel()
        }

        tabsPresenter.setOnLastJsonTabClosedListener {
            ToolWindowManager.getInstance(project).getToolWindow("JSONinja")?.hide()
        }
    }

    fun initialize() {
        tabsPresenter.setupInitialTabs()
    }

    fun getCurrentEditor(): JsonEditorView? = tabsPresenter.getCurrentEditor()

    fun getTabsPresenter(): JsonTabsPresenter = tabsPresenter

    fun getProject(): Project = project

    fun addNewTab(content: String = "", fileExtension: String? = null) {
        tabsPresenter.addNewTabFromPlusTab(content, fileExtension)
    }

    fun setJsonFormatState(state: JsonFormatState) {
        helperService.setJsonFormatState(state)
    }

    fun getJsonFormatState(): JsonFormatState = helperService.getJsonFormatState()

    fun formatJson(formatState: JsonFormatState? = null) {
        val state = formatState ?: getJsonFormatState()
        processCurrentEditorTextAsync { jsonText ->
            val textToFormat = if (formatterService.containsEscapeCharacters(jsonText)) {
                formatterService.fullyUnescapeJson(jsonText)
            } else {
                jsonText
            }
            formatterService.formatJson(textToFormat, state)
        }
    }

    fun escapeJson() {
        processCurrentEditorTextAsync { jsonText -> formatterService.escapeJson(jsonText) }
    }

    fun unescapeJson() {
        processCurrentEditorTextAsync { jsonText -> formatterService.unescapeJson(jsonText) }
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

    private fun processCurrentEditorTextAsync(processor: (String) -> String) {
        val currentEditor = getCurrentEditor() ?: return
        val jsonText = currentEditor.getText()
        val trimmedJsonText = jsonText.trim()
        val isJsonTextEmpty = trimmedJsonText.isBlank() || trimmedJsonText.isEmpty()

        if (isJsonTextEmpty) return

        textProcessingJob?.cancel()
        textProcessingJob = coroutineScope.launch {
            try {
                val processedJson = withContext(Dispatchers.Default) {
                    processor(jsonText)
                }

                withContext(Dispatchers.EDT) {
                    if (project.isDisposed) return@withContext
                    if (getCurrentEditor() !== currentEditor) return@withContext
                    if (currentEditor.getText() != jsonText) return@withContext
                    currentEditor.setText(processedJson)
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            }
        }
    }
}
