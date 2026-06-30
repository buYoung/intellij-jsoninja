package com.livteam.jsoninja.ui.dialog.convertType

import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.dialog.convertType.model.TypeToJsonDialogConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode

class TypeToJsonDialogSettingsAdapter(
    private val settings: JsoninjaSettingsState,
) {
    fun load(): TypeToJsonDialogConfig {
        return TypeToJsonDialogConfig(
            language = SupportedLanguage.fromPersistedValue(settings.typeToJsonLastLanguage),
            propertyGenerationMode = runCatching {
                SchemaPropertyGenerationMode.valueOf(settings.typeToJsonFieldsMode)
            }.getOrDefault(SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL),
            includesNullableFieldWithNullValue = settings.typeToJsonIncludesNullableFieldWithNullValue,
            usesRealisticSampleData = settings.typeToJsonUsesRealisticSampleData,
            outputCount = settings.typeToJsonOutputCount.coerceIn(1, 100),
            formatState = JsonFormatState.fromString(settings.typeToJsonFormatState),
        )
    }

    fun save(config: TypeToJsonDialogConfig) {
        settings.typeToJsonLastLanguage = config.language.name
        settings.typeToJsonFieldsMode = config.propertyGenerationMode.name
        settings.typeToJsonIncludesNullableFieldWithNullValue = config.includesNullableFieldWithNullValue
        settings.typeToJsonUsesRealisticSampleData = config.usesRealisticSampleData
        settings.typeToJsonOutputCount = config.outputCount.coerceIn(1, 100)
        settings.typeToJsonFormatState = config.formatState.name
    }
}
