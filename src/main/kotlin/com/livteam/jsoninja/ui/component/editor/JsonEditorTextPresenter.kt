package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.livteam.jsoninja.services.TemplatePlaceholderSupport
import com.livteam.jsoninja.ui.component.model.JsonQueryUiState

class JsonEditorTextPresenter(
    private val project: Project,
    private val view: JsonEditorTextView
) {
    private var onContentChangeCallback: ((String) -> Unit)? = null
    private var isSettingText = false

    fun setupContentChangeListener() {
        val contentChangeListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val content = view.getText()
                if (isSettingText) {
                    return
                }

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
            view.editor.removeDocumentListener(contentChangeListener)
        }
    }

    fun setText(text: String) {
        isSettingText = true
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                view.setText(text)
            }
        } finally {
            isSettingText = false
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
        invokeLater {
            if (project.isDisposed) return@invokeLater

            val document = view.editor.document
            if (document.modificationStamp != expectedModificationStamp) return@invokeLater

            setText(normalizedContent)
            onContentChangeCallback?.invoke(normalizedContent)
        }
    }
}
