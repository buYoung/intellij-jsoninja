package com.livteam.jsoninja.ui.dialog.convertType

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.typeConversion.JsonToTypeNamingSupport
import javax.swing.JComponent
import kotlinx.coroutines.CancellationException

class JsonToTypeDialogValidator(
    private val objectMapper: ObjectMapper,
) {
    fun validate(
        jsonText: String,
        rootTypeName: String,
        validationComponent: JComponent,
    ): ValidationInfo? {
        validateFields(jsonText, rootTypeName, validationComponent)?.let { return it }
        return getJsonErrorMessage(jsonText)?.let { ValidationInfo(it, validationComponent) }
    }

    fun validateFields(
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
        return null
    }

    fun getJsonErrorMessage(jsonText: String): String? = try {
        objectMapper.readTree(jsonText)
        null
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (exception: Exception) {
        LocalizationBundle.message("validation.json.to.type.invalid.json", exception.message ?: exception.javaClass.simpleName)
    }
}
