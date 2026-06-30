package com.livteam.jsoninja.ui.component.tab

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonHelperService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.ui.component.jsonQuery.JsonQueryPresenter
import com.livteam.jsoninja.ui.component.model.JsonQueryUiState
import com.livteam.jsoninja.ui.component.model.TabUiState
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JsonTabContextFactory(
    private val project: Project,
    private val parentDisposable: Disposable,
    private val formatterService: JsonFormatterService,
    private val helperService: JsonHelperService
) {
    fun create(
        title: String,
        content: String,
        fileExtension: String?,
        onTabContentChangedListener: ((String) -> Unit)?
    ): TabUiState {
        val model = JsonQueryUiState()
        val editor = createEditor(fileExtension, onTabContentChangedListener)

        if (content.isNotEmpty()) {
            editor.setText(content)
        }

        val tabContentPanel = JPanel(BorderLayout(0, 0)).apply {
            name = title
        }

        val tabDisposable = Disposer.newDisposable("JsonHelperTab-$title")
        Disposer.register(parentDisposable, tabDisposable)
        Disposer.register(tabDisposable, editor)

        val jsonQueryPresenter = JsonQueryPresenter(project, model)
        Disposer.register(tabDisposable, jsonQueryPresenter)
        val jmesComponent = jsonQueryPresenter.getComponent().apply {
            border = JBUI.Borders.emptyTop(3)
        }

        tabContentPanel.add(jmesComponent, BorderLayout.NORTH)
        tabContentPanel.add(editor, BorderLayout.CENTER)

        setupJmesPathPresenter(jsonQueryPresenter, editor, tabDisposable, initialJson = content)

        return TabUiState(
            panel = tabContentPanel,
            editor = editor,
            queryPresenter = jsonQueryPresenter,
            disposable = tabDisposable
        )
    }

    private fun createEditor(
        fileExtension: String? = null,
        onTabContentChangedListener: ((String) -> Unit)?
    ): JsonEditorView {
        return JsonEditorView(project, fileExtension).apply {
            setOnContentChangeCallback { newContent ->
                onTabContentChangedListener?.invoke(newContent)
            }
        }
    }

    private fun setupJmesPathPresenter(
        jsonQueryPresenter: JsonQueryPresenter,
        editor: JsonEditorView,
        tabDisposable: Disposable,
        initialJson: String? = null
    ) {
        val queryResultFormatScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
        var queryResultFormatJob: Job? = null
        Disposer.register(tabDisposable) {
            queryResultFormatJob?.cancel()
            queryResultFormatScope.cancel()
        }

        initialJson?.takeIf { it.isNotBlank() }?.let {
            jsonQueryPresenter.setOriginalJson(it)
        }

        jsonQueryPresenter.setOnBeforeSearchCallback {
            if (!jsonQueryPresenter.hasOriginalJson()) {
                val editorText = editor.getText()
                if (editorText.isNotBlank()) {
                    jsonQueryPresenter.setOriginalJson(editorText)
                } else {
                    return@setOnBeforeSearchCallback
                }
            }
        }

        jsonQueryPresenter.setOnSearchCallback { _, resultJson ->
            queryResultFormatJob?.cancel()
            val jsonFormatState = helperService.getJsonFormatState()
            queryResultFormatJob = queryResultFormatScope.launch {
                try {
                    val formattedJson = formatterService.formatJsonOnDefault(resultJson, jsonFormatState)
                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed) return@withContext
                        editor.setText(formattedJson)
                    }
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                }
            }
        }
    }
}
