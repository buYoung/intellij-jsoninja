package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonRootType
import javax.swing.JComponent

class GenerateJsonDialogView(private val onLayoutChanged: () -> Unit) {

    private lateinit var rootTypeObject: JBRadioButton
    private lateinit var rootTypeArray: JBRadioButton
    private lateinit var objectPropertyCountField: JBTextField
    private lateinit var arrayElementCountField: JBTextField
    private lateinit var propertiesPerObjectInArrayField: JBTextField
    private lateinit var maxDepthField: JBTextField
    private lateinit var json5Checkbox: JBCheckBox

    private val initialConfig = JsonGenerationConfig() // 기본값으로 시작

    val component: JComponent by lazy { createComponent() }

    fun validate(): ValidationInfo? {
        if (rootTypeObject.isSelected) {
            if (objectPropertyCountField.text.toIntOrNull() == null || objectPropertyCountField.text.toInt() <= 0) {
                return ValidationInfo(
                    LocalizationBundle.message("validation.error.positive.integer.required"),
                    objectPropertyCountField
                )
            }
        } else { // 배열 선택됨
            if (arrayElementCountField.text.toIntOrNull() == null || arrayElementCountField.text.toInt() <= 0) {
                return ValidationInfo(
                    LocalizationBundle.message("validation.error.positive.integer.required"),
                    arrayElementCountField
                )
            }
            if (propertiesPerObjectInArrayField.text.toIntOrNull() == null || propertiesPerObjectInArrayField.text.toInt() <= 0) {
                return ValidationInfo(
                    LocalizationBundle.message("validation.error.positive.integer.required"),
                    propertiesPerObjectInArrayField
                )
            }
        }
        return null
    }

    fun getConfig(): JsonGenerationConfig {
        return JsonGenerationConfig(
            jsonRootType = if (rootTypeObject.isSelected) JsonRootType.OBJECT else JsonRootType.ARRAY_OF_OBJECTS,
            objectPropertyCount = objectPropertyCountField.text.toIntOrNull() ?: initialConfig.objectPropertyCount,
            arrayElementCount = arrayElementCountField.text.toIntOrNull() ?: initialConfig.arrayElementCount,
            propertiesPerObjectInArray = propertiesPerObjectInArrayField.text.toIntOrNull()
                ?: initialConfig.propertiesPerObjectInArray,
            maxDepth = maxDepthField.text.toIntOrNull() ?: initialConfig.maxDepth,
            isJson5 = json5Checkbox.isSelected
        )
    }

    private fun createComponent(): JComponent {
        return panel {
            group(LocalizationBundle.message("dialog.generate.json.group.structure")) {
                buttonsGroup {
                    row {
                        rootTypeObject = radioButton(
                            LocalizationBundle.message("dialog.generate.json.radio.object"),
                            JsonRootType.OBJECT
                        )
                            .component
                        rootTypeArray = radioButton(
                            LocalizationBundle.message("dialog.generate.json.radio.array"),
                            JsonRootType.ARRAY_OF_OBJECTS
                        )
                            .component
                    }
                }.bind(
                    { if (rootTypeObject.isSelected) JsonRootType.OBJECT else JsonRootType.ARRAY_OF_OBJECTS },
                    { selectedType ->
                        rootTypeObject.isSelected = (selectedType == JsonRootType.OBJECT)
                        rootTypeArray.isSelected = (selectedType == JsonRootType.ARRAY_OF_OBJECTS)
                        updateFieldVisibility()
                    }
                )
            }

            group(LocalizationBundle.message("dialog.generate.json.group.dimensions")) {
                row(LocalizationBundle.message("dialog.generate.json.label.object.prop.count")) {
                    objectPropertyCountField = intTextField(1..100)
                        .apply {
                            component.text = initialConfig.objectPropertyCount.toString()
                        }
                        .gap(RightGap.SMALL)
                        .comment(LocalizationBundle.message("dialog.generate.json.comment.object.prop.count"))
                        .component
                }.visibleIf(rootTypeObject.selected)

                row(LocalizationBundle.message("dialog.generate.json.label.array.element.count")) {
                    arrayElementCountField = intTextField(1..100)
                        .apply {
                            component.text = initialConfig.arrayElementCount.toString()
                        }
                        .gap(RightGap.SMALL)
                        .comment(LocalizationBundle.message("dialog.generate.json.comment.array.element.count"))
                        .component
                }.visibleIf(rootTypeArray.selected)

                row(LocalizationBundle.message("dialog.generate.json.label.props.per.object")) {
                    propertiesPerObjectInArrayField = intTextField(1..100) // 범위 예시
                        .apply {
                            component.text = initialConfig.propertiesPerObjectInArray.toString()
                        }
                        .gap(RightGap.SMALL)
                        .comment(LocalizationBundle.message("dialog.generate.json.comment.props.per.object"))
                        .component
                }.visibleIf(rootTypeArray.selected)

                separator()

                row(LocalizationBundle.message("dialog.generate.json.label.max.depth")) { // "최대 생성 깊이 (Max Depth):"
                    maxDepthField = intTextField(1..10) // 범위 예시: 1부터 10까지
                        .apply { component.text = initialConfig.maxDepth.toString() } // 초기값 설정
                        .gap(RightGap.SMALL)
                        .comment(LocalizationBundle.message("dialog.generate.json.comment.max.depth")) // "중첩 레벨 제한 (1 이상)"
                        .component
                }
            }

            group(LocalizationBundle.message("dialog.generate.json.group.options")) {
                row {
                    json5Checkbox = checkBox(LocalizationBundle.message("dialog.generate.json.checkbox.json5"))
                        .apply { component.isSelected = initialConfig.isJson5 }
                        .component
                }
            }

            updateFieldVisibility()
        }
    }

    private fun updateFieldVisibility() {
        // DSL의 `visibleIf` 바인딩에 의존하지만, 나중에 복잡한 상호 의존성이 생기면
        // 수동 호출이 필요할 수 있습니다. 현재는 DSL이 처리합니다.
        // 이 함수는 향후 더 복잡한 로직에 유용할 수 있습니다.
        onLayoutChanged() // 가시성 변경 시 다이얼로그 크기 조정
    }
}
