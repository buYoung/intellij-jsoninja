package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationService
import com.livteam.jsoninja.services.schema.JsonSchemaGenerationException
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import javax.swing.JComponent

class GenerateJsonDialogPresenter(
    private val project: Project?,
    schemaPrefillProvider: (() -> String?)?,
    onLayoutChanged: () -> Unit
) {
    private val schemaDataGenerationService = project?.getService(JsonSchemaDataGenerationService::class.java)
    private val view = GenerateJsonDialogView(project, schemaPrefillProvider, onLayoutChanged)

    fun dispose() {
        view.dispose()
    }

    fun getComponent(): JComponent {
        return view.component
    }

    fun validate(): ValidationInfo? {
        val fieldValidationInfo = view.validate()
        if (fieldValidationInfo != null) {
            return fieldValidationInfo
        }

        if (view.getGenerationMode() != JsonGenerationMode.SCHEMA) {
            return null
        }

        val schemaText = view.getSchemaText().trim()
        if (schemaText.isBlank()) {
            return ValidationInfo(
                LocalizationBundle.message("validation.error.schema.required"),
                view.getSchemaInputComponent()
            )
        }

        if (schemaDataGenerationService == null) {
            return ValidationInfo(
                LocalizationBundle.message("validation.error.schema.service.unavailable"),
                view.getSchemaInputComponent()
            )
        }

        return try {
            schemaDataGenerationService.prepareSchema(schemaText)
            null
        } catch (generationException: JsonSchemaGenerationException) {
            ValidationInfo(
                generationException.message ?: LocalizationBundle.message("validation.error.schema.invalid"),
                view.getSchemaInputComponent()
            )
        } catch (exception: Exception) {
            ValidationInfo(
                LocalizationBundle.message("validation.error.schema.invalid"),
                view.getSchemaInputComponent()
            )
        }
    }

    fun getConfig(): JsonGenerationConfig {
        return view.getConfig()
    }
}
