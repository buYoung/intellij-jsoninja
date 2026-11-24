package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
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
    companion object {
        private const val PLACEHOLDER_OPEN = "{{"
        private const val PLACEHOLDER_CLOSE = "}}"
    }

    private var onContentChangeCallback: ((String) -> Unit)? = null
    private var isSettingText = false
    private var mayContainPlaceholders = false
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    private var placeholderNormalizationJob: Job? = null

    fun setupContentChangeListener() {
        mayContainPlaceholders = view.editor.document.immutableCharSequence.indexOf(PLACEHOLDER_OPEN) >= 0

        val contentChangeListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (isSettingText) {
                    return
                }

                placeholderNormalizationJob?.cancel()
                placeholderNormalizationJob = null

                val textSequence = event.document.immutableCharSequence
                if (mayContainPlaceholders || mightHaveIntroducedPlaceholderOpen(event, textSequence)) {
                    val openIndex = textSequence.indexOf(PLACEHOLDER_OPEN)
                    mayContainPlaceholders = openIndex >= 0
                    if (openIndex >= 0 &&
                        textSequence.indexOf(PLACEHOLDER_CLOSE, startIndex = openIndex + PLACEHOLDER_OPEN.length) >= 0
                    ) {
                        val content = textSequence.toString()
                        val normalizedContent = TemplatePlaceholderSupport.normalizePlaceholderLayout(content)
                        if (normalizedContent != content) {
                            schedulePlaceholderNormalization(
                                normalizedContent = normalizedContent,
                                expectedModificationStamp = event.document.modificationStamp
                            )
                            return
                        }
                        onContentChangeCallback?.invoke(content)
                        return
                    }
                }

                val callback = onContentChangeCallback ?: return
                callback.invoke(textSequence.toString())
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
        mayContainPlaceholders = text.contains(PLACEHOLDER_OPEN)

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

    private fun mightHaveIntroducedPlaceholderOpen(event: DocumentEvent, text: CharSequence): Boolean {
        val newFragment = event.newFragment
        for (index in 0 until newFragment.length) {
            if (newFragment[index] == '{') {
                return true
            }
        }
        if (event.oldLength > 0 && event.offset > 0) {
            val joinIndex = event.offset + event.newLength
            if (joinIndex < text.length && text[event.offset - 1] == '{' && text[joinIndex] == '{') {
                return true
            }
        }
        return false
    }

    private fun schedulePlaceholderNormalization(
        normalizedContent: String,
        expectedModificationStamp: Long
    ) {
        // Capture component modality (write-safe for runWriteAction, while still active inside modal dialogs).
        val modalityState = ModalityState.stateForComponent(view)
        placeholderNormalizationJob = coroutineScope.launch {
            withContext(Dispatchers.EDT + modalityState.asContextElement()) {
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
        if (StringUtil.equals(document.immutableCharSequence, normalizedContent)) {
            return
        }

        mayContainPlaceholders = normalizedContent.contains(PLACEHOLDER_OPEN)
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
