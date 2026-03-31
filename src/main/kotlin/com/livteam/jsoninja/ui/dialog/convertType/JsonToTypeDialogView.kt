package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.NamingConvention
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.JsonToTypeAnnotationStyle
import com.livteam.jsoninja.ui.component.convertType.CodePreviewPanel
import com.livteam.jsoninja.ui.component.convertType.LanguageSelectorComponent
import com.livteam.jsoninja.ui.dialog.convertType.model.JsonToTypeDialogConfig
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

class JsonToTypeDialogView(
    project: Project,
    initialConfig: JsonToTypeDialogConfig,
) {
    private val languageSelectorComponent = LanguageSelectorComponent(project)
    private val codePreviewPanel = CodePreviewPanel(project)

    private lateinit var rootTypeNameTextField: JBTextField
    private lateinit var allowsNullableFieldsCheckBox: JBCheckBox
    private lateinit var namingConventionComboBox: ComboBox<NamingConvention>
    private lateinit var annotationStyleComboBox: ComboBox<JsonToTypeAnnotationStyle>

    val component: JComponent by lazy { createComponent(initialConfig) }

    fun setOnLanguageChanged(callback: (SupportedLanguage) -> Unit) {
        languageSelectorComponent.setOnLanguageChanged(callback)
    }

    fun setOnOptionsChanged(callback: () -> Unit) {
        component
        rootTypeNameTextField.document.addDocumentListener(SimpleSwingDocumentListener(callback))
        allowsNullableFieldsCheckBox.addActionListener { callback() }
        namingConventionComboBox.addActionListener { callback() }
        annotationStyleComboBox.addActionListener { callback() }
    }

    fun getConfig(): JsonToTypeDialogConfig {
        return JsonToTypeDialogConfig(
            supportedLanguage = languageSelectorComponent.getSelectedLanguage(),
            rootTypeName = rootTypeNameTextField.text.trim().ifBlank {
                LocalizationBundle.message("dialog.json.to.type.root.name.placeholder")
            },
            allowsNullableFields = allowsNullableFieldsCheckBox.isSelected,
            namingConvention = namingConventionComboBox.selectedItem as? NamingConvention
                ?: NamingConvention.CAMEL_CASE,
            annotationStyle = annotationStyleComboBox.selectedItem as? JsonToTypeAnnotationStyle
                ?: JsonToTypeAnnotationStyle.NONE,
        )
    }

    fun setSelectedLanguage(language: SupportedLanguage) {
        languageSelectorComponent.setSelectedLanguage(language)
    }

    fun setNamingConvention(namingConvention: NamingConvention) {
        namingConventionComboBox.selectedItem = namingConvention
    }

    fun updateAnnotationStyleOptions(
        language: SupportedLanguage,
        selectedAnnotationStyle: JsonToTypeAnnotationStyle,
    ) {
        val availableAnnotationStyles = JsonToTypeAnnotationStyle.availableValues(language)
        annotationStyleComboBox.model = DefaultComboBoxModel(availableAnnotationStyles.toTypedArray())
        annotationStyleComboBox.selectedItem = availableAnnotationStyles.firstOrNull { annotationStyle ->
            annotationStyle == selectedAnnotationStyle
        } ?: availableAnnotationStyles.firstOrNull()
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

    fun getRootNameInputComponent(): JComponent {
        return rootTypeNameTextField
    }

    fun dispose() {
        languageSelectorComponent.dispose()
        codePreviewPanel.dispose()
    }

    private fun createComponent(initialConfig: JsonToTypeDialogConfig): JComponent {
        languageSelectorComponent.component
        codePreviewPanel.component

        rootTypeNameTextField = JBTextField(initialConfig.rootTypeName).apply {
            emptyText.text = LocalizationBundle.message("dialog.json.to.type.root.name.placeholder")
        }
        allowsNullableFieldsCheckBox = JBCheckBox(
            LocalizationBundle.message("dialog.json.to.type.nullable"),
            initialConfig.allowsNullableFields,
        )
        namingConventionComboBox = ComboBox(NamingConvention.entries.toTypedArray()).apply {
            renderer = SimpleListCellRenderer.create { label, value, _ ->
                label.text = value?.displayName ?: ""
            }
            selectedItem = initialConfig.namingConvention
        }
        annotationStyleComboBox = ComboBox<JsonToTypeAnnotationStyle>().apply {
            renderer = SimpleListCellRenderer.create { label, value, _ ->
                label.text = value?.displayName ?: ""
            }
        }

        setSelectedLanguage(initialConfig.supportedLanguage)
        updateAnnotationStyleOptions(initialConfig.supportedLanguage, initialConfig.annotationStyle)

        val optionsPanel = panel {
            row(LocalizationBundle.message("dialog.json.to.type.language")) {
                cell(languageSelectorComponent.component)
            }
            row(LocalizationBundle.message("dialog.json.to.type.root.name")) {
                cell(rootTypeNameTextField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
            row {
                cell(allowsNullableFieldsCheckBox)
            }
            row(LocalizationBundle.message("dialog.json.to.type.naming")) {
                cell(namingConventionComboBox)
            }
            row(LocalizationBundle.message("dialog.json.to.type.annotation")) {
                cell(annotationStyleComboBox)
            }
        }

        return JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(760, 560)
            add(optionsPanel, BorderLayout.NORTH)
            add(codePreviewPanel.component, BorderLayout.CENTER)
            border = JBUI.Borders.empty(0, 0, 0, 0)
        }
    }
}
