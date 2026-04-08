package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.ui.component.convertType.CodeInputPanel
import com.livteam.jsoninja.ui.component.convertType.CodePreviewPanel
import com.livteam.jsoninja.ui.component.convertType.LanguageSelectorComponent
import com.livteam.jsoninja.ui.dialog.convertType.model.TypeToJsonDialogConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.SpinnerNumberModel
import com.intellij.ui.dsl.builder.panel

class TypeToJsonDialogView(
    project: com.intellij.openapi.project.Project,
) {
    private val languageSelector = LanguageSelectorComponent()
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
    private val previewPanel = CodePreviewPanel(project)
    private val rootPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private var onStateChanged: (() -> Unit)? = null

    val component: JComponent
        get() = rootPanel

    init {
        val controlsPanel = panel {
            row {
                label(LocalizationBundle.message("dialog.type.to.json.language"))
                cell(languageSelector)
                label(LocalizationBundle.message("dialog.type.to.json.fields.mode")).gap(com.intellij.ui.dsl.builder.RightGap.SMALL)
                cell(fieldsModeComboBox)
                cell(nullableCheckBox)
            }
            row {
                label(LocalizationBundle.message("dialog.type.to.json.output.count"))
                cell(outputCountSpinner)
                label(LocalizationBundle.message("dialog.type.to.json.format")).gap(com.intellij.ui.dsl.builder.RightGap.SMALL)
                cell(formatStateComboBox)
                cell(realisticDataCheckBox)
            }
        }.apply {
            border = com.intellij.util.ui.JBUI.Borders.empty(4, 8)
        }
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, previewPanel).apply {
            resizeWeight = 0.46
        }
        rootPanel.add(controlsPanel, BorderLayout.NORTH)
        rootPanel.add(splitPane, BorderLayout.CENTER)

        languageSelector.setOnLanguageChanged {
            updateInputLanguage(it)
            onStateChanged?.invoke()
        }
        fieldsModeComboBox.addActionListener { onStateChanged?.invoke() }
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
        previewPanel.setOnCopyRequested(callback)
    }

    fun showEmptyPreview() {
        previewPanel.setEmpty()
    }

    fun showLoadingPreview() {
        previewPanel.setLoading()
    }

    fun showErrorPreview(message: String) {
        previewPanel.setError(message)
    }

    fun showSuccessPreview(text: String) {
        previewPanel.setSuccess(text, "json")
    }

    fun getValidationComponent(): JComponent = outputCountSpinner

    fun dispose() {
        inputPanel.dispose()
        previewPanel.dispose()
    }

    private fun updateInputLanguage(language: SupportedLanguage) {
        val placeholderText = when (language) {
            SupportedLanguage.JAVA -> LocalizationBundle.message("dialog.type.to.json.input.placeholder.java")
            SupportedLanguage.KOTLIN -> LocalizationBundle.message("dialog.type.to.json.input.placeholder.kotlin")
            SupportedLanguage.TYPESCRIPT -> LocalizationBundle.message("dialog.type.to.json.input.placeholder.typescript")
            SupportedLanguage.GO -> LocalizationBundle.message("dialog.type.to.json.input.placeholder.go")
        }
        inputPanel.updateLanguage(language.fileExtension, placeholderText)
    }
}
