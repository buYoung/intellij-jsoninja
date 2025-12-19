package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import javax.swing.JComponent

class GenerateJsonDialogPresenter(onLayoutChanged: () -> Unit) {

    private val view = GenerateJsonDialogView(onLayoutChanged)

    fun getComponent(): JComponent {
        return view.component
    }

    fun validate(): ValidationInfo? {
        return view.validate()
    }

    fun getConfig(): JsonGenerationConfig {
        return view.getConfig()
    }
}
