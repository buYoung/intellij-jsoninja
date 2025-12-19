package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import javax.swing.JComponent

class GenerateJsonDialog(project: Project?) : DialogWrapper(project) {

    private val presenter = GenerateJsonDialogPresenter { pack() }

    init {
        title = LocalizationBundle.message("dialog.generate.json.title")
        setOKButtonText(LocalizationBundle.message("button.generate"))
        init()
    }

    override fun createCenterPanel(): JComponent {
        return presenter.getComponent()
    }

    override fun doValidate(): ValidationInfo? {
        return presenter.validate() ?: super.doValidate()
    }

    fun getConfig(): JsonGenerationConfig {
        return presenter.getConfig()
    }
}
