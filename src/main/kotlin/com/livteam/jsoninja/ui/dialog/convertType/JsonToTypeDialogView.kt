package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.JsonToTypeAnnotationStyle
import com.livteam.jsoninja.services.typeConversion.NamingConvention
import com.livteam.jsoninja.ui.component.convertType.CodePreviewPanel
import com.livteam.jsoninja.ui.component.convertType.LanguageSelectorComponent
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.ui.dialog.convertType.model.JsonToTypeDialogConfig
import java.awt.BorderLayout
import java.awt.FlowLayout
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
    private val goUnionCheckBox = JBCheckBox("Go union types")
    private val inputEditorView = JsonEditorView(project, "json5")
    private val previewPanel = CodePreviewPanel(project)
    private val rootPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private var onStateChanged: (() -> Unit)? = null

    val component: JComponent
        get() = rootPanel

    init {
        val controlsPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            add(JBLabel(LocalizationBundle.message("dialog.json.to.type.language")))
            add(languageSelector)
            add(JBLabel(LocalizationBundle.message("dialog.json.to.type.root.name")))
            add(rootTypeNameTextField)
            add(nullableCheckBox)
            add(JBLabel(LocalizationBundle.message("dialog.json.to.type.naming")))
            add(namingConventionComboBox)
            add(JBLabel(LocalizationBundle.message("dialog.json.to.type.annotation")))
            add(annotationStyleComboBox)
            add(goUnionCheckBox)
        }

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputEditorView, previewPanel).apply {
            resizeWeight = 0.5
        }
        rootPanel.add(controlsPanel, BorderLayout.NORTH)
        rootPanel.add(splitPane, BorderLayout.CENTER)

        languageSelector.setOnLanguageChanged {
            updateGoUnionAvailability(it)
            onStateChanged?.invoke()
        }
        rootTypeNameTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent?) = onStateChanged?.invoke() ?: Unit

            override fun removeUpdate(event: DocumentEvent?) = onStateChanged?.invoke() ?: Unit

            override fun changedUpdate(event: DocumentEvent?) = onStateChanged?.invoke() ?: Unit
        })
        nullableCheckBox.addActionListener { onStateChanged?.invoke() }
        namingConventionComboBox.addActionListener { onStateChanged?.invoke() }
        annotationStyleComboBox.addActionListener { onStateChanged?.invoke() }
        goUnionCheckBox.addActionListener { onStateChanged?.invoke() }
        inputEditorView.setOnContentChangeCallback { onStateChanged?.invoke() }
    }

    fun applyConfig(config: JsonToTypeDialogConfig) {
        languageSelector.setSelectedLanguage(config.language)
        rootTypeNameTextField.text = config.rootTypeName
        nullableCheckBox.isSelected = config.allowsNullableFields
        namingConventionComboBox.selectedItem = config.namingConvention
        annotationStyleComboBox.selectedItem = config.annotationStyle
        goUnionCheckBox.isSelected = config.usesExperimentalGoUnionTypes
        updateGoUnionAvailability(config.language)
    }

    fun collectConfig(): JsonToTypeDialogConfig {
        val selectedLanguage = languageSelector.getSelectedLanguage() ?: SupportedLanguage.KOTLIN
        return JsonToTypeDialogConfig(
            rootTypeName = rootTypeNameTextField.text.trim().ifBlank { "Root" },
            language = selectedLanguage,
            namingConvention = namingConventionComboBox.selectedItem as? NamingConvention ?: selectedLanguage.defaultNamingConvention,
            annotationStyle = annotationStyleComboBox.selectedItem as? JsonToTypeAnnotationStyle ?: selectedLanguage.defaultAnnotationStyle,
            allowsNullableFields = nullableCheckBox.isSelected,
            usesExperimentalGoUnionTypes = goUnionCheckBox.isSelected,
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

    private fun updateGoUnionAvailability(language: SupportedLanguage) {
        goUnionCheckBox.isEnabled = language == SupportedLanguage.GO
        if (language != SupportedLanguage.GO) {
            goUnionCheckBox.isSelected = false
        }
    }
}
