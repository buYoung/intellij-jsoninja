package com.livteam.jsoninja.ui.dialog.generateJson.random

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonRootType
import javax.swing.JComponent

class GenerateRandomJsonTabView(
    private val initialConfig: JsonGenerationConfig,
    private val onLayoutChanged: () -> Unit
) {
    private lateinit var rootTypeObject: JBRadioButton
    private lateinit var rootTypeArray: JBRadioButton
    private lateinit var objectPropertyCountField: JBTextField
    private lateinit var arrayElementCountField: JBTextField
    private lateinit var propertiesPerObjectInArrayField: JBTextField
    private lateinit var maxDepthField: JBTextField
    private lateinit var randomJson5Checkbox: JBCheckBox

    val component: JComponent by lazy { createComponent() }

    fun getJsonRootType(): JsonRootType {
        return if (rootTypeObject.isSelected) JsonRootType.OBJECT else JsonRootType.ARRAY_OF_OBJECTS
    }

    fun getObjectPropertyCountText(): String = objectPropertyCountField.text

    fun getArrayElementCountText(): String = arrayElementCountField.text

    fun getPropertiesPerObjectInArrayText(): String = propertiesPerObjectInArrayField.text

    fun getMaxDepthText(): String = maxDepthField.text

    fun isJson5Selected(): Boolean = randomJson5Checkbox.isSelected

    fun getObjectPropertyCountField(): JComponent = objectPropertyCountField

    fun getArrayElementCountField(): JComponent = arrayElementCountField

    fun getPropertiesPerObjectInArrayField(): JComponent = propertiesPerObjectInArrayField

    fun isObjectRootTypeSelected(): Boolean = rootTypeObject.isSelected

    private fun createComponent(): JComponent {
        return panel {
            group(LocalizationBundle.message("dialog.generate.json.group.structure")) {
                buttonsGroup {
                    row {
                        rootTypeObject = radioButton(
                            LocalizationBundle.message("dialog.generate.json.radio.object"),
                            JsonRootType.OBJECT
                        ).component
                        rootTypeArray = radioButton(
                            LocalizationBundle.message("dialog.generate.json.radio.array"),
                            JsonRootType.ARRAY_OF_OBJECTS
                        ).component
                    }
                }.bind(
                    { if (rootTypeObject.isSelected) JsonRootType.OBJECT else JsonRootType.ARRAY_OF_OBJECTS },
                    { selectedType ->
                        rootTypeObject.isSelected = selectedType == JsonRootType.OBJECT
                        rootTypeArray.isSelected = selectedType == JsonRootType.ARRAY_OF_OBJECTS
                        onLayoutChanged()
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
                    propertiesPerObjectInArrayField = intTextField(1..100)
                        .apply {
                            component.text = initialConfig.propertiesPerObjectInArray.toString()
                        }
                        .gap(RightGap.SMALL)
                        .comment(LocalizationBundle.message("dialog.generate.json.comment.props.per.object"))
                        .component
                }.visibleIf(rootTypeArray.selected)

                row(LocalizationBundle.message("dialog.generate.json.label.max.depth")) {
                    maxDepthField = intTextField(1..10)
                        .apply { component.text = initialConfig.maxDepth.toString() }
                        .gap(RightGap.SMALL)
                        .comment(LocalizationBundle.message("dialog.generate.json.comment.max.depth"))
                        .component
                }
            }

            group(LocalizationBundle.message("dialog.generate.json.group.options")) {
                row {
                    randomJson5Checkbox = checkBox(LocalizationBundle.message("dialog.generate.json.checkbox.json5"))
                        .apply { component.isSelected = initialConfig.isJson5 }
                        .component
                }
            }
        }
    }
}
