package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.json.JsonFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
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
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import com.livteam.jsoninja.ui.component.editor.SimpleJsonDocumentCreator
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonRootType
import io.burt.jmespath.jackson.JacksonRuntime
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

class GenerateJsonDialogView(
    private val project: Project?,
    private val onLayoutChanged: () -> Unit
) {
    private val LOG = logger<GenerateJsonDialogView>()
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val jmesPathRuntime = JacksonRuntime()

    @Volatile
    private var isDisposed = false

    private lateinit var rootTypeObject: JBRadioButton
    private lateinit var rootTypeArray: JBRadioButton
    private lateinit var objectPropertyCountField: JBTextField
    private lateinit var arrayElementCountField: JBTextField
    private lateinit var propertiesPerObjectInArrayField: JBTextField
    private lateinit var maxDepthField: JBTextField
    private lateinit var randomJson5Checkbox: JBCheckBox

    private lateinit var schemaEditor: EditorTextField
    private lateinit var schemaUrlComboBox: ComboBox<SchemaUrlComboBoxItem>
    private lateinit var loadSchemaFromUrlButton: JButton
    private lateinit var schemaOutputCountField: JBTextField
    private lateinit var schemaJson5Checkbox: JBCheckBox
    private lateinit var tabbedPane: JBTabbedPane

    private val initialConfig = JsonGenerationConfig()
    private var schemaStoreCatalogItems: List<SchemaStoreCatalogItem> = emptyList()
    private var schemaStoreCatalogState = SchemaStoreCatalogState.LOADING
    private var isUpdatingSchemaUrlComboBoxModel = false

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
        isDisposed = true
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
        schemaUrlComboBox = createSchemaUrlComboBox()

        val optionsPanel = panel {
            group(LocalizationBundle.message("dialog.generate.json.schema.group.input")) {
                row(LocalizationBundle.message("dialog.generate.json.schema.url.label")) {
                    cell(schemaUrlComboBox)
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .comment(LocalizationBundle.message("dialog.generate.json.schema.url.comment"))
                    loadSchemaFromUrlButton = button(LocalizationBundle.message("dialog.generate.json.schema.url.load.button")) {
                        loadSchemaFromUrl()
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
        loadSchemaStoreCatalog()
        return schemaPanel
    }

    private fun createSchemaUrlComboBox(): ComboBox<SchemaUrlComboBoxItem> {
        val comboBox = ComboBox<SchemaUrlComboBoxItem>()
        comboBox.isEditable = true
        attachSchemaUrlEditorDocumentListener(comboBox)
        comboBox.addItemListener { itemEvent ->
            if (itemEvent.stateChange != ItemEvent.SELECTED || isUpdatingSchemaUrlComboBoxModel) {
                return@addItemListener
            }

            val selectedItem = itemEvent.item as? SchemaUrlComboBoxItem.CatalogEntry ?: return@addItemListener
            val currentEditorText = getSchemaUrlEditorText().trim()
            if (currentEditorText != selectedItem.displayText) {
                return@addItemListener
            }
            setSchemaUrlEditorText(selectedItem.schemaStoreCatalogItem.url)
        }
        updateSchemaUrlComboBoxModel(
            listOf(
                SchemaUrlComboBoxItem.StatusEntry(
                    LocalizationBundle.message("dialog.generate.json.schema.store.loading")
                )
            ),
            editorText = ""
        )

        return comboBox
    }

    private fun attachSchemaUrlEditorDocumentListener(comboBox: ComboBox<SchemaUrlComboBoxItem>) {
        val editorComponent = comboBox.editor.editorComponent as? JTextComponent ?: return
        editorComponent.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(documentEvent: DocumentEvent?) = filterSchemaStoreCatalogItemsByInput()
            override fun removeUpdate(documentEvent: DocumentEvent?) = filterSchemaStoreCatalogItemsByInput()
            override fun changedUpdate(documentEvent: DocumentEvent?) = filterSchemaStoreCatalogItemsByInput()
        })
    }

    private fun loadSchemaStoreCatalog() {
        schemaStoreCatalogState = SchemaStoreCatalogState.LOADING
        updateSchemaUrlComboBoxModel(
            listOf(
                SchemaUrlComboBoxItem.StatusEntry(
                    LocalizationBundle.message("dialog.generate.json.schema.store.loading")
                )
            ),
            editorText = getSchemaUrlEditorText()
        )

        ApplicationManager.getApplication().executeOnPooledThread {
            if (isDisposed) return@executeOnPooledThread
            try {
                val schemaStoreCatalogText = fetchSchemaText(SCHEMA_STORE_CATALOG_URL)
                val parsedSchemaStoreCatalogItems = parseSchemaStoreCatalog(schemaStoreCatalogText)
                if (parsedSchemaStoreCatalogItems.isEmpty()) {
                    throw IOException("SchemaStore catalog has no valid entries.")
                }

                invokeLater(ModalityState.any()) {
                    if (isDisposed) return@invokeLater
                    schemaStoreCatalogItems = parsedSchemaStoreCatalogItems
                    schemaStoreCatalogState = SchemaStoreCatalogState.READY
                    filterSchemaStoreCatalogItemsByInput()
                }
            } catch (exception: Exception) {
                LOG.warn("Failed to load SchemaStore catalog.", exception)
                invokeLater(ModalityState.any()) {
                    if (isDisposed) return@invokeLater
                    schemaStoreCatalogItems = emptyList()
                    schemaStoreCatalogState = SchemaStoreCatalogState.FAILED
                    updateSchemaUrlComboBoxModel(
                        listOf(
                            SchemaUrlComboBoxItem.StatusEntry(
                                LocalizationBundle.message("dialog.generate.json.schema.store.unavailable")
                            )
                        ),
                        editorText = getSchemaUrlEditorText()
                    )
                }
            }
        }
    }

    private fun parseSchemaStoreCatalog(schemaStoreCatalogText: String): List<SchemaStoreCatalogItem> {
        val schemaStoreCatalogNode = objectMapper.readTree(schemaStoreCatalogText)
        val schemaNameExpression = jmesPathRuntime.compile("schemas[].name")
        val schemaUrlExpression = jmesPathRuntime.compile("schemas[].url")
        val schemaNameNodes = schemaNameExpression.search(schemaStoreCatalogNode)
        val schemaUrlNodes = schemaUrlExpression.search(schemaStoreCatalogNode)

        if (!schemaNameNodes.isArray || !schemaUrlNodes.isArray) {
            return emptyList()
        }

        val schemaStoreCatalogItemsByUrl = linkedMapOf<String, SchemaStoreCatalogItem>()
        val schemaCount = minOf(schemaNameNodes.size(), schemaUrlNodes.size())
        for (index in 0 until schemaCount) {
            val schemaName = schemaNameNodes.path(index).asText("").trim()
            val schemaUrl = schemaUrlNodes.path(index).asText("").trim()
            if (schemaName.isBlank() || schemaUrl.isBlank()) {
                continue
            }

            if (!schemaStoreCatalogItemsByUrl.containsKey(schemaUrl)) {
                schemaStoreCatalogItemsByUrl[schemaUrl] = SchemaStoreCatalogItem(
                    name = schemaName,
                    url = schemaUrl
                )
            }
        }

        return schemaStoreCatalogItemsByUrl.values.toList()
    }

    private fun filterSchemaStoreCatalogItemsByInput() {
        if (!::schemaUrlComboBox.isInitialized || isUpdatingSchemaUrlComboBoxModel || isDisposed) {
            return
        }

        if (schemaStoreCatalogState != SchemaStoreCatalogState.READY) {
            return
        }

        val editorText = getSchemaUrlEditorText()
        val normalizedFilterKeyword = editorText.trim().lowercase()
        val filteredSchemaStoreCatalogItems = if (normalizedFilterKeyword.isBlank()) {
            schemaStoreCatalogItems
        } else {
            schemaStoreCatalogItems.filter { schemaStoreCatalogItem ->
                schemaStoreCatalogItem.name.lowercase().contains(normalizedFilterKeyword) ||
                    schemaStoreCatalogItem.url.lowercase().contains(normalizedFilterKeyword)
            }
        }

        if (filteredSchemaStoreCatalogItems.isEmpty()) {
            updateSchemaUrlComboBoxModel(
                listOf(
                    SchemaUrlComboBoxItem.StatusEntry(
                        LocalizationBundle.message("dialog.generate.json.schema.store.no.match")
                    )
                ),
                editorText = editorText
            )
            return
        }

        updateSchemaUrlComboBoxModel(
            filteredSchemaStoreCatalogItems.map { schemaStoreCatalogItem ->
                SchemaUrlComboBoxItem.CatalogEntry(schemaStoreCatalogItem)
            },
            editorText = editorText
        )
    }

    private fun updateSchemaUrlComboBoxModel(
        schemaUrlComboBoxItems: List<SchemaUrlComboBoxItem>,
        editorText: String
    ) {
        if (!::schemaUrlComboBox.isInitialized || isDisposed) {
            return
        }

        val wasPopupVisible = schemaUrlComboBox.isPopupVisible
        isUpdatingSchemaUrlComboBoxModel = true
        try {
            schemaUrlComboBox.model = DefaultComboBoxModel(schemaUrlComboBoxItems.toTypedArray())
            schemaUrlComboBox.selectedItem = null
            setSchemaUrlEditorText(editorText)
        } finally {
            isUpdatingSchemaUrlComboBoxModel = false
        }

        if (wasPopupVisible && schemaUrlComboBox.itemCount > 0 && schemaStoreCatalogState == SchemaStoreCatalogState.READY) {
            schemaUrlComboBox.showPopup()
        }
    }

    private fun getSchemaUrlEditorText(): String {
        val editorComponent = schemaUrlComboBox.editor.editorComponent as? JTextComponent ?: return ""
        return editorComponent.text
    }

    private fun setSchemaUrlEditorText(schemaUrlText: String) {
        val editorComponent = schemaUrlComboBox.editor.editorComponent as? JTextComponent ?: return
        editorComponent.text = schemaUrlText
    }

    private fun getSchemaUrlInputText(): String {
        val selectedItem = schemaUrlComboBox.selectedItem
        if (selectedItem is SchemaUrlComboBoxItem.CatalogEntry) {
            return selectedItem.schemaStoreCatalogItem.url
        }

        return getSchemaUrlEditorText().trim()
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

    private fun loadSchemaFromUrl() {
        val schemaUrl = getSchemaUrlInputText()
        if (schemaUrl.isBlank()) {
            showSchemaUrlError(LocalizationBundle.message("dialog.generate.json.schema.url.empty"))
            return
        }

        if (!schemaUrl.startsWith("http://") && !schemaUrl.startsWith("https://")) {
            showSchemaUrlError(LocalizationBundle.message("dialog.generate.json.schema.url.invalid"))
            return
        }

        loadSchemaFromUrlButton.isEnabled = false

        ApplicationManager.getApplication().executeOnPooledThread {
            if (isDisposed) return@executeOnPooledThread
            try {
                val fetchedSchemaText = fetchSchemaText(schemaUrl)
                if (fetchedSchemaText.trim().isEmpty()) {
                    throw IOException(LocalizationBundle.message("dialog.generate.json.schema.url.fetch.empty"))
                }

                invokeLater(ModalityState.any()) {
                    if (isDisposed) return@invokeLater
                    setSchemaEditorText(fetchedSchemaText)
                }
            } catch (exception: Exception) {
                val message = exception.message
                    ?: LocalizationBundle.message("dialog.generate.json.schema.url.fetch.failed")
                invokeLater(ModalityState.any()) {
                    if (isDisposed) return@invokeLater
                    showSchemaUrlError(message)
                }
            } finally {
                invokeLater(ModalityState.any()) {
                    if (isDisposed) return@invokeLater
                    loadSchemaFromUrlButton.isEnabled = true
                }
            }
        }
    }

    private fun fetchSchemaText(schemaUrl: String): String {
        val connection = URL(schemaUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/schema+json, application/json;q=0.9, */*;q=0.8")

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException(
                    LocalizationBundle.message("dialog.generate.json.schema.url.http.status", responseCode)
                )
            }

            connection.inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun setSchemaEditorText(schemaText: String) {
        if (project == null) {
            schemaEditor.text = schemaText
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            schemaEditor.text = schemaText
        }
    }

    private fun showSchemaUrlError(errorMessage: String) {
        Messages.showErrorDialog(
            project,
            errorMessage,
            LocalizationBundle.message("dialog.generate.json.error.title")
        )
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

    private data class SchemaStoreCatalogItem(
        val name: String,
        val url: String
    )

    private enum class SchemaStoreCatalogState {
        LOADING,
        READY,
        FAILED
    }

    private sealed class SchemaUrlComboBoxItem(open val displayText: String) {
        class CatalogEntry(val schemaStoreCatalogItem: SchemaStoreCatalogItem) : SchemaUrlComboBoxItem(
            schemaStoreCatalogItem.name
        )

        class StatusEntry(override val displayText: String) : SchemaUrlComboBoxItem(displayText)

        override fun toString(): String = displayText
    }

    companion object {
        private const val SCHEMA_STORE_CATALOG_URL = "https://www.schemastore.org/api/json/catalog.json"
    }
}
