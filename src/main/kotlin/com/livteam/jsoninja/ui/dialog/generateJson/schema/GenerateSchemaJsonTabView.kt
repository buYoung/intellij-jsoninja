package com.livteam.jsoninja.ui.dialog.generateJson.schema

import com.intellij.ide.IdeEventQueue
import com.intellij.json.JsonFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.EditorTextField
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import com.livteam.jsoninja.ui.component.editor.SimpleJsonDocumentCreator
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JSeparator
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.DefaultListModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import com.intellij.ui.awt.RelativePoint

class GenerateSchemaJsonTabView(
    private val project: Project?,
    private val initialConfig: JsonGenerationConfig
) {
    private lateinit var schemaEditor: EditorTextField
    private lateinit var schemaUrlSearchField: SearchTextField
    private lateinit var schemaUrlSuggestionList: JBList<SchemaUrlComboBoxItem>
    private lateinit var loadSchemaFromUrlButton: JButton
    private lateinit var schemaOutputCountField: JBTextField
    private lateinit var schemaRequiredAndOptionalRadioButton: JBRadioButton
    private lateinit var schemaRequiredOnlyRadioButton: JBRadioButton
    private lateinit var schemaRequiredAndOptionalCommentedRadioButton: JBRadioButton
    private lateinit var schemaJson5Checkbox: JBCheckBox
    private var schemaUrlSuggestionPopup: JBPopup? = null
    private var selectedSchemaStoreCatalogItem: SchemaStoreCatalogItem? = null
    private var schemaUrlSuggestionItems: List<SchemaUrlComboBoxItem> = emptyList()
    private var isUpdatingSchemaUrlEditorText = false
    private var isDisposed = false

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

    fun getSchemaPropertyGenerationMode(): SchemaPropertyGenerationMode {
        return when {
            schemaRequiredOnlyRadioButton.isSelected -> SchemaPropertyGenerationMode.REQUIRED_ONLY
            schemaRequiredAndOptionalCommentedRadioButton.isSelected -> SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
            else -> SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL
        }
    }

    fun isJson5Selected(): Boolean = schemaJson5Checkbox.isSelected

    fun getSchemaUrlEditorText(): String {
        return schemaUrlSearchField.textEditor.text
    }

    fun setSchemaUrlEditorText(schemaUrlText: String) {
        val editorComponent = schemaUrlSearchField.textEditor
        val currentCaretPosition = editorComponent.caretPosition
        isUpdatingSchemaUrlEditorText = true
        try {
            editorComponent.text = schemaUrlText
            if (currentCaretPosition <= schemaUrlText.length) {
                editorComponent.caretPosition = currentCaretPosition
            }
        } finally {
            isUpdatingSchemaUrlEditorText = false
        }
    }

    fun getSchemaUrlInputText(): String {
        val editorText = getSchemaUrlEditorText().trim()
        val selectedCatalogItem = selectedSchemaStoreCatalogItem
        if (selectedCatalogItem != null && (editorText.isBlank() || editorText == selectedCatalogItem.name)) {
            return selectedCatalogItem.url
        }

        return editorText
    }

    fun setSchemaEditorText(schemaText: String) {
        schemaEditor.text = schemaText
    }

    fun setLoadSchemaFromUrlButtonEnabled(isEnabled: Boolean) {
        loadSchemaFromUrlButton.isEnabled = isEnabled
    }

    fun updateSchemaUrlSuggestions(
        schemaUrlSuggestionItems: List<SchemaUrlComboBoxItem>,
        editorText: String,
        showPopupWhenAvailable: Boolean
    ) {
        if (isDisposed) {
            return
        }
        this.schemaUrlSuggestionItems = schemaUrlSuggestionItems
        refreshSchemaUrlSuggestionList(schemaUrlSuggestionItems)

        val editorComponent = schemaUrlSearchField.textEditor
        if (editorComponent.text != editorText) {
            setSchemaUrlEditorText(editorText)
            val selectedCatalogItem = selectedSchemaStoreCatalogItem
            if (selectedCatalogItem != null && editorText.isNotBlank() && editorText != selectedCatalogItem.name) {
                selectedSchemaStoreCatalogItem = null
            }
        }

        if (showPopupWhenAvailable && schemaUrlSuggestionItems.isNotEmpty()) {
            showSchemaUrlSuggestionPopup()
            selectFirstSelectableSuggestion()
            return
        }

        hideSchemaUrlSuggestionPopup()
    }

    fun dispose() {
        isDisposed = true
        hideSchemaUrlSuggestionPopup()
        (schemaEditor as? Disposable)?.let { disposableEditor ->
            com.intellij.openapi.util.Disposer.dispose(disposableEditor)
        }
    }

    private fun createComponent(): JComponent {
        val schemaPanel = JPanel(BorderLayout())
        schemaUrlSearchField = createSchemaUrlSearchField()
        schemaUrlSuggestionList = createSchemaUrlSuggestionList()

        val optionsPanel = panel {
            group(LocalizationBundle.message("dialog.generate.json.schema.group.input")) {
                row(LocalizationBundle.message("dialog.generate.json.schema.url.label")) {
                    cell(schemaUrlSearchField)
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .comment(LocalizationBundle.message("dialog.generate.json.schema.url.comment"))
                    loadSchemaFromUrlButton =
                        button(LocalizationBundle.message("dialog.generate.json.schema.url.load.button")) {
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

                group(LocalizationBundle.message("dialog.generate.json.schema.property.mode.label")) {
                    buttonsGroup {
                        row {
                            schemaRequiredAndOptionalRadioButton = radioButton(
                                LocalizationBundle.message("dialog.generate.json.schema.property.mode.required.optional"),
                                SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL
                            ).component
                        }
                        row {
                            schemaRequiredOnlyRadioButton = radioButton(
                                LocalizationBundle.message("dialog.generate.json.schema.property.mode.required.only"),
                                SchemaPropertyGenerationMode.REQUIRED_ONLY
                            ).component
                        }
                        row {
                            schemaRequiredAndOptionalCommentedRadioButton = radioButton(
                                LocalizationBundle.message("dialog.generate.json.schema.property.mode.required.optional.commented"),
                                SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
                            ).component
                        }
                    }.bind(
                        { getSchemaPropertyGenerationMode() },
                        { selectedMode ->
                            setSchemaPropertyGenerationMode(selectedMode)
                            updateSchemaJson5CheckboxState(selectedMode)
                        }
                    )
                }

                row {
                    schemaJson5Checkbox = checkBox(LocalizationBundle.message("dialog.generate.json.checkbox.json5"))
                        .apply {
                            component.isSelected = initialConfig.isJson5
                            updateSchemaJson5CheckboxState(getSchemaPropertyGenerationMode())
                        }
                        .component
                }

            }
        }

        schemaEditor = createSchemaEditor()
        val groupSeparatorColor = resolveGroupSeparatorColor(optionsPanel)
        val schemaEditorContainer = JPanel(BorderLayout()).apply {
            val dividerPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8, 0, 10, 0)
                add(JSeparator().apply {
                    foreground = groupSeparatorColor
                    background = groupSeparatorColor
                }, BorderLayout.NORTH)
            }
            add(dividerPanel, BorderLayout.NORTH)
            add(schemaEditor, BorderLayout.CENTER)
        }
        schemaPanel.add(optionsPanel, BorderLayout.NORTH)
        schemaPanel.add(schemaEditorContainer, BorderLayout.CENTER)

        return schemaPanel
    }

    private fun setSchemaPropertyGenerationMode(schemaPropertyGenerationMode: SchemaPropertyGenerationMode) {
        schemaRequiredAndOptionalRadioButton.isSelected =
            schemaPropertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL
        schemaRequiredOnlyRadioButton.isSelected =
            schemaPropertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_ONLY
        schemaRequiredAndOptionalCommentedRadioButton.isSelected =
            schemaPropertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
    }

    private fun updateSchemaJson5CheckboxState(schemaPropertyGenerationMode: SchemaPropertyGenerationMode) {
        if (!::schemaJson5Checkbox.isInitialized) {
            return
        }

        val isCommentedMode = schemaPropertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
        if (isCommentedMode) {
            schemaJson5Checkbox.isSelected = true
            schemaJson5Checkbox.isEnabled = false
        } else {
            schemaJson5Checkbox.isEnabled = true
        }
    }

    private fun createSchemaUrlSearchField(): SearchTextField {
        val searchTextField = SearchTextField()
        attachSchemaUrlEditorDocumentListener(searchTextField)
        searchTextField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(keyEvent: KeyEvent) {
                when (keyEvent.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        if (schemaUrlSuggestionItems.isNotEmpty()) {
                            showSchemaUrlSuggestionPopup()
                            selectFirstSelectableSuggestion()
                            keyEvent.consume()
                        }
                    }

                    KeyEvent.VK_ENTER -> {
                        if (isSchemaUrlSuggestionPopupVisible() && applySelectedSchemaUrlSuggestion()) {
                            keyEvent.consume()
                        }
                    }

                    KeyEvent.VK_ESCAPE -> {
                        if (isSchemaUrlSuggestionPopupVisible()) {
                            hideSchemaUrlSuggestionPopup()
                            keyEvent.consume()
                        }
                    }
                }
            }
        })
        searchTextField.textEditor.addFocusListener(object : FocusAdapter() {
            override fun focusGained(focusEvent: FocusEvent?) {
                if (schemaUrlSuggestionItems.isNotEmpty()) {
                    showSchemaUrlSuggestionPopup()
                    selectFirstSelectableSuggestion()
                }
            }
        })

        return searchTextField
    }

    private fun attachSchemaUrlEditorDocumentListener(searchTextField: SearchTextField) {
        searchTextField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(documentEvent: DocumentEvent?) {
                handleSchemaUrlEditorTextChanged()
            }

            override fun removeUpdate(documentEvent: DocumentEvent?) {
                handleSchemaUrlEditorTextChanged()
            }

            override fun changedUpdate(documentEvent: DocumentEvent?) {
                handleSchemaUrlEditorTextChanged()
            }
        })
    }

    private fun handleSchemaUrlEditorTextChanged() {
        if (isUpdatingSchemaUrlEditorText) {
            return
        }

        val editorText = getSchemaUrlEditorText().trim()
        val selectedCatalogItem = selectedSchemaStoreCatalogItem
        if (selectedCatalogItem != null && editorText.isNotBlank() && editorText != selectedCatalogItem.name) {
            selectedSchemaStoreCatalogItem = null
        }

        onSchemaUrlInputChangedCallback?.invoke()
    }

    private fun createSchemaUrlSuggestionList(): JBList<SchemaUrlComboBoxItem> {
        val suggestionList = JBList<SchemaUrlComboBoxItem>()
        suggestionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        suggestionList.cellRenderer = createSchemaUrlSuggestionRenderer()
        suggestionList.addListSelectionListener {
            val selectedSuggestion = suggestionList.selectedValue
            if (selectedSuggestion is SchemaUrlComboBoxItem.StatusEntry) {
                suggestionList.clearSelection()
            }
        }
        suggestionList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(mouseEvent: java.awt.event.MouseEvent) {
                if (mouseEvent.clickCount != 1) {
                    return
                }

                val clickedIndex = suggestionList.locationToIndex(mouseEvent.point)
                if (clickedIndex < 0) {
                    return
                }
                val clickedCellBounds = suggestionList.getCellBounds(clickedIndex, clickedIndex) ?: return
                if (!clickedCellBounds.contains(mouseEvent.point)) {
                    return
                }

                val clickedItem = suggestionList.model.getElementAt(clickedIndex)
                suggestionList.selectedIndex = clickedIndex
                applySchemaUrlSuggestion(clickedItem)
            }
        })
        suggestionList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(keyEvent: KeyEvent) {
                when (keyEvent.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        if (applySelectedSchemaUrlSuggestion()) {
                            keyEvent.consume()
                        }
                    }

                    KeyEvent.VK_ESCAPE -> {
                        hideSchemaUrlSuggestionPopup()
                        keyEvent.consume()
                    }
                }
            }
        })
        return suggestionList
    }

    private fun createSchemaUrlSuggestionRenderer(): ListCellRenderer<in SchemaUrlComboBoxItem> {
        val defaultRenderer = JBList<SchemaUrlComboBoxItem>().cellRenderer as ListCellRenderer<in SchemaUrlComboBoxItem>
        return ListCellRenderer { list, value, index, isSelected, cellHasFocus ->
            val renderedComponent = defaultRenderer.getListCellRendererComponent(
                list,
                value,
                index,
                isSelected,
                cellHasFocus
            )

            if (renderedComponent is JLabel && value is SchemaUrlComboBoxItem.StatusEntry) {
                renderedComponent.isEnabled = false
                renderedComponent.foreground = UIManager.getColor("Label.disabledForeground")
            }

            renderedComponent
        }
    }

    private fun refreshSchemaUrlSuggestionList(schemaUrlSuggestionItems: List<SchemaUrlComboBoxItem>) {
        val listModel = DefaultListModel<SchemaUrlComboBoxItem>()
        schemaUrlSuggestionItems.forEach { schemaUrlSuggestionItem ->
            listModel.addElement(schemaUrlSuggestionItem)
        }
        schemaUrlSuggestionList.model = listModel
        schemaUrlSuggestionList.clearSelection()
    }

    private fun applySelectedSchemaUrlSuggestion(): Boolean {
        val selectedItem = schemaUrlSuggestionList.selectedValue
            ?: schemaUrlSuggestionItems.firstOrNull { schemaUrlSuggestionItem ->
                schemaUrlSuggestionItem is SchemaUrlComboBoxItem.CatalogEntry
            }
            ?: return false

        return applySchemaUrlSuggestion(selectedItem)
    }

    private fun applySchemaUrlSuggestion(schemaUrlSuggestionItem: SchemaUrlComboBoxItem): Boolean {
        if (schemaUrlSuggestionItem !is SchemaUrlComboBoxItem.CatalogEntry) {
            return false
        }

        selectedSchemaStoreCatalogItem = schemaUrlSuggestionItem.schemaStoreCatalogItem
        setSchemaUrlEditorText(schemaUrlSuggestionItem.schemaStoreCatalogItem.name)
        hideSchemaUrlSuggestionPopup()
        return true
    }

    private fun selectFirstSelectableSuggestion() {
        val firstSelectableIndex = schemaUrlSuggestionItems.indexOfFirst { schemaUrlSuggestionItem ->
            schemaUrlSuggestionItem is SchemaUrlComboBoxItem.CatalogEntry
        }
        if (firstSelectableIndex < 0) {
            schemaUrlSuggestionList.clearSelection()
            return
        }

        schemaUrlSuggestionList.selectedIndex = firstSelectableIndex
        schemaUrlSuggestionList.ensureIndexIsVisible(firstSelectableIndex)
    }

    private fun showSchemaUrlSuggestionPopup() {
        if (isDisposed || schemaUrlSuggestionItems.isEmpty()) {
            return
        }
        val popupAnchorComponent = schemaUrlSearchField.textEditor
        if (!popupAnchorComponent.isShowing) {
            return
        }

        if (schemaUrlSuggestionPopup?.isVisible == true) {
            hideSchemaUrlSuggestionPopup()
        }

        val popupWidth = popupAnchorComponent.width.takeIf { it > 0 } ?: 420
        val popupContent = JPanel(BorderLayout()).apply {
            add(
                JBScrollPane(schemaUrlSuggestionList).apply {
                    border = JBUI.Borders.empty()
                    preferredSize = JBUI.size(popupWidth, 200)
                },
                BorderLayout.CENTER
            )
        }

        schemaUrlSuggestionPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(popupContent, schemaUrlSuggestionList)
            .setCancelOnClickOutside(true)
            .setCancelCallback { shouldCancelSchemaUrlSuggestionPopup() }
            .setCancelOnOtherWindowOpen(true)
            .setCancelOnWindowDeactivation(true)
            .setCancelKeyEnabled(true)
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(false)
            .createPopup()
            .also { popup ->
                val anchorPoint = RelativePoint(popupAnchorComponent, Point(0, popupAnchorComponent.height))
                popup.show(anchorPoint)
            }
    }

    private fun hideSchemaUrlSuggestionPopup() {
        schemaUrlSuggestionPopup?.cancel()
        schemaUrlSuggestionPopup = null
    }

    private fun isSchemaUrlSuggestionPopupVisible(): Boolean {
        return schemaUrlSuggestionPopup?.isVisible == true
    }

    private fun shouldCancelSchemaUrlSuggestionPopup(): Boolean {
        val currentEvent = IdeEventQueue.getInstance().trueCurrentEvent as? MouseEvent ?: return true
        val eventComponent = currentEvent.component ?: return true
        return !SwingUtilities.isDescendingFrom(eventComponent, schemaUrlSearchField)
    }

    private fun resolveGroupSeparatorColor(optionsPanel: JComponent): Color {
        val groupSeparatorColor = findFirstSeparator(optionsPanel)?.foreground
        return groupSeparatorColor
            ?: UIManager.getColor("Group.separatorColor")
            ?: UIManager.getColor("Separator.foreground")
            ?: UIManager.getColor("Label.foreground")
    }

    private fun findFirstSeparator(component: Component): JSeparator? {
        if (component is JSeparator) {
            return component
        }
        if (component !is Container) {
            return null
        }

        component.components.forEach { childComponent ->
            val separator = findFirstSeparator(childComponent)
            if (separator != null) {
                return separator
            }
        }
        return null
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
