package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.livteam.jsoninja.ui.component.model.JsonQueryUiState

class JsonEditorTextPresenter(
    private val project: Project,
    private val view: JsonEditorTextView,
    private val model: JsonQueryUiState
) {
    private var onContentChangeCallback: ((String) -> Unit)? = null
    private var isSettingText = false

    fun setupContentChangeListener() {
        val contentChangeListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (isSettingText) {
                    return
                }
                onContentChangeCallback?.invoke(view.getText())
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

    fun setOriginalJson(json: String) {
        model.originalJson = json
    }

    fun getOriginalJson(): String {
        return model.originalJson
    }

    fun setOnContentChangeCallback(callback: (String) -> Unit) {
        onContentChangeCallback = callback
    }
}
