package com.livteam.jsoninja.ui.dialog.convertType.model

import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode

data class TypeToJsonDialogConfig(
    val supportedLanguage: SupportedLanguage = SupportedLanguage.KOTLIN,
    val typeDeclarationText: String = "",
    val propertyGenerationMode: SchemaPropertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL,
    val includesNullableFieldWithNullValue: Boolean = true,
    val usesRealisticSampleData: Boolean = true,
    val outputCount: Int = 1,
    val jsonFormatState: JsonFormatState = JsonFormatState.PRETTIFY,
)
