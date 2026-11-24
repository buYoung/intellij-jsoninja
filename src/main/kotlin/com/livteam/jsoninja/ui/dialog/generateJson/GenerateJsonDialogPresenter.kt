package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.wm.IdeFocusManager
import com.livteam.jsoninja.services.random.RandomJsonGenerationSessionService
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.random.GenerateRandomJsonTabPresenter
import com.livteam.jsoninja.ui.dialog.generateJson.schema.GenerateSchemaJsonTabPresenter
import javax.swing.JComponent
import javax.swing.SwingUtilities

class GenerateJsonDialogPresenter(
    private val project: Project,
    private val onLayoutChanged: () -> Unit,
    onAdvancedOptionsChanged: () -> Unit = onLayoutChanged,
) {
    private val sessionService = project.service<GenerateJsonSessionService>()
    private val randomTabPresenter = GenerateRandomJsonTabPresenter(
        project.service<RandomJsonGenerationSessionService>().createInitialConfig()
    )
    private val schemaTabPresenter = GenerateSchemaJsonTabPresenter(
        project = project,
        initialConfig = sessionService.lastSchemaConfig,
        initialSchemaUrlText = sessionService.lastSchemaUrlText,
    )
    private var isInitializing = true
    private val view = GenerateJsonDialogView(
        randomTabComponent = randomTabPresenter.getComponent(),
        schemaTabComponent = schemaTabPresenter.getComponent(),
        onGenerationModeChanged = ::onGenerationModeChanged
    )

    init {
        view.component
        view.setGenerationMode(sessionService.lastGenerationMode)
        schemaTabPresenter.setActive(sessionService.lastGenerationMode == JsonGenerationMode.SCHEMA)
        isInitializing = false
        randomTabPresenter.setOnOptionsChanged(::onOptionsChanged)
        randomTabPresenter.setOnAdvancedOptionsChanged(onAdvancedOptionsChanged)
        schemaTabPresenter.setOnOptionsChanged(::onOptionsChanged)
    }

    fun registerValidators(parentDisposable: Disposable) {
        randomTabPresenter.registerValidators(parentDisposable)
        schemaTabPresenter.registerValidators(parentDisposable)
    }

    fun dispose() {
        persistSessionState()
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
            JsonGenerationMode.RANDOM -> randomTabPresenter.validate(shouldRevealSeedErrors = true)
            JsonGenerationMode.SCHEMA -> schemaTabPresenter.validate()
        }
    }

    fun getConfig(): JsonGenerationConfig {
        persistSessionState()
        return when (view.getGenerationMode()) {
            JsonGenerationMode.RANDOM -> randomTabPresenter.getConfig()
            JsonGenerationMode.SCHEMA -> schemaTabPresenter.getConfig()
        }
    }

    private fun persistSessionState() {
        sessionService.rememberGenerationMode(view.getGenerationMode())
        sessionService.rememberSchemaState(
            config = schemaTabPresenter.getCurrentSessionConfig(),
            schemaUrlText = schemaTabPresenter.getCurrentSchemaUrlText(),
        )
    }

    private fun onOptionsChanged() {
        updateSummary()
        onLayoutChanged()
    }

    private fun onGenerationModeChanged() {
        if (isInitializing) return
        val mode = view.getGenerationMode()
        sessionService.rememberGenerationMode(mode)
        schemaTabPresenter.setActive(mode == JsonGenerationMode.SCHEMA)
        onOptionsChanged()
        val target = getPreferredFocusedComponent()
        SwingUtilities.invokeLater {
            if (!project.isDisposed && target.isShowing) {
                IdeFocusManager.getInstance(project).requestFocus(target, true)
            }
        }
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
