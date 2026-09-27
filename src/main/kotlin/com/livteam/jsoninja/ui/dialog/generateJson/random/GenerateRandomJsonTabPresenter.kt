package com.livteam.jsoninja.ui.dialog.generateJson.random

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import javax.swing.JComponent

class GenerateRandomJsonTabPresenter {
    private val initialConfig = JsonGenerationConfig()
    private val view = GenerateRandomJsonTabView(initialConfig)

    fun getComponent(): JComponent {
        return view.component
    }

    fun setOnOptionsChanged(callback: () -> Unit) = view.setOnOptionsChanged(callback)

    fun registerValidators(parentDisposable: Disposable) = view.registerValidators(parentDisposable)

    fun getPreferredFocusedComponent(): JComponent = view.getPreferredFocusedComponent()

    fun getSummary(): String {
        if (validate() != null) {
            return LocalizationBundle.message("dialog.generate.json.summary.invalid")
        }
        val outputFormat = if (view.isJson5Selected()) "JSON5" else "JSON"
        return if (view.isObjectRootTypeSelected()) {
            LocalizationBundle.message(
                "dialog.generate.json.summary.object", view.getObjectPropertyCountText().toInt(), outputFormat
            )
        } else {
            LocalizationBundle.message(
                "dialog.generate.json.summary.array",
                view.getArrayElementCountText().toInt(),
                view.getPropertiesPerObjectInArrayText().toInt(),
                outputFormat
            )
        }
    }

    fun validate(): ValidationInfo? {
        if (view.isObjectRootTypeSelected()) {
            val objectPropertyCount = view.getObjectPropertyCountText().toIntOrNull()
            if (objectPropertyCount == null || objectPropertyCount !in 1..100) {
                return ValidationInfo(
                    LocalizationBundle.message("dialog.generate.json.validation.integer.range", 1, 100),
                    view.getObjectPropertyCountField()
                )
            }
        } else {
            val arrayElementCount = view.getArrayElementCountText().toIntOrNull()
            if (arrayElementCount == null || arrayElementCount !in 1..100) {
                return ValidationInfo(
                    LocalizationBundle.message("dialog.generate.json.validation.integer.range", 1, 100),
                    view.getArrayElementCountField()
                )
            }

            val propertiesPerObjectInArrayCount = view.getPropertiesPerObjectInArrayText().toIntOrNull()
            if (propertiesPerObjectInArrayCount == null || propertiesPerObjectInArrayCount !in 1..100) {
                return ValidationInfo(
                    LocalizationBundle.message("dialog.generate.json.validation.integer.range", 1, 100),
                    view.getPropertiesPerObjectInArrayField()
                )
            }
        }

        val maxDepth = view.getMaxDepthText().toIntOrNull()
        if (maxDepth == null || maxDepth !in 1..10) {
            return ValidationInfo(
                LocalizationBundle.message("dialog.generate.json.validation.integer.range", 1, 10),
                view.getMaxDepthField()
            )
        }

        return null
    }

    fun getConfig(): JsonGenerationConfig {
        return JsonGenerationConfig(
            generationMode = JsonGenerationMode.RANDOM,
            jsonRootType = view.getJsonRootType(),
            objectPropertyCount = view.getObjectPropertyCountText().toIntOrNull() ?: initialConfig.objectPropertyCount,
            arrayElementCount = view.getArrayElementCountText().toIntOrNull() ?: initialConfig.arrayElementCount,
            propertiesPerObjectInArray = view.getPropertiesPerObjectInArrayText().toIntOrNull()
                ?: initialConfig.propertiesPerObjectInArray,
            maxDepth = view.getMaxDepthText().toInt(),
            isJson5 = view.isJson5Selected()
        )
    }

    fun dispose() = Unit
}
