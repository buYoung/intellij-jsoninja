package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.EditorTextField
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationOptions
import com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.dialog.convertType.model.TypeToJsonDialogConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import com.livteam.jsoninja.utils.ConvertResultUtils
import kotlinx.coroutines.cancel

class TypeToJsonDialogPresenter(
    private val project: Project,
    initialInputText: String,
    initialLanguageOverride: SupportedLanguage? = null,
    private val onLanguageChanged: (SupportedLanguage) -> Unit,
) {
    companion object {
        private const val NON_BLANK_SENTINEL = "x"
    }

    private val settingsAdapter = TypeToJsonDialogSettingsAdapter(JsoninjaSettingsState.getInstance(project))
    private val generationService = project.getService(TypeToJsonGenerationService::class.java)
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    private val previewExecutor = ConvertPreviewExecutor(coroutineScope)
    private val view = TypeToJsonDialogView(project)
    private var currentConfig = settingsAdapter.load().let { loaded ->
        if (initialLanguageOverride != null) loaded.copy(language = initialLanguageOverride) else loaded
    }
    private var previewState: ConvertPreviewState<TypeToJsonDialogConfig> = ConvertPreviewState.Invalid()
    private var readyInputDocument: Document? = null
    private var readyInputStamp: Long = -1L
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

    fun getPreferredFocusedComponent() = view.getPreferredFocusedComponent()

    fun updateLanguage(language: SupportedLanguage) {
        invalidatePreview()
        currentConfig = currentConfig.copy(language = language)
        applyConfig()
        settingsAdapter.save(currentConfig)
        schedulePreview()
    }

    fun validate(): ValidationInfo? {
        return TypeToJsonDialogValidator.validate(
            sourceCode = if (isInputBlank()) "" else NON_BLANK_SENTINEL,
            outputCount = view.collectConfig().outputCount,
            inputValidationComponent = view.getInputValidationComponent(),
            outputCountValidationComponent = view.getOutputCountValidationComponent(),
        ) ?: (previewState as? ConvertPreviewState.Invalid)?.message?.let {
            ValidationInfo(it, view.getInputValidationComponent())
        }
    }

    fun getCurrentPreviewText(): String {
        val ready = previewState as? ConvertPreviewState.Ready<*> ?: return ""
        return if (isInputUnchanged(readyInputDocument, readyInputStamp, ready.input) &&
            ready.config == view.collectConfig()
        ) {
            ready.text
        } else {
            ""
        }
    }

    fun setOnPreviewStateChanged(callback: () -> Unit) {
        onPreviewStateChanged = callback
    }

    fun getOutputFileExtension(): String =
        if (currentConfig.propertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED) "json5" else "json"

    fun copyPreview() {
        val text = getCurrentPreviewText()
        if (text.isNotBlank()) {
            ConvertResultUtils.copyToClipboard(text, project)
        }
    }

    fun dispose() {
        readyInputDocument = null
        readyInputStamp = -1L
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
        if (isInputBlank()) {
            updatePreviewState(ConvertPreviewState.Invalid())
            view.showEmptyPreview()
            return
        }
        val validationInfo = TypeToJsonDialogValidator.validate(
            sourceCode = NON_BLANK_SENTINEL,
            outputCount = currentConfig.outputCount,
            inputValidationComponent = view.getInputValidationComponent(),
            outputCountValidationComponent = view.getOutputCountValidationComponent(),
        )
        if (validationInfo != null) {
            updatePreviewState(ConvertPreviewState.Invalid(validationInfo.message))
            view.showErrorPreview(validationInfo.message)
            return
        }

        val expectedDocument = getInputDocument()
        val expectedStamp = expectedDocument?.modificationStamp ?: -1L
        val inputText = view.getInputText()
        val previewConfig = currentConfig
        previewExecutor.submitDetailed(
            delayMs = 500,
            onLoading = {
                if (isInputUnchanged(expectedDocument, expectedStamp, inputText) &&
                    previewConfig == view.collectConfig()
                ) {
                    view.showLoadingPreview()
                }
            },
            computePreview = { checkCancellation ->
                generationService.generateDetailed(
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
            onSuccess = { result ->
                if (isInputUnchanged(expectedDocument, expectedStamp, inputText) &&
                    previewConfig == view.collectConfig()
                ) {
                    val previewText = result.jsonText
                    readyInputDocument = expectedDocument
                    readyInputStamp = expectedStamp
                    val retainedInput = if (expectedDocument != null) "" else inputText
                    updatePreviewState(ConvertPreviewState.Ready(retainedInput, previewConfig, previewText))
                    val warnings = if (previewConfig.language.showsAnalysisWarnings) result.diagnostics
                        .filter { it.severity == com.livteam.jsoninja.model.typeConversion.TypeAnalysisSeverity.WARNING }
                        .map { it.message }.distinct() else emptyList()
                    view.showSuccessPreview(previewText, getOutputFileExtension(), warnings)
                }
            },
            onError = { error ->
                if (isInputUnchanged(expectedDocument, expectedStamp, inputText) &&
                    previewConfig == view.collectConfig()
                ) {
                    val message = error.message ?: error.javaClass.simpleName
                    updatePreviewState(ConvertPreviewState.Invalid(message))
                    view.showErrorPreview(message)
                }
            },
        )
    }

    private fun getInputDocument(): Document? =
        (view.getPreferredFocusedComponent() as? EditorTextField)?.document

    private fun isInputBlank(): Boolean {
        val document = getInputDocument()
        return if (document != null) {
            document.immutableCharSequence.isBlank()
        } else {
            view.getInputText().isBlank()
        }
    }

    private fun isInputUnchanged(
        expectedDocument: Document?,
        expectedStamp: Long,
        expectedInput: String,
    ): Boolean {
        if (expectedDocument != null) {
            val currentDocument = getInputDocument()
            return currentDocument === expectedDocument && currentDocument.modificationStamp == expectedStamp
        }
        return expectedInput == view.getInputText()
    }

    private fun invalidatePreview() {
        readyInputDocument = null
        readyInputStamp = -1L
        previewExecutor.cancel()
        view.stopLoadingIndicator()
        view.clearPreviewWarnings()
        updatePreviewState(ConvertPreviewState.Pending)
    }

    private fun updatePreviewState(state: ConvertPreviewState<TypeToJsonDialogConfig>) {
        previewState = state
        onPreviewStateChanged?.invoke()
    }
}
