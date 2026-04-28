package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.JsonToTypeAnnotationStyle
import com.livteam.jsoninja.services.typeConversion.NamingConvention
import com.livteam.jsoninja.ui.component.convertType.CodePreviewPanel
import com.livteam.jsoninja.ui.component.convertType.LanguageSelectorComponent
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.ui.dialog.convertType.model.JsonToTypeDialogConfig
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JSplitPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class JsonToTypeDialogView(
    project: com.intellij.openapi.project.Project,
) {
    private val languageSelector = LanguageSelectorComponent()
    private val rootTypeNameTextField = JBTextField(12)
    private val nullableCheckBox = JBCheckBox(LocalizationBundle.message("dialog.json.to.type.nullable"))
    private val namingConventionComboBox = ComboBox(NamingConvention.entries.toTypedArray())
    private val annotationStyleComboBox = ComboBox(JsonToTypeAnnotationStyle.entries.toTypedArray())
    private val inputEditorView = JsonEditorView(project, "json5")
    private val previewPanel = CodePreviewPanel(project)
    private val rootPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private var onStateChanged: (() -> Unit)? = null
    private var isUpdatingLanguageOptions = false

    val component: JComponent
        get() = rootPanel

    init {
        val controlsPanel = panel {
            row {
                label(LocalizationBundle.message("dialog.json.to.type.language"))
                cell(languageSelector)
                label(LocalizationBundle.message("dialog.json.to.type.root.name")).gap(com.intellij.ui.dsl.builder.RightGap.SMALL)
                cell(rootTypeNameTextField)
                cell(nullableCheckBox)
            }
            row {
                label(LocalizationBundle.message("dialog.json.to.type.naming"))
                cell(namingConventionComboBox)
                label(LocalizationBundle.message("dialog.json.to.type.annotation")).gap(com.intellij.ui.dsl.builder.RightGap.SMALL)
                cell(annotationStyleComboBox)
            }
        }.apply {
            border = com.intellij.util.ui.JBUI.Borders.empty(4, 8)
        }

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputEditorView, previewPanel).apply {
            resizeWeight = 0.5
        }
        rootPanel.add(controlsPanel, BorderLayout.NORTH)
        rootPanel.add(splitPane, BorderLayout.CENTER)

        languageSelector.setOnLanguageChanged {
            updateLanguageOptions(it)
            onStateChanged?.invoke()
        }
        rootTypeNameTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent?) = onStateChanged?.invoke() ?: Unit

            override fun removeUpdate(event: DocumentEvent?) = onStateChanged?.invoke() ?: Unit

            override fun changedUpdate(event: DocumentEvent?) = onStateChanged?.invoke() ?: Unit
        })
        nullableCheckBox.addActionListener { onStateChanged?.invoke() }
        namingConventionComboBox.addActionListener { notifyStateChanged() }
        annotationStyleComboBox.addActionListener { notifyStateChanged() }
        inputEditorView.setOnContentChangeCallback { onStateChanged?.invoke() }
    }

    fun applyConfig(config: JsonToTypeDialogConfig) {
        languageSelector.setSelectedLanguage(config.language)
        rootTypeNameTextField.text = config.rootTypeName
        nullableCheckBox.isSelected = config.allowsNullableFields
        updateLanguageOptions(
            language = config.language,
            selectedNamingConvention = config.namingConvention,
            selectedAnnotationStyle = config.annotationStyle,
        )
    }

    fun collectConfig(): JsonToTypeDialogConfig {
        val selectedLanguage = languageSelector.getSelectedLanguage() ?: SupportedLanguage.KOTLIN
        return JsonToTypeDialogConfig(
            rootTypeName = rootTypeNameTextField.text.trim().ifBlank { "Root" },
            language = selectedLanguage,
            namingConvention = selectedLanguage.getSupportedNamingConvention(
                namingConventionComboBox.selectedItem as? NamingConvention,
            ),
            annotationStyle = selectedLanguage.getSupportedAnnotationStyle(
                annotationStyleComboBox.selectedItem as? JsonToTypeAnnotationStyle,
            ),
            allowsNullableFields = nullableCheckBox.isSelected,
            usesExperimentalGoUnionTypes = false,
        )
    }

    fun setInputText(text: String) {
        inputEditorView.setText(text)
    }

    fun getInputText(): String = inputEditorView.getText()

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

    fun showSuccessPreview(text: String, fileExtension: String) {
        previewPanel.setSuccess(text, fileExtension)
    }

    fun getValidationComponent(): JComponent = rootTypeNameTextField

    fun dispose() {
        inputEditorView.dispose()
        previewPanel.dispose()
    }

    private fun updateLanguageOptions(
        language: SupportedLanguage,
        selectedNamingConvention: NamingConvention = language.defaultNamingConvention,
        selectedAnnotationStyle: JsonToTypeAnnotationStyle = language.defaultAnnotationStyle,
    ) {
        isUpdatingLanguageOptions = true
        try {
            namingConventionComboBox.removeAllItems()
            language.availableNamingConventions.forEach(namingConventionComboBox::addItem)
            namingConventionComboBox.selectedItem = language.getSupportedNamingConvention(selectedNamingConvention)
            namingConventionComboBox.isEnabled = language.availableNamingConventions.size > 1

            annotationStyleComboBox.removeAllItems()
            language.availableAnnotationStyles.forEach(annotationStyleComboBox::addItem)
            annotationStyleComboBox.selectedItem = language.getSupportedAnnotationStyle(selectedAnnotationStyle)
            annotationStyleComboBox.isEnabled = language.availableAnnotationStyles.size > 1
        } finally {
            isUpdatingLanguageOptions = false
        }
    }

    private fun notifyStateChanged() {
        if (!isUpdatingLanguageOptions) {
            onStateChanged?.invoke()
        }
    }
}
