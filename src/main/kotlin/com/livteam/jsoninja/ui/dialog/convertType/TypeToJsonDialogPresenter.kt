package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationOptions
import com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.utils.ConvertResultUtils

class TypeToJsonDialogPresenter(
    private val project: Project,
    initialInputText: String,
    private val onLanguageChanged: (SupportedLanguage) -> Unit,
) {
    private val settingsAdapter = TypeToJsonDialogSettingsAdapter(JsoninjaSettingsState.getInstance(project))
    private val generationService = project.getService(TypeToJsonGenerationService::class.java)
    private val previewExecutor = ConvertPreviewExecutor()
    private val view = TypeToJsonDialogView(project)
    private var currentConfig = settingsAdapter.load()
    private var currentPreviewText: String = ""
    private var isApplyingState = false

    init {
        bindView()
        applyConfig()
        view.setInputText(initialInputText)
        schedulePreview()
    }

    val component
        get() = view.component

    fun updateLanguage(language: SupportedLanguage) {
        currentConfig = currentConfig.copy(language = language)
        applyConfig()
        settingsAdapter.save(currentConfig)
        schedulePreview()
    }

    fun validate(): ValidationInfo? {
        return TypeToJsonDialogValidator.validate(
            sourceCode = view.getInputText(),
            outputCount = view.collectConfig().outputCount,
            validationComponent = view.getValidationComponent(),
        )
    }

    fun getCurrentPreviewText(): String = currentPreviewText

    fun getOutputFileExtension(): String = "json"

    fun copyPreview() {
        if (currentPreviewText.isNotBlank()) {
            ConvertResultUtils.copyToClipboard(currentPreviewText, project)
        }
    }

    fun dispose() {
        previewExecutor.dispose()
        view.dispose()
    }

    private fun bindView() {
        view.setOnStateChanged {
            if (isApplyingState) {
                return@setOnStateChanged
            }
            val updatedConfig = view.collectConfig()
            val previousLanguage = currentConfig.language
            currentConfig = updatedConfig
            settingsAdapter.save(currentConfig)
            if (previousLanguage != updatedConfig.language) {
                onLanguageChanged(updatedConfig.language)
            }
            schedulePreview()
        }
        view.setOnCopyRequested { copyPreview() }
    }

    private fun applyConfig() {
        isApplyingState = true
        view.applyConfig(currentConfig)
        isApplyingState = false
    }

    private fun schedulePreview() {
        val validationInfo = validate()
        if (validationInfo != null) {
            currentPreviewText = ""
            view.showErrorPreview(validationInfo.message)
            return
        }
        if (view.getInputText().isBlank()) {
            currentPreviewText = ""
            view.showEmptyPreview()
            return
        }

        previewExecutor.submit(
            delayMs = 500,
            onLoading = { view.showLoadingPreview() },
            computePreview = {
                generationService.generate(
                    sourceCode = view.getInputText(),
                    language = currentConfig.language,
                    options = TypeToJsonGenerationOptions(
                        propertyGenerationMode = currentConfig.propertyGenerationMode,
                        includesNullableFieldWithNullValue = currentConfig.includesNullableFieldWithNullValue,
                        usesRealisticSampleData = currentConfig.usesRealisticSampleData,
                        outputCount = currentConfig.outputCount,
                        formatState = currentConfig.formatState,
                    ),
                )
            },
            onSuccess = { previewText ->
                currentPreviewText = previewText
                view.showSuccessPreview(previewText)
            },
            onError = { error ->
                currentPreviewText = ""
                view.showErrorPreview(error.message ?: error.javaClass.simpleName)
            },
        )
    }
}
