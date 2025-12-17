package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

import com.livteam.jsoninja.ui.component.model.JsonQueryModel

class JsonEditorPresenter(
    private val project: Project,
    private val view: JsonEditorView,
    private val model: JsonQueryModel
) {
    private var onContentChangeCallback: ((String) -> Unit)? = null
    var isSettingText = false

    fun setupContentChangeListener() {
        val contentChangeListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val content = view.getText()
                if (isSettingText) {
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
        this.isSettingText = true
        WriteCommandAction.runWriteCommandAction(project) {
            view.editor.text = text
            this.isSettingText = false
        }
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
