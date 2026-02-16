package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.random.GenerateRandomJsonTabPresenter
import com.livteam.jsoninja.ui.dialog.generateJson.schema.GenerateSchemaJsonTabPresenter
import javax.swing.JComponent

class GenerateJsonDialogPresenter(
    project: Project?,
    onLayoutChanged: () -> Unit
) {
    private val randomTabPresenter = GenerateRandomJsonTabPresenter(onLayoutChanged)
    private val schemaTabPresenter = GenerateSchemaJsonTabPresenter(project)
    private val view = GenerateJsonDialogView(
        randomTabComponent = randomTabPresenter.getComponent(),
        schemaTabComponent = schemaTabPresenter.getComponent(),
        onLayoutChanged = onLayoutChanged
    )

    fun dispose() {
        randomTabPresenter.dispose()
        schemaTabPresenter.dispose()
    }

    fun getComponent(): JComponent {
        return view.component
    }

    fun validate(): ValidationInfo? {
        return when (view.getGenerationMode()) {
            JsonGenerationMode.RANDOM -> randomTabPresenter.validate()
            JsonGenerationMode.SCHEMA -> schemaTabPresenter.validate()
        }
    }

    fun getConfig(): JsonGenerationConfig {
        return when (view.getGenerationMode()) {
            JsonGenerationMode.RANDOM -> randomTabPresenter.getConfig()
            JsonGenerationMode.SCHEMA -> schemaTabPresenter.getConfig()
        }
    }
}
