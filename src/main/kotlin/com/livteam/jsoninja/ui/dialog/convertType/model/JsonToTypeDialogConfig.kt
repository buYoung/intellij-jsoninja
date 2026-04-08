package com.livteam.jsoninja.ui.dialog.convertType.model

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.JsonToTypeAnnotationStyle
import com.livteam.jsoninja.services.typeConversion.NamingConvention

data class JsonToTypeDialogConfig(
    val rootTypeName: String = "Root",
    val language: SupportedLanguage = SupportedLanguage.KOTLIN,
    val namingConvention: NamingConvention = language.defaultNamingConvention,
    val annotationStyle: JsonToTypeAnnotationStyle = language.defaultAnnotationStyle,
    val allowsNullableFields: Boolean = true,
    val usesExperimentalGoUnionTypes: Boolean = false,
)
