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
    ): ValidationInfo? = validate(
        jsonText = jsonText,
        rootTypeName = rootTypeName,
        inputValidationComponent = validationComponent,
        rootTypeNameValidationComponent = validationComponent,
    )

    fun validate(
        jsonText: String,
        rootTypeName: String,
        inputValidationComponent: JComponent,
        rootTypeNameValidationComponent: JComponent,
    ): ValidationInfo? {
        validateFields(
            jsonText = jsonText,
            rootTypeName = rootTypeName,
            inputValidationComponent = inputValidationComponent,
            rootTypeNameValidationComponent = rootTypeNameValidationComponent,
        )?.let { return it }
        return getJsonErrorMessage(jsonText)?.let { ValidationInfo(it, inputValidationComponent) }
    }

    fun validateFields(
        jsonText: String,
        rootTypeName: String,
        validationComponent: JComponent,
    ): ValidationInfo? = validateFields(
        jsonText = jsonText,
        rootTypeName = rootTypeName,
        inputValidationComponent = validationComponent,
        rootTypeNameValidationComponent = validationComponent,
    )

    fun validateFields(
        jsonText: String,
        rootTypeName: String,
        inputValidationComponent: JComponent,
        rootTypeNameValidationComponent: JComponent,
    ): ValidationInfo? {
        if (jsonText.isBlank()) {
            return ValidationInfo(LocalizationBundle.message("validation.json.to.type.empty.input"), inputValidationComponent)
        }
        if (!JsonToTypeNamingSupport.isValidTypeIdentifier(rootTypeName)) {
            return ValidationInfo(LocalizationBundle.message("validation.json.to.type.root.name.invalid"), rootTypeNameValidationComponent)
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
