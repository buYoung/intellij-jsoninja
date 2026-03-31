package com.livteam.jsoninja.ui.dialog.convertType.model

import com.livteam.jsoninja.model.NamingConvention
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.JsonToTypeAnnotationStyle

data class JsonToTypeDialogConfig(
    val supportedLanguage: SupportedLanguage = SupportedLanguage.KOTLIN,
    val rootTypeName: String = "Root",
    val allowsNullableFields: Boolean = true,
    val namingConvention: NamingConvention = SupportedLanguage.KOTLIN.defaultNamingConvention,
    val annotationStyle: JsonToTypeAnnotationStyle = JsonToTypeAnnotationStyle.NONE,
)
