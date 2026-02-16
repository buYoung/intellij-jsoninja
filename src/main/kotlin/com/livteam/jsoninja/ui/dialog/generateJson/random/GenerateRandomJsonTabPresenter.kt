package com.livteam.jsoninja.ui.dialog.generateJson.random

import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import javax.swing.JComponent

class GenerateRandomJsonTabPresenter(
    onLayoutChanged: () -> Unit
) {
    private val initialConfig = JsonGenerationConfig()
    private val view = GenerateRandomJsonTabView(initialConfig, onLayoutChanged)

    fun getComponent(): JComponent {
        return view.component
    }

    fun validate(): ValidationInfo? {
        if (view.isObjectRootTypeSelected()) {
            val objectPropertyCount = view.getObjectPropertyCountText().toIntOrNull()
            if (objectPropertyCount == null || objectPropertyCount <= 0) {
                return ValidationInfo(
                    LocalizationBundle.message("validation.error.positive.integer.required"),
                    view.getObjectPropertyCountField()
                )
            }
        } else {
            val arrayElementCount = view.getArrayElementCountText().toIntOrNull()
            if (arrayElementCount == null || arrayElementCount <= 0) {
                return ValidationInfo(
                    LocalizationBundle.message("validation.error.positive.integer.required"),
                    view.getArrayElementCountField()
                )
            }

            val propertiesPerObjectInArrayCount = view.getPropertiesPerObjectInArrayText().toIntOrNull()
            if (propertiesPerObjectInArrayCount == null || propertiesPerObjectInArrayCount <= 0) {
                return ValidationInfo(
                    LocalizationBundle.message("validation.error.positive.integer.required"),
                    view.getPropertiesPerObjectInArrayField()
                )
            }
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
            maxDepth = view.getMaxDepthText().toIntOrNull() ?: initialConfig.maxDepth,
            isJson5 = view.isJson5Selected()
        )
    }

    fun dispose() = Unit
}
