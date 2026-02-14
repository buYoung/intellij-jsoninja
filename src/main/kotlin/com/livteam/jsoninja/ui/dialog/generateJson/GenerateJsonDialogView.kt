package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.json.JsonFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import com.livteam.jsoninja.ui.component.editor.SimpleJsonDocumentCreator
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonRootType
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout

class GenerateJsonDialogView(
    private val project: Project?,
    private val schemaPrefillProvider: (() -> String?)?,
    private val onLayoutChanged: () -> Unit
) {
    private lateinit var rootTypeObject: JBRadioButton
    private lateinit var rootTypeArray: JBRadioButton
    private lateinit var objectPropertyCountField: JBTextField
    private lateinit var arrayElementCountField: JBTextField
    private lateinit var propertiesPerObjectInArrayField: JBTextField
    private lateinit var maxDepthField: JBTextField
    private lateinit var randomJson5Checkbox: JBCheckBox

    private lateinit var schemaEditor: EditorTextField
    private lateinit var schemaOutputCountField: JBTextField
    private lateinit var schemaJson5Checkbox: JBCheckBox
    private lateinit var loadCurrentEditorButton: JButton
    private lateinit var tabbedPane: JBTabbedPane

    private val initialConfig = JsonGenerationConfig()

    val component: JComponent by lazy { createComponent() }

    fun validate(): ValidationInfo? {
        return if (getGenerationMode() == JsonGenerationMode.RANDOM) {
            validateRandomTab()
        } else {
            validateSchemaTab()
        }
    }

    fun getGenerationMode(): JsonGenerationMode {
        return if (tabbedPane.selectedIndex == 1) {
            JsonGenerationMode.SCHEMA
        } else {
            JsonGenerationMode.RANDOM
        }
    }

    fun getSchemaText(): String {
        return schemaEditor.text
    }

    fun getSchemaInputComponent(): JComponent {
        return schemaEditor
    }

    fun getConfig(): JsonGenerationConfig {
        val generationMode = getGenerationMode()
        return JsonGenerationConfig(
            generationMode = generationMode,
            jsonRootType = if (rootTypeObject.isSelected) JsonRootType.OBJECT else JsonRootType.ARRAY_OF_OBJECTS,
            objectPropertyCount = objectPropertyCountField.text.toIntOrNull() ?: initialConfig.objectPropertyCount,
            arrayElementCount = arrayElementCountField.text.toIntOrNull() ?: initialConfig.arrayElementCount,
            propertiesPerObjectInArray = propertiesPerObjectInArrayField.text.toIntOrNull()
                ?: initialConfig.propertiesPerObjectInArray,
            maxDepth = maxDepthField.text.toIntOrNull() ?: initialConfig.maxDepth,
            isJson5 = if (generationMode == JsonGenerationMode.RANDOM) {
                randomJson5Checkbox.isSelected
            } else {
                schemaJson5Checkbox.isSelected
            },
            schemaText = schemaEditor.text,
            schemaOutputCount = schemaOutputCountField.text.toIntOrNull() ?: initialConfig.schemaOutputCount
        )
    }

    fun dispose() {
        (schemaEditor as? Disposable)?.let { disposableEditor ->
            com.intellij.openapi.util.Disposer.dispose(disposableEditor)
        }
    }

    private fun createComponent(): JComponent {
        tabbedPane = JBTabbedPane()
        tabbedPane.addTab(LocalizationBundle.message("dialog.generate.json.tab.random"), createRandomPanel())
        tabbedPane.addTab(LocalizationBundle.message("dialog.generate.json.tab.schema"), createSchemaPanel())
        tabbedPane.addChangeListener {
            onLayoutChanged()
        }

        return JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(640, 500)
            add(tabbedPane, BorderLayout.CENTER)
        }
    }

    private fun createRandomPanel(): JComponent {
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

    private fun createSchemaPanel(): JComponent {
        val schemaPanel = JPanel(BorderLayout())
        val optionsPanel = panel {
            group(LocalizationBundle.message("dialog.generate.json.schema.group.input")) {
                row {
                    loadCurrentEditorButton = button(LocalizationBundle.message("dialog.generate.json.schema.load.current")) {
                        loadSchemaFromCurrentEditor()
                    }.component
                }

                row(LocalizationBundle.message("dialog.generate.json.schema.output.count")) {
                    schemaOutputCountField = intTextField(1..100)
                        .apply { component.text = initialConfig.schemaOutputCount.toString() }
                        .gap(RightGap.SMALL)
                        .comment(LocalizationBundle.message("dialog.generate.json.schema.output.count.comment"))
                        .component
                }

                row {
                    schemaJson5Checkbox = checkBox(LocalizationBundle.message("dialog.generate.json.checkbox.json5"))
                        .apply { component.isSelected = initialConfig.isJson5 }
                        .component
                }
            }
        }

        schemaEditor = createSchemaEditor()
        schemaPanel.add(optionsPanel, BorderLayout.NORTH)
        schemaPanel.add(schemaEditor, BorderLayout.CENTER)
        return schemaPanel
    }

    private fun createSchemaEditor(): EditorTextField {
        val initialSchemaText = LocalizationBundle.message("dialog.generate.json.schema.placeholder")
        val document = JsonDocumentFactory.createJsonDocument(
            initialSchemaText,
            project,
            SimpleJsonDocumentCreator(),
            "json"
        )
        return EditorTextField(document, project, JsonFileType.INSTANCE, false, false).apply {
            preferredSize = JBUI.size(620, 320)
            putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)
        }
    }

    private fun loadSchemaFromCurrentEditor() {
        val schemaPrefillText = schemaPrefillProvider?.invoke()?.trim().orEmpty()
        if (schemaPrefillText.isBlank()) return

        if (project == null) {
            schemaEditor.text = schemaPrefillText
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            schemaEditor.text = schemaPrefillText
        }
    }

    private fun validateRandomTab(): ValidationInfo? {
        if (rootTypeObject.isSelected) {
            if (objectPropertyCountField.text.toIntOrNull() == null || objectPropertyCountField.text.toInt() <= 0) {
                return ValidationInfo(
                    LocalizationBundle.message("validation.error.positive.integer.required"),
                    objectPropertyCountField
                )
            }
        } else {
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

    private fun validateSchemaTab(): ValidationInfo? {
        if (schemaOutputCountField.text.toIntOrNull() == null || schemaOutputCountField.text.toInt() <= 0) {
            return ValidationInfo(
                LocalizationBundle.message("validation.error.positive.integer.required.ge1"),
                schemaOutputCountField
            )
        }

        if (schemaEditor.text.trim().isBlank()) {
            return ValidationInfo(
                LocalizationBundle.message("validation.error.schema.required"),
                schemaEditor
            )
        }

        return null
    }
}
