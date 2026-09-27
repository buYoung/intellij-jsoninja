package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.random.GenerateRandomJsonTabPresenter
import com.livteam.jsoninja.ui.dialog.generateJson.schema.GenerateSchemaJsonTabPresenter
import javax.swing.JComponent

class GenerateJsonDialogPresenter(
    project: Project,
    private val onLayoutChanged: () -> Unit
) {
    private val randomTabPresenter = GenerateRandomJsonTabPresenter()
    private val schemaTabPresenter = GenerateSchemaJsonTabPresenter(project)
    private val view = GenerateJsonDialogView(
        randomTabComponent = randomTabPresenter.getComponent(),
        schemaTabComponent = schemaTabPresenter.getComponent(),
        onGenerationModeChanged = ::onGenerationModeChanged
    )

    init {
        randomTabPresenter.setOnOptionsChanged(::onOptionsChanged)
        schemaTabPresenter.setOnOptionsChanged(::onOptionsChanged)
    }

    fun registerValidators(parentDisposable: Disposable) {
        randomTabPresenter.registerValidators(parentDisposable)
        schemaTabPresenter.registerValidators(parentDisposable)
    }

    fun dispose() {
        randomTabPresenter.dispose()
        schemaTabPresenter.dispose()
    }

    fun getComponent(): JComponent {
        val component = view.component
        updateSummary()
        return component
    }

    fun getGenerationMode(): JsonGenerationMode = view.getGenerationMode()

    fun getPreferredFocusedComponent(): JComponent = when (view.getGenerationMode()) {
        JsonGenerationMode.RANDOM -> randomTabPresenter.getPreferredFocusedComponent()
        JsonGenerationMode.SCHEMA -> schemaTabPresenter.getPreferredFocusedComponent()
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

    private fun onOptionsChanged() {
        updateSummary()
        onLayoutChanged()
    }

    private fun onGenerationModeChanged() {
        schemaTabPresenter.setActive(view.getGenerationMode() == JsonGenerationMode.SCHEMA)
        onOptionsChanged()
    }

    private fun updateSummary() {
        view.setSummary(
            when (view.getGenerationMode()) {
                JsonGenerationMode.RANDOM -> randomTabPresenter.getSummary()
                JsonGenerationMode.SCHEMA -> schemaTabPresenter.getSummary()
            }
        )
    }
}
