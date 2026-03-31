package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.Alarm
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationOptions
import com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelPresenter
import com.livteam.jsoninja.ui.dialog.convertType.model.TypeToJsonDialogConfig
import com.livteam.jsoninja.utils.ConvertResultUtils
import javax.swing.JComponent

class TypeToJsonDialogPresenter(
    private val project: Project,
    private val panelPresenter: JsoninjaPanelPresenter,
) : Disposable {
    private val settings = JsoninjaSettingsState.getInstance(project)
    private val generationService = project.service<TypeToJsonGenerationService>()
    private val previewAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val view = TypeToJsonDialogView(project, createInitialConfig())

    @Volatile
    private var isDisposed = false

    @Volatile
    private var previewRequestSequence = 0

    private var latestGeneratedJson = ""

    init {
        view.setOnLanguageChanged { language ->
            settings.typeToJsonLastLanguage = language.name
            settings.convertTypeLastLanguage = language.name
            view.setSelectedLanguage(language)
            view.setInputPlaceholder(resolvePlaceholder(language))
            schedulePreviewRefresh()
        }
        view.setOnTypeDeclarationChanged {
            schedulePreviewRefresh()
        }
        view.setOnOptionsChanged {
            persistSettings(view.getConfig())
            schedulePreviewRefresh()
        }
        view.setInputPlaceholder(resolvePlaceholder(view.getConfig().supportedLanguage))
        view.clearPreview()
    }

    fun getComponent(): JComponent {
        return view.component
    }

    fun validate(): ValidationInfo? {
        val config = view.getConfig()
        if (config.typeDeclarationText.trim().isEmpty()) {
            return ValidationInfo(
                LocalizationBundle.message("validation.type.to.json.empty.input"),
                view.getTypeDeclarationInputComponent(),
            )
        }
        if (config.outputCount !in 1..100) {
            return ValidationInfo(
                LocalizationBundle.message("validation.type.to.json.output.count"),
                view.getOutputCountInputComponent(),
            )
        }
        return null
    }

    fun insertPreviewJson(): Boolean {
        val validationInfo = validate()
        if (validationInfo != null) {
            view.setPreviewError(validationInfo.message)
            return false
        }

        val generatedJson = generatePreviewJson(view.getConfig()) ?: return false
        val fileExtension = if (view.getConfig().propertyGenerationMode.name.endsWith("COMMENTED")) "json5" else "json"
        ConvertResultUtils.insertToNewTab(generatedJson, panelPresenter, fileExtension)
        latestGeneratedJson = generatedJson
        return true
    }

    override fun dispose() {
        isDisposed = true
        previewAlarm.cancelAllRequests()
        view.dispose()
    }

    private fun schedulePreviewRefresh() {
        if (isDisposed) return

        previewAlarm.cancelAllRequests()
        previewAlarm.addRequest({
            refreshPreview()
        }, 500)
    }

    private fun refreshPreview() {
        if (isDisposed) return

        val validationInfo = validate()
        if (validationInfo != null) {
            latestGeneratedJson = ""
            if (view.getConfig().typeDeclarationText.isBlank()) {
                view.clearPreview()
            } else {
                view.setPreviewError(validationInfo.message)
            }
            return
        }

        val config = view.getConfig()
        persistSettings(config)
        latestGeneratedJson = ""
        view.setPreviewLoading()
        val requestSequence = ++previewRequestSequence

        ApplicationManager.getApplication().executeOnPooledThread {
            val generatedJson = runCatching { generatePreviewJson(config) }
            invokeLater(ModalityState.any()) {
                if (isDisposed || requestSequence != previewRequestSequence) {
                    return@invokeLater
                }

                generatedJson.fold(
                    onSuccess = { previewText ->
                        if (previewText.isNullOrBlank()) {
                            latestGeneratedJson = ""
                            view.clearPreview()
                        } else {
                            latestGeneratedJson = previewText
                            view.setPreviewContent(previewText)
                        }
                    },
                    onFailure = { throwable ->
                        latestGeneratedJson = ""
                        view.setPreviewError(
                            throwable.message ?: LocalizationBundle.message(
                                "validation.type.to.json.parse.failed",
                                throwable.javaClass.simpleName,
                            )
                        )
                    },
                )
            }
        }
    }

    private fun generatePreviewJson(config: TypeToJsonDialogConfig): String? {
        if (config.typeDeclarationText.trim().isEmpty()) {
            return null
        }

        return generationService.generateJsonFromTypeDeclaration(
            language = config.supportedLanguage.toTypeConversionLanguage(),
            sourceCode = config.typeDeclarationText,
            options = TypeToJsonGenerationOptions(
                propertyGenerationMode = config.propertyGenerationMode,
                includesNullableFieldWithNullValue = config.includesNullableFieldWithNullValue,
                usesRealisticSampleData = config.usesRealisticSampleData,
                outputCount = config.outputCount,
                formatState = config.jsonFormatState,
            ),
        )
    }

    private fun createInitialConfig(): TypeToJsonDialogConfig {
        return TypeToJsonDialogConfig(
            supportedLanguage = SupportedLanguage.fromNameOrDefault(settings.typeToJsonLastLanguage),
            propertyGenerationMode = runCatching {
                com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode.valueOf(settings.typeToJsonFieldsMode)
            }.getOrDefault(com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL),
            includesNullableFieldWithNullValue = settings.typeToJsonIncludesNullableFieldWithNullValue,
            usesRealisticSampleData = settings.typeToJsonUsesRealisticSampleData,
            outputCount = settings.typeToJsonOutputCount,
            jsonFormatState = runCatching {
                com.livteam.jsoninja.model.JsonFormatState.valueOf(settings.typeToJsonFormatState)
            }.getOrDefault(com.livteam.jsoninja.model.JsonFormatState.PRETTIFY),
        )
    }

    private fun persistSettings(config: TypeToJsonDialogConfig) {
        settings.typeToJsonLastLanguage = config.supportedLanguage.name
        settings.convertTypeLastLanguage = config.supportedLanguage.name
        settings.typeToJsonFieldsMode = config.propertyGenerationMode.name
        settings.typeToJsonIncludesNullableFieldWithNullValue = config.includesNullableFieldWithNullValue
        settings.typeToJsonUsesRealisticSampleData = config.usesRealisticSampleData
        settings.typeToJsonOutputCount = config.outputCount
        settings.typeToJsonFormatState = config.jsonFormatState.name
    }

    private fun resolvePlaceholder(language: SupportedLanguage): String {
        return when (language) {
            SupportedLanguage.JAVA -> LocalizationBundle.message("dialog.type.to.json.input.placeholder.java")
            SupportedLanguage.KOTLIN -> LocalizationBundle.message("dialog.type.to.json.input.placeholder.kotlin")
            SupportedLanguage.TYPESCRIPT -> LocalizationBundle.message("dialog.type.to.json.input.placeholder.typescript")
            SupportedLanguage.GO -> LocalizationBundle.message("dialog.type.to.json.input.placeholder.go")
        }
    }
}
