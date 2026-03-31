package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode

data class TypeToJsonGenerationOptions(
    val propertyGenerationMode: SchemaPropertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL,
    val includesNullableFieldWithNullValue: Boolean = true,
    val usesRealisticSampleData: Boolean = true,
    val outputCount: Int = 1,
    val formatState: JsonFormatState = JsonFormatState.PRETTIFY,
)
