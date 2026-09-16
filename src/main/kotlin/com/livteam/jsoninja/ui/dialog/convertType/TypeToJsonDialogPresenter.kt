package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationOptions
import com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.dialog.convertType.model.TypeToJsonDialogConfig
import com.livteam.jsoninja.utils.ConvertResultUtils
import kotlinx.coroutines.cancel

class TypeToJsonDialogPresenter(
    private val project: Project,
    initialInputText: String,
    private val onLanguageChanged: (SupportedLanguage) -> Unit,
) {
    private val settingsAdapter = TypeToJsonDialogSettingsAdapter(JsoninjaSettingsState.getInstance(project))
    private val generationService = project.getService(TypeToJsonGenerationService::class.java)
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    private val previewExecutor = ConvertPreviewExecutor(coroutineScope)
    private val view = TypeToJsonDialogView(project)
    private var currentConfig = settingsAdapter.load()
    private var previewState: ConvertPreviewState<TypeToJsonDialogConfig> = ConvertPreviewState.Invalid()
    private var onPreviewStateChanged: (() -> Unit)? = null
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
        invalidatePreview()
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

    fun getOutputFileExtension(): String = "json"

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

    private fun schedulePreview() {
        invalidatePreview()
        val inputText = view.getInputText()
        if (inputText.isBlank()) {
            updatePreviewState(ConvertPreviewState.Invalid())
            view.showEmptyPreview()
            return
        }
        val validationInfo = validate()
        if (validationInfo != null) {
            updatePreviewState(ConvertPreviewState.Invalid(validationInfo.message))
            view.showErrorPreview(validationInfo.message)
            return
        }
        val previewConfig = currentConfig
        view.showLoadingPreview()
        previewExecutor.submit(
            delayMs = 500,
            onLoading = { view.showLoadingPreview() },
            computePreview = { checkCancellation ->
                generationService.generate(
                    sourceCode = inputText,
                    language = previewConfig.language,
                    options = TypeToJsonGenerationOptions(
                        propertyGenerationMode = previewConfig.propertyGenerationMode,
                        includesNullableFieldWithNullValue = previewConfig.includesNullableFieldWithNullValue,
                        usesRealisticSampleData = previewConfig.usesRealisticSampleData,
                        outputCount = previewConfig.outputCount,
                        formatState = previewConfig.formatState,
                    ),
                    checkCancellation = checkCancellation,
                )
            },
            onSuccess = { previewText ->
                if (inputText == view.getInputText() && previewConfig == view.collectConfig()) {
                    updatePreviewState(ConvertPreviewState.Ready(inputText, previewConfig, previewText))
                    view.showSuccessPreview(previewText)
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

    private fun updatePreviewState(state: ConvertPreviewState<TypeToJsonDialogConfig>) {
        previewState = state
        onPreviewStateChanged?.invoke()
    }
}
