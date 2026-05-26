package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.TemplatePlaceholderSupport
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JsonEditorTextPresenter(
    private val project: Project,
    private val view: JsonEditorTextView
) {
    private var onContentChangeCallback: ((String) -> Unit)? = null
    private var isSettingText = false
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    private var placeholderNormalizationJob: Job? = null

    fun setupContentChangeListener() {
        val contentChangeListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (isSettingText) {
                    return
                }

                placeholderNormalizationJob?.cancel()
                placeholderNormalizationJob = null

                val content = view.getText()
                val normalizedContent = TemplatePlaceholderSupport.normalizePlaceholderLayout(content)
                if (normalizedContent != content) {
                    schedulePlaceholderNormalization(
                        normalizedContent = normalizedContent,
                        expectedModificationStamp = event.document.modificationStamp
                    )
                    return
                }

                onContentChangeCallback?.invoke(content)
            }
        }

        view.editor.addDocumentListener(contentChangeListener)

        Disposer.register(view) {
            placeholderNormalizationJob?.cancel()
            coroutineScope.cancel()
            view.editor.removeDocumentListener(contentChangeListener)
        }
    }

    fun setText(text: String) {
        placeholderNormalizationJob?.cancel()
        placeholderNormalizationJob = null

        runWhileSettingText {
            WriteCommandAction.runWriteCommandAction(project) {
                view.setText(text)
            }
        }
    }

    fun getText(): String {
        return view.getText()
    }

    fun setOnContentChangeCallback(callback: (String) -> Unit) {
        onContentChangeCallback = callback
    }

    private fun schedulePlaceholderNormalization(
        normalizedContent: String,
        expectedModificationStamp: Long
    ) {
        placeholderNormalizationJob = coroutineScope.launch {
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) return@withContext

                val document = view.editor.document
                if (document.modificationStamp != expectedModificationStamp) return@withContext

                applyNormalizedContent(document, normalizedContent)
                onContentChangeCallback?.invoke(normalizedContent)
            }
        }
    }

    private fun applyNormalizedContent(
        document: Document,
        normalizedContent: String,
    ) {
        if (document.text == normalizedContent) {
            return
        }

        runWhileSettingText {
            CommandProcessor.getInstance().runUndoTransparentAction {
                ApplicationManager.getApplication().runWriteAction {
                    document.replaceString(0, document.textLength, normalizedContent)
                }
            }
        }
    }

    private inline fun runWhileSettingText(action: () -> Unit) {
        isSettingText = true
        try {
            action()
        } finally {
            isSettingText = false
        }
    }
}
