package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.JsonToTypeAnnotationStyle
import com.livteam.jsoninja.services.typeConversion.NamingConvention
import com.livteam.jsoninja.ui.component.convertType.ConvertTypeWorkspacePanel
import com.livteam.jsoninja.ui.component.convertType.LanguageSelectorComponent
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.ui.dialog.convertType.model.JsonToTypeDialogConfig
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class JsonToTypeDialogView(
    project: com.intellij.openapi.project.Project,
) {
    private val languageSelector = LanguageSelectorComponent(project)
    private val rootTypeNameTextField = JBTextField(12)
    private val nullableCheckBox = JBCheckBox(LocalizationBundle.message("dialog.json.to.type.nullable"))
    private val namingConventionComboBox = ComboBox(NamingConvention.entries.toTypedArray())
    private val originalKeysLabel = JBLabel(LocalizationBundle.message("dialog.json.to.type.original.keys"))
    private val annotationStyleComboBox = ComboBox(JsonToTypeAnnotationStyle.entries.toTypedArray())
    private val inputEditorView = JsonEditorView(project, "json5")
    private val workspace: ConvertTypeWorkspacePanel
    private lateinit var namingConventionRow: Row
    private lateinit var originalKeysRow: Row
    private var onStateChanged: (() -> Unit)? = null
    private var isUpdatingLanguageOptions = false

    val component: JComponent
        get() = workspace

    init {
        languageSelector.setAccessibleName(LocalizationBundle.message("dialog.json.to.type.language"))
        namingConventionComboBox.renderer = SimpleListCellRenderer.create("") { convention ->
            when (convention) {
                NamingConvention.CAMEL_CASE -> "camelCase"
                NamingConvention.PASCAL_CASE -> "PascalCase"
                NamingConvention.SNAKE_CASE -> "snake_case"
            }
        }
        annotationStyleComboBox.renderer = SimpleListCellRenderer.create("") { style ->
            LocalizationBundle.message(when (style) {
                JsonToTypeAnnotationStyle.NONE -> "dialog.json.to.type.annotation.none"
                JsonToTypeAnnotationStyle.GSON_SERIALIZED_NAME -> "dialog.json.to.type.annotation.gson"
                JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> "dialog.json.to.type.annotation.jackson"
                JsonToTypeAnnotationStyle.KOTLIN_SERIAL_NAME -> "dialog.json.to.type.annotation.kotlinx"
                JsonToTypeAnnotationStyle.GO_JSON_TAG -> "dialog.json.to.type.annotation.go"
                JsonToTypeAnnotationStyle.CSHARP_JSON_PROPERTY_NAME -> "dialog.json.to.type.annotation.csharp"
            })
        }
        val settingsPanel = panel {
            row {
                cell(languageSelector)
                    .label(LocalizationBundle.message("dialog.json.to.type.language"), LabelPosition.TOP)
                    .align(AlignX.FILL).resizableColumn()
            }
            row {
                cell(rootTypeNameTextField)
                    .label(LocalizationBundle.message("dialog.json.to.type.root.name"), LabelPosition.TOP)
                    .align(AlignX.FILL).resizableColumn()
            }
            namingConventionRow = row {
                cell(namingConventionComboBox)
                    .label(LocalizationBundle.message("dialog.json.to.type.naming"), LabelPosition.TOP)
                    .align(AlignX.FILL).resizableColumn()
            }
            originalKeysRow = row {
                cell(originalKeysLabel)
                    .label(LocalizationBundle.message("dialog.json.to.type.naming"), LabelPosition.TOP)
            }.visible(false)
            row {
                cell(annotationStyleComboBox)
                    .label(LocalizationBundle.message("dialog.json.to.type.annotation"), LabelPosition.TOP)
                    .align(AlignX.FILL).resizableColumn()
            }
            row {
                cell(nullableCheckBox)
            }
        }
        workspace = ConvertTypeWorkspacePanel(
            project,
            inputEditorView,
            settingsPanel,
            LocalizationBundle.message("common.convert.input.json"),
        ).apply {
            setInputLanguage("JSON")
            setPreviewLanguage(SupportedLanguage.KOTLIN.getDisplayName())
        }

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

    fun showSuccessPreview(text: String, fileExtension: String) {
        workspace.showSuccessPreview(text, fileExtension)
    }

    fun getValidationComponent(): JComponent = rootTypeNameTextField

    fun getPreferredFocusedComponent(): JComponent = inputEditorView.editor

    fun dispose() {
        inputEditorView.dispose()
        workspace.dispose()
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
            namingConventionRow.visible(!language.usesOriginalJsonFieldNames)
            originalKeysRow.visible(language.usesOriginalJsonFieldNames)

            annotationStyleComboBox.removeAllItems()
            language.availableAnnotationStyles.forEach(annotationStyleComboBox::addItem)
            annotationStyleComboBox.selectedItem = language.getSupportedAnnotationStyle(selectedAnnotationStyle)
            annotationStyleComboBox.isEnabled = language.availableAnnotationStyles.size > 1
            workspace.setPreviewLanguage(language.getDisplayName())
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
