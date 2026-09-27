package com.livteam.jsoninja.ui.dialog.generateJson.random

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationOutputFormat
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonRootType
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import kotlin.random.Random

class GenerateRandomJsonTabView(
    private val initialConfig: JsonGenerationConfig
) {
    private lateinit var rootTypeObject: JBRadioButton
    private lateinit var rootTypeArray: JBRadioButton
    private lateinit var propertyCountField: JBTextField
    private lateinit var arrayElementCountField: JBTextField
    private lateinit var maxDepthField: JBTextField
    private lateinit var structureSeedField: JBTextField
    private lateinit var valueSeedField: JBTextField
    private lateinit var keepSeedsCheckBox: JBCheckBox
    private lateinit var advancedOptionsRow: CollapsibleRow
    private var displayedRootType = initialConfig.jsonRootType
    private var savedObjectPropertyCountText = initialConfig.objectPropertyCount.toString()
    private var savedArrayElementCountText = initialConfig.arrayElementCount.toString()
    private var savedPropertiesPerObjectInArrayText = initialConfig.propertiesPerObjectInArray.toString()
    private var isUpdatingRootType = false
    private val outputFormatComboBox = ComboBox(JsonGenerationOutputFormat.entries.toTypedArray()).apply {
        selectedItem = if (initialConfig.isJson5) JsonGenerationOutputFormat.JSON5 else JsonGenerationOutputFormat.JSON
    }
    private lateinit var outputFormatCommentRow: Row
    private var onOptionsChangedCallback: (() -> Unit)? = null
    private var onAdvancedOptionsChangedCallback: (() -> Unit)? = null

    val component: DialogPanel by lazy { createComponent() }

    fun setOnOptionsChanged(callback: () -> Unit) {
        onOptionsChangedCallback = callback
    }

    fun setOnAdvancedOptionsChanged(callback: () -> Unit) {
        onAdvancedOptionsChangedCallback = callback
    }

    fun setAdvancedOptionsExpanded(isExpanded: Boolean) {
        advancedOptionsRow.expanded = isExpanded
    }

    fun registerValidators(parentDisposable: Disposable) {
        component.registerValidators(parentDisposable)
    }

    fun getJsonRootType(): JsonRootType {
        return if (rootTypeObject.isSelected) JsonRootType.OBJECT else JsonRootType.ARRAY_OF_OBJECTS
    }

    fun getObjectPropertyCountText(): String =
        if (isObjectRootTypeSelected()) propertyCountField.text else savedObjectPropertyCountText

    fun getArrayElementCountText(): String =
        if (isObjectRootTypeSelected()) savedArrayElementCountText else arrayElementCountField.text

    fun getPropertiesPerObjectInArrayText(): String =
        if (isObjectRootTypeSelected()) savedPropertiesPerObjectInArrayText else propertyCountField.text

    fun getMaxDepthText(): String = maxDepthField.text

    fun getStructureSeedText(): String = structureSeedField.text.trim()

    fun getValueSeedText(): String = valueSeedField.text.trim()

    fun getStructureSeedField(): JComponent = structureSeedField

    fun getValueSeedField(): JComponent = valueSeedField

    fun shouldKeepSeeds(): Boolean = keepSeedsCheckBox.isSelected

    fun isJson5Selected(): Boolean = outputFormatComboBox.selectedItem == JsonGenerationOutputFormat.JSON5

    fun getObjectPropertyCountField(): JComponent = propertyCountField

    fun getArrayElementCountField(): JComponent = arrayElementCountField

    fun getPropertiesPerObjectInArrayField(): JComponent = propertyCountField

    fun getMaxDepthField(): JComponent = maxDepthField

    fun getPreferredFocusedComponent(): JComponent =
        if (isObjectRootTypeSelected()) propertyCountField else arrayElementCountField

    fun isObjectRootTypeSelected(): Boolean = rootTypeObject.isSelected

    private fun createComponent(): DialogPanel {
        val result = panel {
            buttonsGroup {
                row(LocalizationBundle.message("dialog.generate.json.label.root.type")) {
                    rootTypeObject = radioButton(LocalizationBundle.message("dialog.generate.json.radio.object"))
                        .applyToComponent { isSelected = initialConfig.jsonRootType == JsonRootType.OBJECT }
                        .component
                    rootTypeArray = radioButton(LocalizationBundle.message("dialog.generate.json.radio.array"))
                        .applyToComponent { isSelected = initialConfig.jsonRootType == JsonRootType.ARRAY_OF_OBJECTS }
                        .component
                }
            }

            row(LocalizationBundle.message("dialog.generate.json.label.array.element.count")) {
                arrayElementCountField = intTextField(1..100, keyboardStep = 1)
                    .applyToComponent {
                        text = if (initialConfig.jsonRootType == JsonRootType.OBJECT) "1" else savedArrayElementCountText
                    }
                    .component
                label(LocalizationBundle.message("dialog.generate.json.range", 1, 100))
                    .applyToComponent { foreground = UIUtil.getContextHelpForeground() }
            }.enabledIf(rootTypeArray.selected)

            row(LocalizationBundle.message("dialog.generate.json.label.props.per.object")) {
                propertyCountField = intTextField(1..100, keyboardStep = 1)
                    .applyToComponent {
                        text = if (initialConfig.jsonRootType == JsonRootType.OBJECT) {
                            savedObjectPropertyCountText
                        } else {
                            savedPropertiesPerObjectInArrayText
                        }
                    }
                    .component
                label(LocalizationBundle.message("dialog.generate.json.range", 1, 100))
                    .applyToComponent { foreground = UIUtil.getContextHelpForeground() }
            }

            row(LocalizationBundle.message("dialog.generate.json.label.max.depth")) {
                maxDepthField = intTextField(1..10, keyboardStep = 1)
                    .applyToComponent { text = initialConfig.maxDepth.toString() }
                    .component
                label(LocalizationBundle.message("dialog.generate.json.range", 1, 10))
                    .applyToComponent { foreground = UIUtil.getContextHelpForeground() }
            }

            separator().topGap(TopGap.SMALL)
            row(LocalizationBundle.message("dialog.generate.json.output.format")) {
                cell(outputFormatComboBox)
            }
            outputFormatCommentRow = row {
                comment(LocalizationBundle.message("dialog.generate.json.output.json5.comment"))
            }.visible(isJson5Selected())

            advancedOptionsRow = collapsibleGroup(LocalizationBundle.message("dialog.generate.json.random.group")) {
                row {
                    keepSeedsCheckBox = checkBox(LocalizationBundle.message("dialog.generate.json.random.keep.seeds"))
                        .applyToComponent {
                            name = "keepRandomSeeds"
                            isSelected = initialConfig.shouldKeepRandomSeeds
                        }
                        .component
                }
                row { comment(LocalizationBundle.message("dialog.generate.json.random.seed.comment")) }
                row(LocalizationBundle.message("dialog.generate.json.random.structure.seed")) {
                    structureSeedField = textField().columns(21)
                        .applyToComponent {
                            name = "randomStructureSeed"
                            text = (initialConfig.randomStructureSeed ?: Random.nextLong()).toString()
                        }
                        .validationOnInput { validateSeed(it) }
                        .validationRequestor { revalidate -> keepSeedsCheckBox.addActionListener { revalidate() } }
                        .component
                    button(LocalizationBundle.message("dialog.generate.json.random.new.structure")) {
                        structureSeedField.text = Random.nextLong().toString()
                        valueSeedField.text = Random.nextLong().toString()
                    }
                }.enabledIf(keepSeedsCheckBox.selected)
                row(LocalizationBundle.message("dialog.generate.json.random.value.seed")) {
                    valueSeedField = textField().columns(21)
                        .applyToComponent {
                            name = "randomValueSeed"
                            text = (initialConfig.randomValueSeed ?: Random.nextLong()).toString()
                        }
                        .validationOnInput { validateSeed(it) }
                        .validationRequestor { revalidate -> keepSeedsCheckBox.addActionListener { revalidate() } }
                        .component
                    button(LocalizationBundle.message("dialog.generate.json.random.new.values")) {
                        valueSeedField.text = Random.nextLong().toString()
                    }
                }.enabledIf(keepSeedsCheckBox.selected)
                row { comment(LocalizationBundle.message("dialog.generate.json.random.structure.comment")) }
                row { comment(LocalizationBundle.message("dialog.generate.json.random.replay.comment")) }
            }.apply {
                expanded = initialConfig.shouldKeepRandomSeeds
                addExpandedListener { onAdvancedOptionsChangedCallback?.invoke() }
            }
        }.apply {
            border = JBUI.Borders.empty(16, 12, 12, 12)
        }

        rootTypeObject.addActionListener { updateRootTypeFields() }
        rootTypeArray.addActionListener { updateRootTypeFields() }
        keepSeedsCheckBox.addActionListener { onOptionsChangedCallback?.invoke() }
        outputFormatComboBox.addActionListener {
            outputFormatCommentRow.visible(isJson5Selected())
            onOptionsChangedCallback?.invoke()
        }
        val inputListener = object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                if (!isUpdatingRootType) {
                    onOptionsChangedCallback?.invoke()
                }
            }
        }
        listOf(propertyCountField, arrayElementCountField, maxDepthField, structureSeedField, valueSeedField)
            .forEach { it.document.addDocumentListener(inputListener) }
        return result
    }

    private fun validateSeed(field: JBTextField): ValidationInfo? =
        if (shouldKeepSeeds() && field.text.trim().toLongOrNull() == null) {
            ValidationInfo(LocalizationBundle.message("dialog.generate.json.random.seed.invalid"), field)
        } else {
            null
        }

    private fun updateRootTypeFields() {
        val selectedRootType = getJsonRootType()
        if (selectedRootType == displayedRootType) return

        if (displayedRootType == JsonRootType.OBJECT) {
            savedObjectPropertyCountText = propertyCountField.text
        } else {
            savedArrayElementCountText = arrayElementCountField.text
            savedPropertiesPerObjectInArrayText = propertyCountField.text
        }

        isUpdatingRootType = true
        try {
            displayedRootType = selectedRootType
            arrayElementCountField.text = if (selectedRootType == JsonRootType.OBJECT) "1" else savedArrayElementCountText
            propertyCountField.text = if (selectedRootType == JsonRootType.OBJECT) {
                savedObjectPropertyCountText
            } else {
                savedPropertiesPerObjectInArrayText
            }
        } finally {
            isUpdatingRootType = false
        }
        onOptionsChangedCallback?.invoke()
    }
}
