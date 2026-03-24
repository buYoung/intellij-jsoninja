package com.livteam.jsoninja.ui.component.model

import com.intellij.openapi.Disposable
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.ui.component.jsonQuery.JsonQueryPresenter
import javax.swing.JPanel

data class TabUiState(
    val panel: JPanel,
    val editor: JsonEditorView,
    val queryPresenter: JsonQueryPresenter,
    val disposable: Disposable
)
