package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.ui.component.convertType.CodeInputPanel
import com.livteam.jsoninja.ui.component.convertType.ConvertTypeWorkspacePanel
import com.livteam.jsoninja.ui.component.convertType.LanguageSelectorComponent
import com.livteam.jsoninja.ui.dialog.convertType.model.TypeToJsonDialogConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import com.intellij.ui.dsl.builder.panel

class TypeToJsonDialogView(
    project: com.intellij.openapi.project.Project,
) {
    private val languageSelector = LanguageSelectorComponent(project)
    private val fieldsModeComboBox = ComboBox(SchemaPropertyGenerationMode.entries.toTypedArray())
    private val nullableCheckBox = JBCheckBox(LocalizationBundle.message("dialog.type.to.json.nullable"))
    private val realisticDataCheckBox = JBCheckBox(LocalizationBundle.message("dialog.type.to.json.faker"))
    private val outputCountSpinner = JSpinner(SpinnerNumberModel(1, 1, 100, 1))
    private val formatStateComboBox = ComboBox(arrayOf(
        JsonFormatState.PRETTIFY,
        JsonFormatState.PRETTIFY_COMPACT,
        JsonFormatState.UGLIFY,
    ))
    private val inputPanel = CodeInputPanel(project)
    private val workspace: ConvertTypeWorkspacePanel
    private var onStateChanged: (() -> Unit)? = null

    val component: JComponent
        get() = workspace

    init {
        languageSelector.setAccessibleName(LocalizationBundle.message("dialog.type.to.json.language"))
        fieldsModeComboBox.renderer = SimpleListCellRenderer.create("") { mode ->
            LocalizationBundle.message(when (mode) {
                SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL -> "dialog.type.to.json.fields.required.and.optional"
                SchemaPropertyGenerationMode.REQUIRED_ONLY -> "dialog.type.to.json.fields.required.only"
                SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED -> "dialog.type.to.json.fields.commented"
            })
        }
        formatStateComboBox.renderer = SimpleListCellRenderer.create("") { format ->
            LocalizationBundle.message(when (format) {
                JsonFormatState.PRETTIFY, JsonFormatState.PRETTIFY_SORTED -> "dialog.type.to.json.format.prettify"
                JsonFormatState.PRETTIFY_COMPACT -> "dialog.type.to.json.format.prettify.compact"
                JsonFormatState.UGLIFY -> "dialog.type.to.json.format.uglify"
            })
        }
        val settingsPanel = panel {
            row {
                cell(languageSelector)
                    .label(LocalizationBundle.message("dialog.type.to.json.language"), LabelPosition.TOP)
                    .align(AlignX.FILL).resizableColumn()
            }
            row {
                cell(fieldsModeComboBox)
                    .label(LocalizationBundle.message("dialog.type.to.json.fields.mode"), LabelPosition.TOP)
                    .align(AlignX.FILL).resizableColumn()
            }
            row {
                cell(nullableCheckBox)
            }
            row {
                cell(outputCountSpinner)
                    .label(LocalizationBundle.message("dialog.type.to.json.output.count"), LabelPosition.TOP)
            }
            row {
                cell(formatStateComboBox)
                    .label(LocalizationBundle.message("dialog.type.to.json.format"), LabelPosition.TOP)
                    .align(AlignX.FILL).resizableColumn()
            }
            row {
                cell(realisticDataCheckBox)
            }
        }
        workspace = ConvertTypeWorkspacePanel(
            project,
            inputPanel,
            settingsPanel,
            LocalizationBundle.message("common.convert.input.type"),
        )
        updatePreviewLanguage()

        languageSelector.setOnLanguageChanged {
            updateInputLanguage(it)
            onStateChanged?.invoke()
        }
        fieldsModeComboBox.addActionListener {
            updatePreviewLanguage()
            onStateChanged?.invoke()
        }
        nullableCheckBox.addActionListener { onStateChanged?.invoke() }
        realisticDataCheckBox.addActionListener { onStateChanged?.invoke() }
        outputCountSpinner.addChangeListener { onStateChanged?.invoke() }
        formatStateComboBox.addActionListener { onStateChanged?.invoke() }
        inputPanel.setOnTextChanged { onStateChanged?.invoke() }
        updateInputLanguage(SupportedLanguage.KOTLIN)
    }

    fun applyConfig(config: TypeToJsonDialogConfig) {
        languageSelector.setSelectedLanguage(config.language)
        fieldsModeComboBox.selectedItem = config.propertyGenerationMode
        nullableCheckBox.isSelected = config.includesNullableFieldWithNullValue
        realisticDataCheckBox.isSelected = config.usesRealisticSampleData
        outputCountSpinner.value = config.outputCount
        formatStateComboBox.selectedItem = config.formatState
        updateInputLanguage(config.language)
    }

    fun collectConfig(): TypeToJsonDialogConfig {
        return TypeToJsonDialogConfig(
            language = languageSelector.getSelectedLanguage() ?: SupportedLanguage.KOTLIN,
            propertyGenerationMode = fieldsModeComboBox.selectedItem as? SchemaPropertyGenerationMode
                ?: SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL,
            includesNullableFieldWithNullValue = nullableCheckBox.isSelected,
            usesRealisticSampleData = realisticDataCheckBox.isSelected,
            outputCount = (outputCountSpinner.value as? Int ?: 1).coerceIn(1, 100),
            formatState = formatStateComboBox.selectedItem as? JsonFormatState ?: JsonFormatState.PRETTIFY,
        )
    }

    fun setInputText(text: String) {
        inputPanel.setText(text)
    }

    fun getInputText(): String = inputPanel.getText()

    fun setOnStateChanged(callback: () -> Unit) {
        onStateChanged = callback
    }

    fun setOnCopyRequested(callback: () -> Unit) {
        workspace.setOnCopyRequested(callback)
    }

    fun showEmptyPreview() {
        workspace.showEmptyPreview()
    }

    fun showLoadingPreview() {
        workspace.showLoadingPreview()
    }

    fun showErrorPreview(message: String) {
        workspace.showErrorPreview(message)
    }

    fun showSuccessPreview(text: String) {
        showSuccessPreview(text, "json")
    }

    fun showSuccessPreview(text: String, fileExtension: String) {
        workspace.showSuccessPreview(text, fileExtension)
    }

    fun showSuccessPreview(text: String, fileExtension: String, warnings: List<String>) {
        workspace.showSuccessPreview(text, fileExtension, warnings)
    }

    fun clearPreviewWarnings() = workspace.clearPreviewWarnings()

    fun getValidationComponent(): JComponent = outputCountSpinner

    fun getPreferredFocusedComponent(): JComponent = inputPanel.getPreferredFocusedComponent()

    fun dispose() {
        inputPanel.dispose()
        workspace.dispose()
    }

    private fun updateInputLanguage(language: SupportedLanguage) {
        val placeholderText = LocalizationBundle.message(language.inputPlaceholderKey)
        inputPanel.updateLanguage(language.fileExtension, placeholderText)
        workspace.setInputLanguage(language.getDisplayName())
    }

    private fun updatePreviewLanguage() {
        workspace.setPreviewLanguage(
            if (fieldsModeComboBox.selectedItem == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED) "JSON5" else "JSON"
        )
    }
}
