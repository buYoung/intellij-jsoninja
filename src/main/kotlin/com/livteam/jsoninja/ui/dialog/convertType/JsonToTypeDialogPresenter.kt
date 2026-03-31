package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.Alarm
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.NamingConvention
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.typeConversion.JsonToTypeAnnotationStyle
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionOptions
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelPresenter
import com.livteam.jsoninja.ui.dialog.convertType.model.JsonToTypeDialogConfig
import com.livteam.jsoninja.utils.ConvertResultUtils
import javax.swing.JComponent

class JsonToTypeDialogPresenter(
    private val project: com.intellij.openapi.project.Project,
    private val panelPresenter: JsoninjaPanelPresenter,
    private val sourceJsonText: String,
) : Disposable {
    private val settings = JsoninjaSettingsState.getInstance(project)
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val conversionService = project.service<JsonToTypeConversionService>()
    private val previewAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val view = JsonToTypeDialogView(project, createInitialConfig())

    @Volatile
    private var isDisposed = false

    @Volatile
    private var previewRequestSequence = 0

    init {
        view.setOnLanguageChanged { language ->
            settings.jsonToTypeLastLanguage = language.name
            settings.convertTypeLastLanguage = language.name

            val selectedAnnotationStyle = JsonToTypeAnnotationStyle.fromNameOrDefault(
                settings.jsonToTypeAnnotationStyle,
                language,
            )
            view.setSelectedLanguage(language)
            view.updateAnnotationStyleOptions(language, selectedAnnotationStyle)
            if (settings.jsonToTypeDefaultNaming == AUTO_NAMING_SETTING) {
                view.setNamingConvention(language.defaultNamingConvention)
            }
            schedulePreviewRefresh()
        }
        view.setOnOptionsChanged {
            persistSettings(view.getConfig())
            schedulePreviewRefresh()
        }
        view.clearPreview()
        schedulePreviewRefresh()
    }

    fun getComponent(): JComponent {
        return view.component
    }

    fun validate(): ValidationInfo? {
        if (sourceJsonText.isBlank()) {
            return ValidationInfo(
                LocalizationBundle.message("validation.json.to.type.empty.input"),
                view.getRootNameInputComponent(),
            )
        }

        val config = view.getConfig()
        if (!isValidIdentifier(config.rootTypeName)) {
            return ValidationInfo(
                LocalizationBundle.message("validation.json.to.type.root.name.invalid"),
                view.getRootNameInputComponent(),
            )
        }

        return runCatching {
            objectMapper.readTree(sourceJsonText)
        }.fold(
            onSuccess = { null },
            onFailure = { throwable ->
                ValidationInfo(
                    LocalizationBundle.message(
                        "validation.json.to.type.invalid.json",
                        throwable.message ?: throwable.javaClass.simpleName,
                    ),
                    view.getRootNameInputComponent(),
                )
            },
        )
    }

    fun insertPreviewTypeDeclaration(): Boolean {
        val validationInfo = validate()
        if (validationInfo != null) {
            view.setPreviewError(validationInfo.message)
            return false
        }

        val generatedTypeDeclaration = runCatching { generatePreviewTypeDeclaration(view.getConfig()) }
            .getOrElse { throwable ->
                view.setPreviewError(
                    LocalizationBundle.message(
                        "notification.json.to.type.conversion.failed",
                        throwable.message ?: throwable.javaClass.simpleName,
                    )
                )
                return false
            }

        ConvertResultUtils.insertToNewTab(
            text = generatedTypeDeclaration,
            panel = panelPresenter,
            fileExtension = view.getConfig().supportedLanguage.fileExtension,
        )
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
        previewAlarm.addRequest(
            {
                refreshPreview()
            },
            300,
        )
    }

    private fun refreshPreview() {
        if (isDisposed) return

        val validationInfo = validate()
        if (validationInfo != null) {
            view.setPreviewError(validationInfo.message)
            return
        }

        val config = view.getConfig()
        persistSettings(config)
        view.setPreviewLoading()
        val requestSequence = ++previewRequestSequence

        ApplicationManager.getApplication().executeOnPooledThread {
            val generatedTypeDeclaration = runCatching { generatePreviewTypeDeclaration(config) }
            invokeLater(ModalityState.any()) {
                if (isDisposed || requestSequence != previewRequestSequence) {
                    return@invokeLater
                }

                generatedTypeDeclaration.fold(
                    onSuccess = { previewText ->
                        view.setPreviewContent(previewText)
                    },
                    onFailure = { throwable ->
                        view.setPreviewError(
                            throwable.message ?: LocalizationBundle.message(
                                "notification.json.to.type.conversion.failed",
                                throwable.javaClass.simpleName,
                            )
                        )
                    },
                )
            }
        }
    }

    private fun generatePreviewTypeDeclaration(config: JsonToTypeDialogConfig): String {
        return conversionService.generateTypeDeclaration(
            language = config.supportedLanguage,
            sourceJson = sourceJsonText,
            options = JsonToTypeConversionOptions(
                rootTypeName = config.rootTypeName,
                namingConvention = config.namingConvention,
                annotationStyle = config.annotationStyle,
                allowsNullableFields = config.allowsNullableFields,
            ),
        )
    }

    private fun createInitialConfig(): JsonToTypeDialogConfig {
        val supportedLanguage = SupportedLanguage.fromNameOrDefault(settings.jsonToTypeLastLanguage)
        return JsonToTypeDialogConfig(
            supportedLanguage = supportedLanguage,
            allowsNullableFields = settings.jsonToTypeNullableByDefault,
            namingConvention = resolveNamingConvention(supportedLanguage),
            annotationStyle = JsonToTypeAnnotationStyle.fromNameOrDefault(
                settings.jsonToTypeAnnotationStyle,
                supportedLanguage,
            ),
        )
    }

    private fun persistSettings(config: JsonToTypeDialogConfig) {
        settings.jsonToTypeLastLanguage = config.supportedLanguage.name
        settings.convertTypeLastLanguage = config.supportedLanguage.name
        settings.jsonToTypeDefaultNaming = config.namingConvention.name
        settings.jsonToTypeNullableByDefault = config.allowsNullableFields
        settings.jsonToTypeAnnotationStyle = config.annotationStyle.name
    }

    private fun resolveNamingConvention(language: SupportedLanguage): NamingConvention {
        return if (settings.jsonToTypeDefaultNaming == AUTO_NAMING_SETTING) {
            language.defaultNamingConvention
        } else {
            runCatching {
                NamingConvention.valueOf(settings.jsonToTypeDefaultNaming)
            }.getOrDefault(language.defaultNamingConvention)
        }
    }

    private fun isValidIdentifier(value: String): Boolean {
        return value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))
    }

    companion object {
        private const val AUTO_NAMING_SETTING = "AUTO"
    }
}
