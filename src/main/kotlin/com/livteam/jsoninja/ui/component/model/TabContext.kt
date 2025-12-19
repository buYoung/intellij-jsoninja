package com.livteam.jsoninja.ui.component.model

import com.intellij.openapi.Disposable
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import javax.swing.JPanel

data class TabContext(
    val panel: JPanel,
    val editor: JsonEditorView,
    val disposable: Disposable
)
