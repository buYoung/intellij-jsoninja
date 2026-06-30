package com.livteam.jsoninja.ui.dialog.convertType

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.typeConversion.JsonToTypeNamingSupport
import javax.swing.JComponent

class JsonToTypeDialogValidator(
    private val objectMapper: ObjectMapper,
) {
    fun validate(
        jsonText: String,
        rootTypeName: String,
        validationComponent: JComponent,
    ): ValidationInfo? {
        if (jsonText.isBlank()) {
            return ValidationInfo(LocalizationBundle.message("validation.json.to.type.empty.input"), validationComponent)
        }
        if (!JsonToTypeNamingSupport.isValidTypeIdentifier(rootTypeName)) {
            return ValidationInfo(LocalizationBundle.message("validation.json.to.type.root.name.invalid"), validationComponent)
        }
        val jsonErrorMessage = runCatching { objectMapper.readTree(jsonText) }.exceptionOrNull()?.message
        if (jsonErrorMessage != null) {
            return ValidationInfo(LocalizationBundle.message("validation.json.to.type.invalid.json", jsonErrorMessage), validationComponent)
        }
        return null
    }
}
