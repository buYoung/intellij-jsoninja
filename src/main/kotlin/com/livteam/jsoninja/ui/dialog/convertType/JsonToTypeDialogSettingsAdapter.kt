package com.livteam.jsoninja.ui.dialog.convertType

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.JsonToTypeAnnotationStyle
import com.livteam.jsoninja.services.typeConversion.NamingConvention
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.dialog.convertType.model.JsonToTypeDialogConfig

class JsonToTypeDialogSettingsAdapter(
    private val settings: JsoninjaSettingsState,
) {
    fun load(): JsonToTypeDialogConfig {
        val language = SupportedLanguage.fromPersistedValue(settings.jsonToTypeLastLanguage)
        return JsonToTypeDialogConfig(
            language = language,
            namingConvention = NamingConvention.fromPersistedValue(settings.jsonToTypeDefaultNaming)
                ?: language.defaultNamingConvention,
            annotationStyle = JsonToTypeAnnotationStyle.fromPersistedValue(settings.jsonToTypeAnnotationStyle)
                ?: language.defaultAnnotationStyle,
            allowsNullableFields = settings.jsonToTypeNullableByDefault,
            usesExperimentalGoUnionTypes = settings.jsonToTypeUsesExperimentalGoUnionTypes,
        )
    }

    fun applyLanguageDefaults(
        currentConfig: JsonToTypeDialogConfig,
        language: SupportedLanguage,
    ): JsonToTypeDialogConfig {
        return currentConfig.copy(
            language = language,
            namingConvention = language.defaultNamingConvention,
            annotationStyle = language.defaultAnnotationStyle,
        )
    }

    fun save(config: JsonToTypeDialogConfig) {
        settings.jsonToTypeLastLanguage = config.language.name
        settings.jsonToTypeDefaultNaming = config.namingConvention.name
        settings.jsonToTypeAnnotationStyle = config.annotationStyle.name
        settings.jsonToTypeNullableByDefault = config.allowsNullableFields
        settings.jsonToTypeUsesExperimentalGoUnionTypes = config.usesExperimentalGoUnionTypes
    }
}
