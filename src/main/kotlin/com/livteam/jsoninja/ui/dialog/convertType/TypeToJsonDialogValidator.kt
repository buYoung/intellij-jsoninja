package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import javax.swing.JComponent

object TypeToJsonDialogValidator {
    fun validate(
        sourceCode: String,
        outputCount: Int,
        validationComponent: JComponent,
    ): ValidationInfo? = validate(
        sourceCode = sourceCode,
        outputCount = outputCount,
        inputValidationComponent = validationComponent,
        outputCountValidationComponent = validationComponent,
    )

    fun validate(
        sourceCode: String,
        outputCount: Int,
        inputValidationComponent: JComponent,
        outputCountValidationComponent: JComponent,
    ): ValidationInfo? {
        if (sourceCode.isBlank()) {
            return ValidationInfo(LocalizationBundle.message("validation.type.to.json.empty.input"), inputValidationComponent)
        }
        if (outputCount !in 1..100) {
            return ValidationInfo(LocalizationBundle.message("validation.type.to.json.output.count"), outputCountValidationComponent)
        }
        return null
    }
}
