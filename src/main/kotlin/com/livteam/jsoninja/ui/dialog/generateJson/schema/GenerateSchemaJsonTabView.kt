package com.livteam.jsoninja.ui.dialog.generateJson.schema

import com.intellij.json.JsonFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import com.livteam.jsoninja.ui.component.editor.SimpleJsonDocumentCreator
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import java.awt.BorderLayout
import javax.swing.ComboBoxEditor
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

class GenerateSchemaJsonTabView(
    private val project: Project?,
    private val initialConfig: JsonGenerationConfig
) {
    private lateinit var schemaEditor: EditorTextField
    private lateinit var schemaUrlComboBox: ComboBox<SchemaUrlComboBoxItem>
    private lateinit var loadSchemaFromUrlButton: JButton
    private lateinit var schemaOutputCountField: JBTextField
    private lateinit var schemaJson5Checkbox: JBCheckBox
    private var isUpdatingSchemaUrlComboBoxModel = false

    private var onSchemaUrlInputChangedCallback: (() -> Unit)? = null
    private var onLoadSchemaFromUrlRequestedCallback: (() -> Unit)? = null

    val component: JComponent = createComponent()

    fun setOnSchemaUrlInputChanged(callback: () -> Unit) {
        onSchemaUrlInputChangedCallback = callback
    }

    fun setOnLoadSchemaFromUrlRequested(callback: () -> Unit) {
        onLoadSchemaFromUrlRequestedCallback = callback
    }

    fun getSchemaText(): String = schemaEditor.text

    fun getSchemaInputComponent(): JComponent = schemaEditor

    fun getSchemaOutputCountText(): String = schemaOutputCountField.text

    fun getSchemaOutputCountField(): JComponent = schemaOutputCountField

    fun isJson5Selected(): Boolean = schemaJson5Checkbox.isSelected

    fun getSchemaUrlEditorText(): String {
        val editorComponent = schemaUrlComboBox.editor.editorComponent as? JTextComponent ?: return ""
        return editorComponent.text
    }

    fun setSchemaUrlEditorText(schemaUrlText: String) {
        val editorComponent = schemaUrlComboBox.editor.editorComponent as? JTextComponent ?: return
        editorComponent.text = schemaUrlText
    }

    fun getSchemaUrlInputText(): String {
        val editorText = getSchemaUrlEditorText().trim()
        val selectedItem = schemaUrlComboBox.selectedItem
        if (selectedItem is SchemaUrlComboBoxItem.CatalogEntry) {
            if (editorText.isBlank() || editorText == selectedItem.displayText) {
                return selectedItem.schemaStoreCatalogItem.url
            }
        }

        if (editorText.isNotBlank()) {
            return editorText
        }

        if (selectedItem is SchemaUrlComboBoxItem.CatalogEntry) {
            return selectedItem.schemaStoreCatalogItem.url
        }
        return editorText
        return getSchemaUrlEditorText().trim()
    }

    fun setSchemaEditorText(schemaText: String) {
        schemaEditor.text = schemaText
    }

    fun setLoadSchemaFromUrlButtonEnabled(isEnabled: Boolean) {
        loadSchemaFromUrlButton.isEnabled = isEnabled
    }

    fun updateSchemaUrlComboBoxModel(
        schemaUrlComboBoxItems: List<SchemaUrlComboBoxItem>,
        editorText: String,
        showPopupWhenAvailable: Boolean
    ) {
        val editorComponent = schemaUrlComboBox.editor.editorComponent as? JTextComponent
        val currentCaretPosition = editorComponent?.caretPosition

        val wasPopupVisible = schemaUrlComboBox.isPopupVisible
        isUpdatingSchemaUrlComboBoxModel = true
        try {
            schemaUrlComboBox.model = DefaultComboBoxModel(schemaUrlComboBoxItems.toTypedArray())
            schemaUrlComboBox.selectedItem = null

            if (editorComponent == null || editorComponent.text != editorText) {
                setSchemaUrlEditorText(editorText)

                if (currentCaretPosition != null && currentCaretPosition <= editorText.length) {
                    editorComponent?.caretPosition = currentCaretPosition
                }
            }
        } finally {
            isUpdatingSchemaUrlComboBoxModel = false
        }

        if (showPopupWhenAvailable && wasPopupVisible && schemaUrlComboBox.itemCount > 0) {
            schemaUrlComboBox.showPopup()
        }
    }

    fun dispose() {
        (schemaEditor as? Disposable)?.let { disposableEditor ->
            com.intellij.openapi.util.Disposer.dispose(disposableEditor)
        }
    }

    private fun createComponent(): JComponent {
        val schemaPanel = JPanel(BorderLayout())
        schemaUrlComboBox = createSchemaUrlComboBox()

        val optionsPanel = panel {
            group(LocalizationBundle.message("dialog.generate.json.schema.group.input")) {
                row(LocalizationBundle.message("dialog.generate.json.schema.url.label")) {
                    cell(schemaUrlComboBox)
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .comment(LocalizationBundle.message("dialog.generate.json.schema.url.comment"))
                    loadSchemaFromUrlButton = button(LocalizationBundle.message("dialog.generate.json.schema.url.load.button")) {
                        onLoadSchemaFromUrlRequestedCallback?.invoke()
                    }.component
                }.layout(RowLayout.PARENT_GRID)

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

    private fun createSchemaUrlComboBox(): ComboBox<SchemaUrlComboBoxItem> {
        val comboBox = object : ComboBox<SchemaUrlComboBoxItem>() {
            override fun configureEditor(anEditor: ComboBoxEditor?, anItem: Any?) {
                if (isUpdatingSchemaUrlComboBoxModel) {
                    return
                }
                super.configureEditor(anEditor, anItem)
            }
        }
        comboBox.isEditable = true
        attachSchemaUrlEditorDocumentListener(comboBox)

        return comboBox
    }

    private fun attachSchemaUrlEditorDocumentListener(comboBox: ComboBox<SchemaUrlComboBoxItem>) {
        val editorComponent = comboBox.editor.editorComponent as? JTextComponent ?: return
        editorComponent.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(documentEvent: DocumentEvent?) {
                if (isUpdatingSchemaUrlComboBoxModel) return
                onSchemaUrlInputChangedCallback?.invoke()
            }

            override fun removeUpdate(documentEvent: DocumentEvent?) {
                if (isUpdatingSchemaUrlComboBoxModel) return
                onSchemaUrlInputChangedCallback?.invoke()
            }

            override fun changedUpdate(documentEvent: DocumentEvent?) {
                if (isUpdatingSchemaUrlComboBoxModel) return
                onSchemaUrlInputChangedCallback?.invoke()
            }
        })
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
            addSettingsProvider { editor ->
                editor.settings.applySchemaEditorSettings()
                editor.setHorizontalScrollbarVisible(true)
                editor.setVerticalScrollbarVisible(true)
                editor.isEmbeddedIntoDialogWrapper = true
            }
            putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)
        }
    }

    private fun EditorSettings.applySchemaEditorSettings() {
        isLineNumbersShown = true
    }
}
