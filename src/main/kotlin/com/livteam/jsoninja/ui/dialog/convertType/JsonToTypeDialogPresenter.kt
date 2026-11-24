package com.livteam.jsoninja.ui.dialog.convertType

import com.fasterxml.jackson.core.JsonProcessingException
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.EditorTextField
import com.livteam.jsoninja.LocalizationBundle
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
    companion object {
        private const val NON_BLANK_SENTINEL = "x"
    }

    private val settingsAdapter = JsonToTypeDialogSettingsAdapter(JsoninjaSettingsState.getInstance(project))
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val validator = JsonToTypeDialogValidator(objectMapper)
    private val conversionService = project.getService(JsonToTypeConversionService::class.java)
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    private val previewExecutor = ConvertPreviewExecutor(coroutineScope)
    private val view = JsonToTypeDialogView(project)
    private var currentConfig = settingsAdapter.load()
    private var previewState: ConvertPreviewState<JsonToTypeDialogConfig> = ConvertPreviewState.Invalid()
    private var readyInputDocument: Document? = null
    private var readyInputStamp: Long = -1L
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

    fun getPreferredFocusedComponent() = view.getPreferredFocusedComponent()

    fun updateLanguage(language: SupportedLanguage) {
        invalidatePreview()
        currentConfig = settingsAdapter.applyLanguageDefaults(currentConfig, language)
        applyConfig()
        settingsAdapter.save(currentConfig)
        schedulePreview()
    }

    fun validate(): ValidationInfo? {
        return validator.validateFields(
            jsonText = if (isInputBlank()) "" else NON_BLANK_SENTINEL,
            rootTypeName = view.collectConfig().rootTypeName,
            inputValidationComponent = view.getInputValidationComponent(),
            rootTypeNameValidationComponent = view.getRootTypeNameValidationComponent(),
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

    fun getOutputFileExtension(): String = currentConfig.language.fileExtension

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
        if (isInputBlank()) {
            updatePreviewState(ConvertPreviewState.Invalid())
            view.showEmptyPreview()
            return
        }
        val validationInfo = validator.validateFields(
            jsonText = NON_BLANK_SENTINEL,
            rootTypeName = currentConfig.rootTypeName,
            inputValidationComponent = view.getInputValidationComponent(),
            rootTypeNameValidationComponent = view.getRootTypeNameValidationComponent(),
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
        previewExecutor.submit(
            delayMs = 300,
            onLoading = {
                if (isInputUnchanged(expectedDocument, expectedStamp, inputText) &&
                    previewConfig == view.collectConfig()
                ) {
                    view.showLoadingPreview()
                }
            },
            computePreview = { checkCancellation ->
                checkCancellation()
                try {
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
                } catch (jsonException: JsonProcessingException) {
                    throw IllegalArgumentException(
                        LocalizationBundle.message(
                            "validation.json.to.type.invalid.json",
                            jsonException.message ?: jsonException.javaClass.simpleName,
                        ),
                        jsonException,
                    )
                }
            },
            onSuccess = { previewText ->
                if (isInputUnchanged(expectedDocument, expectedStamp, inputText) &&
                    previewConfig == view.collectConfig()
                ) {
                    readyInputDocument = expectedDocument
                    readyInputStamp = expectedStamp
                    val retainedInput = if (expectedDocument != null) "" else inputText
                    updatePreviewState(ConvertPreviewState.Ready(retainedInput, previewConfig, previewText))
                    view.showSuccessPreview(previewText, previewConfig.language.fileExtension)
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
        updatePreviewState(ConvertPreviewState.Pending)
    }

    private fun updatePreviewState(state: ConvertPreviewState<JsonToTypeDialogConfig>) {
        previewState = state
        onPreviewStateChanged?.invoke()
    }
}
