package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.ui.component.convertType.CodeInputPanel
import com.livteam.jsoninja.ui.component.convertType.CodePreviewPanel
import com.livteam.jsoninja.ui.component.convertType.LanguageSelectorComponent
import com.livteam.jsoninja.ui.dialog.convertType.model.TypeToJsonDialogConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class TypeToJsonDialogView(
    private val project: Project,
    initialConfig: TypeToJsonDialogConfig,
) {
    private val languageSelectorComponent = LanguageSelectorComponent(project)
    private val codeInputPanel = CodeInputPanel(
        project = project,
        initialLanguage = initialConfig.supportedLanguage,
        initialPlaceholderText = LocalizationBundle.message("dialog.type.to.json.input.placeholder"),
    )
    private val codePreviewPanel = CodePreviewPanel(project)

    private lateinit var propertyGenerationModeComboBox: ComboBox<SchemaPropertyGenerationMode>
    private lateinit var includesNullableFieldCheckBox: JBCheckBox
    private lateinit var usesRealisticSampleDataCheckBox: JBCheckBox
    private lateinit var outputCountTextField: JBTextField
    private lateinit var jsonFormatStateComboBox: ComboBox<JsonFormatState>

    val component: JComponent by lazy { createComponent(initialConfig) }

    init {
        codeInputPanel.setText(initialConfig.typeDeclarationText)
    }

    fun setOnLanguageChanged(callback: (SupportedLanguage) -> Unit) {
        languageSelectorComponent.setOnLanguageChanged(callback)
    }

    fun setOnTypeDeclarationChanged(callback: (String) -> Unit) {
        codeInputPanel.setOnTextChanged(callback)
    }

    fun setOnOptionsChanged(callback: () -> Unit) {
        propertyGenerationModeComboBox.addActionListener { callback() }
        includesNullableFieldCheckBox.addActionListener { callback() }
        usesRealisticSampleDataCheckBox.addActionListener { callback() }
        jsonFormatStateComboBox.addActionListener { callback() }
        outputCountTextField.document.addDocumentListener(SimpleSwingDocumentListener(callback))
    }

    fun getConfig(): TypeToJsonDialogConfig {
        return TypeToJsonDialogConfig(
            supportedLanguage = languageSelectorComponent.getSelectedLanguage(),
            typeDeclarationText = codeInputPanel.getText(),
            propertyGenerationMode = propertyGenerationModeComboBox.selectedItem as? SchemaPropertyGenerationMode
                ?: SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL,
            includesNullableFieldWithNullValue = includesNullableFieldCheckBox.isSelected,
            usesRealisticSampleData = usesRealisticSampleDataCheckBox.isSelected,
            outputCount = outputCountTextField.text.trim().toIntOrNull() ?: 1,
            jsonFormatState = jsonFormatStateComboBox.selectedItem as? JsonFormatState ?: JsonFormatState.PRETTIFY,
        )
    }

    fun setSelectedLanguage(language: SupportedLanguage) {
        languageSelectorComponent.setSelectedLanguage(language)
        codeInputPanel.setLanguage(language)
    }

    fun setInputPlaceholder(text: String) {
        codeInputPanel.setPlaceholder(text)
    }

    fun clearPreview() {
        codePreviewPanel.clear()
    }

    fun setPreviewLoading() {
        codePreviewPanel.setLoading()
    }

    fun setPreviewContent(text: String) {
        codePreviewPanel.setContent(text)
    }

    fun setPreviewError(message: String) {
        codePreviewPanel.setError(message)
    }

    fun getPreviewContent(): String {
        return codePreviewPanel.getContent()
    }

    fun getTypeDeclarationInputComponent(): JComponent {
        return codeInputPanel.component
    }

    fun getOutputCountInputComponent(): JComponent {
        return outputCountTextField
    }

    fun dispose() {
        languageSelectorComponent.dispose()
        codeInputPanel.dispose()
        codePreviewPanel.dispose()
    }

    private fun createComponent(initialConfig: TypeToJsonDialogConfig): JComponent {
        languageSelectorComponent.component
        codePreviewPanel.component
        setSelectedLanguage(initialConfig.supportedLanguage)
        setInputPlaceholder(LocalizationBundle.message("dialog.type.to.json.input.placeholder"))

        propertyGenerationModeComboBox = ComboBox(SchemaPropertyGenerationMode.entries.toTypedArray()).apply {
            renderer = SimpleListCellRenderer.create { label, value, _ ->
                label.text = when (value) {
                    SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL -> LocalizationBundle.message("dialog.type.to.json.fields.required.and.optional")
                    SchemaPropertyGenerationMode.REQUIRED_ONLY -> LocalizationBundle.message("dialog.type.to.json.fields.required.only")
                    SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED -> LocalizationBundle.message("dialog.type.to.json.fields.commented")
                    null -> ""
                }
            }
            selectedItem = initialConfig.propertyGenerationMode
        }

        includesNullableFieldCheckBox = JBCheckBox(
            LocalizationBundle.message("dialog.type.to.json.nullable"),
            initialConfig.includesNullableFieldWithNullValue,
        )
        usesRealisticSampleDataCheckBox = JBCheckBox(
            LocalizationBundle.message("dialog.type.to.json.faker"),
            initialConfig.usesRealisticSampleData,
        )
        outputCountTextField = JBTextField(initialConfig.outputCount.toString())
        jsonFormatStateComboBox = ComboBox(
            arrayOf(
                JsonFormatState.PRETTIFY,
                JsonFormatState.PRETTIFY_COMPACT,
                JsonFormatState.UGLIFY,
            )
        ).apply {
            renderer = SimpleListCellRenderer.create { label, value, _ ->
                label.text = when (value) {
                    JsonFormatState.PRETTIFY -> LocalizationBundle.message("dialog.type.to.json.format.prettify")
                    JsonFormatState.PRETTIFY_COMPACT -> LocalizationBundle.message("dialog.type.to.json.format.prettify.compact")
                    JsonFormatState.UGLIFY -> LocalizationBundle.message("dialog.type.to.json.format.uglify")
                    else -> ""
                }
            }
            selectedItem = initialConfig.jsonFormatState
        }

        val optionsPanel = panel {
            row(LocalizationBundle.message("dialog.type.to.json.language")) {
                cell(languageSelectorComponent.component)
            }
            row(LocalizationBundle.message("dialog.type.to.json.fields.mode")) {
                cell(propertyGenerationModeComboBox)
            }
            row {
                cell(includesNullableFieldCheckBox)
            }
            row {
                cell(usesRealisticSampleDataCheckBox)
            }
            row(LocalizationBundle.message("dialog.type.to.json.output.count")) {
                cell(outputCountTextField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
            row(LocalizationBundle.message("dialog.type.to.json.format")) {
                cell(jsonFormatStateComboBox)
            }
        }

        val inputSectionPanel = JPanel(BorderLayout()).apply {
            add(JLabel(LocalizationBundle.message("dialog.type.to.json.input.title")), BorderLayout.NORTH)
            add(codeInputPanel.component, BorderLayout.CENTER)
            border = JBUI.Borders.empty(0, 0, 8, 0)
        }

        val previewSectionPanel = JPanel(BorderLayout()).apply {
            add(codePreviewPanel.component, BorderLayout.CENTER)
            border = JBUI.Borders.empty(8, 0, 0, 0)
        }

        val splitter = JBSplitter(true, 0.46f).apply {
            firstComponent = inputSectionPanel
            secondComponent = previewSectionPanel
            minimumSize = JBUI.size(720, 420)
        }

        return JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(760, 640)
            add(optionsPanel, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        }
    }
}
