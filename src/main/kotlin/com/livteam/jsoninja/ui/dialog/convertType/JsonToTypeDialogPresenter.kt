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
import com.livteam.jsoninja.ui.dialog.convertType.model.JsonToTypeDialogConfig
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
    private var previewState: ConvertPreviewState<JsonToTypeDialogConfig> = ConvertPreviewState.Invalid()
    private var onPreviewStateChanged: (() -> Unit)? = null
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
        invalidatePreview()
        currentConfig = settingsAdapter.applyLanguageDefaults(currentConfig, language)
        applyConfig()
        settingsAdapter.save(currentConfig)
        schedulePreview()
    }

    fun validate(): ValidationInfo? {
        return validator.validateFields(
            jsonText = view.getInputText(),
            rootTypeName = view.collectConfig().rootTypeName,
            validationComponent = view.getValidationComponent(),
        ) ?: (previewState as? ConvertPreviewState.Invalid)?.message?.let {
            ValidationInfo(it, view.getValidationComponent())
        }
    }

    fun getCurrentPreviewText(): String {
        val ready = previewState as? ConvertPreviewState.Ready<*> ?: return ""
        return if (ready.input == view.getInputText() && ready.config == view.collectConfig()) ready.text else ""
    }

    fun setOnPreviewStateChanged(callback: () -> Unit) {
        onPreviewStateChanged = callback
    }

    fun getOutputFileExtension(): String = currentConfig.language.fileExtension

    fun copyPreview() {
        val text = getCurrentPreviewText()
        if (text.isNotBlank()) {
            ConvertResultUtils.copyToClipboard(text, project)
        }
    }

    fun dispose() {
        updatePreviewState(ConvertPreviewState.Invalid())
        previewExecutor.dispose()
        coroutineScope.cancel()
        view.dispose()
    }

    private fun bindView() {
        view.setOnStateChanged {
            if (isApplyingState) {
                return@setOnStateChanged
            }
            invalidatePreview()
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
        invalidatePreview()
        val inputText = view.getInputText()
        if (inputText.isBlank()) {
            updatePreviewState(ConvertPreviewState.Invalid())
            view.showEmptyPreview()
            return
        }
        val validationInfo = validator.validateFields(inputText, currentConfig.rootTypeName, view.getValidationComponent())
        if (validationInfo != null) {
            updatePreviewState(ConvertPreviewState.Invalid(validationInfo.message))
            view.showErrorPreview(validationInfo.message)
            return
        }

        val previewConfig = currentConfig
        view.showLoadingPreview()
        previewExecutor.submit(
            delayMs = 300,
            onLoading = { view.showLoadingPreview() },
            computePreview = { checkCancellation ->
                checkCancellation()
                validator.getJsonErrorMessage(inputText)?.let { throw IllegalArgumentException(it) }
                checkCancellation()
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
                if (inputText == view.getInputText() && previewConfig == view.collectConfig()) {
                    updatePreviewState(ConvertPreviewState.Ready(inputText, previewConfig, previewText))
                    view.showSuccessPreview(previewText, previewConfig.language.fileExtension)
                }
            },
            onError = { error ->
                if (inputText == view.getInputText() && previewConfig == view.collectConfig()) {
                    val message = error.message ?: error.javaClass.simpleName
                    updatePreviewState(ConvertPreviewState.Invalid(message))
                    view.showErrorPreview(message)
                }
            },
        )
    }

    private fun invalidatePreview() {
        previewExecutor.cancel()
        updatePreviewState(ConvertPreviewState.Pending)
    }

    private fun updatePreviewState(state: ConvertPreviewState<JsonToTypeDialogConfig>) {
        previewState = state
        onPreviewStateChanged?.invoke()
    }
}
