package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionOptions
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.utils.ConvertResultUtils
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JsonToTypeDialogPresenter(
    private val project: Project,
    initialInputText: String,
    private val onLanguageChanged: (SupportedLanguage) -> Unit,
) {
    private val settingsAdapter = JsonToTypeDialogSettingsAdapter(JsoninjaSettingsState.getInstance(project))
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val validator = JsonToTypeDialogValidator(objectMapper)
    private val conversionService = project.getService(JsonToTypeConversionService::class.java)
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    private val previewExecutor = ConvertPreviewExecutor(coroutineScope)
    private val view = JsonToTypeDialogView(project)
    private var currentConfig = settingsAdapter.load()
    private var currentPreviewText: String = ""
    private var isApplyingState = false

    init {
        bindView()
        applyConfig()
        view.setInputText(initialInputText)
        scheduleInitialPreview()
    }

    val component
        get() = view.component

    fun updateLanguage(language: SupportedLanguage) {
        currentConfig = settingsAdapter.applyLanguageDefaults(currentConfig, language)
        applyConfig()
        settingsAdapter.save(currentConfig)
        schedulePreview()
    }

    fun validate(): ValidationInfo? {
        return validator.validate(
            jsonText = view.getInputText(),
            rootTypeName = view.collectConfig().rootTypeName,
            validationComponent = view.getValidationComponent(),
        )
    }

    fun getCurrentPreviewText(): String = currentPreviewText

    fun getOutputFileExtension(): String = currentConfig.language.fileExtension

    fun copyPreview() {
        if (currentPreviewText.isNotBlank()) {
            ConvertResultUtils.copyToClipboard(currentPreviewText, project)
        }
    }

    fun dispose() {
        previewExecutor.dispose()
        coroutineScope.cancel()
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

    private fun scheduleInitialPreview() {
        coroutineScope.launch {
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                if (project.isDisposed) {
                    return@withContext
                }
                schedulePreview()
            }
        }
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

        val inputText = view.getInputText()
        val previewConfig = currentConfig
        previewExecutor.submit(
            delayMs = 300,
            onLoading = { view.showLoadingPreview() },
            computePreview = {
                conversionService.convert(
                    jsonText = inputText,
                    language = previewConfig.language,
                    options = JsonToTypeConversionOptions(
                        rootTypeName = previewConfig.rootTypeName,
                        namingConvention = previewConfig.namingConvention,
                        annotationStyle = previewConfig.annotationStyle,
                        allowsNullableFields = previewConfig.allowsNullableFields,
                        usesExperimentalGoUnionTypes = previewConfig.usesExperimentalGoUnionTypes,
                    ),
                )
            },
            onSuccess = { previewText ->
                currentPreviewText = previewText
                view.showSuccessPreview(previewText, previewConfig.language.fileExtension)
            },
            onError = { error ->
                currentPreviewText = ""
                view.showErrorPreview(error.message ?: error.javaClass.simpleName)
            },
        )
    }
}
