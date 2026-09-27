package com.livteam.jsoninja.ui.dialog.generateJson.schema

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.EditorTextField
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.editor.EditorTextFieldFactory
import com.livteam.jsoninja.ui.component.editor.setEditorTextAndRefreshCodeFolding
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationOutputFormat
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
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
    private val project: Project,
    private val initialConfig: JsonGenerationConfig
) {
    private lateinit var schemaEditor: EditorTextField
    private lateinit var schemaUrlSearchField: SearchTextField
    private lateinit var schemaUrlSuggestionList: JBList<SchemaUrlComboBoxItem>
    private lateinit var loadSchemaFromUrlButton: JButton
    private lateinit var schemaOutputCountField: JBTextField
    private val schemaPropertyModeComboBox = ComboBox(SchemaPropertyGenerationMode.entries.toTypedArray()).apply {
        selectedItem = initialConfig.schemaPropertyGenerationMode
        renderer = SimpleListCellRenderer.create("") { mode ->
            LocalizationBundle.message(
                when (mode) {
                    SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL -> "dialog.generate.json.schema.property.mode.required.optional"
                    SchemaPropertyGenerationMode.REQUIRED_ONLY -> "dialog.generate.json.schema.property.mode.required.only"
                    SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED -> "dialog.generate.json.schema.property.mode.required.optional.commented"
                }
            )
        }
    }
    private var preferredOutputFormat =
        if (initialConfig.isJson5) JsonGenerationOutputFormat.JSON5 else JsonGenerationOutputFormat.JSON
    private val schemaOutputFormatComboBox = ComboBox(JsonGenerationOutputFormat.entries.toTypedArray()).apply {
        selectedItem = preferredOutputFormat
    }
    private lateinit var outputFormatComment: JEditorPane
    private lateinit var outputFormatCommentRow: Row
    private var schemaUrlSuggestionPopup: JBPopup? = null
    private var selectedSchemaStoreCatalogItem: SchemaStoreCatalogItem? = null
    private var hasPendingSchemaStoreSelectionLoad = false
    private var schemaUrlSuggestionItems: List<SchemaUrlComboBoxItem> = emptyList()
    private var isUpdatingSchemaUrlEditorText = false
    private var isDisposed = false
    private var shouldShowSchemaUrlSuggestions = false

    private var onSchemaUrlInputChangedCallback: (() -> Unit)? = null
    private var onLoadSchemaFromUrlRequestedCallback: (() -> Unit)? = null
    private var onSchemaSourceReplacedCallback: (() -> Unit)? = null
    private var onOptionsChangedCallback: (() -> Unit)? = null

    val component: DialogPanel = createComponent()

    fun setOnOptionsChanged(callback: () -> Unit) {
        onOptionsChangedCallback = callback
    }

    fun setActive(isActive: Boolean) {
        if (!isActive) {
            hideSchemaUrlSuggestionPopup()
        }
    }

    fun registerValidators(parentDisposable: Disposable) {
        component.registerValidators(parentDisposable)
    }

    fun setOnSchemaUrlInputChanged(callback: () -> Unit) {
        onSchemaUrlInputChangedCallback = callback
    }

    fun setOnLoadSchemaFromUrlRequested(callback: () -> Unit) {
        onLoadSchemaFromUrlRequestedCallback = callback
    }

    fun setOnSchemaSourceReplaced(callback: () -> Unit) {
        onSchemaSourceReplacedCallback = callback
    }

    fun getSchemaText(): String = schemaEditor.text

    fun getSchemaInputComponent(): JComponent = schemaEditor

    fun getSchemaOutputCountText(): String = schemaOutputCountField.text

    fun getSchemaOutputCountField(): JComponent = schemaOutputCountField

    fun getSchemaUrlInputComponent(): JComponent = schemaUrlSearchField

    fun getSchemaPropertyGenerationMode(): SchemaPropertyGenerationMode =
        schemaPropertyModeComboBox.selectedItem as SchemaPropertyGenerationMode

    fun isJson5Selected(): Boolean = schemaOutputFormatComboBox.selectedItem == JsonGenerationOutputFormat.JSON5

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

    fun hasPendingSchemaStoreSelectionLoad(): Boolean = hasPendingSchemaStoreSelectionLoad

    fun markSchemaStoreSelectionLoaded() {
        if (selectedSchemaStoreCatalogItem != null) {
            hasPendingSchemaStoreSelectionLoad = false
        }
    }

    fun setSchemaEditorText(schemaText: String) {
        setEditorTextAndRefreshCodeFolding(project, schemaEditor, schemaText)
    }

    fun setLoadSchemaFromUrlButtonEnabled(isEnabled: Boolean) {
        loadSchemaFromUrlButton.isEnabled = isEnabled
        loadSchemaFromUrlButton.text = LocalizationBundle.message(
            if (isEnabled) "dialog.generate.json.schema.url.load.button" else "dialog.generate.json.schema.url.loading"
        )
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

        val selectedCatalogItem = selectedSchemaStoreCatalogItem
        if (selectedCatalogItem != null && !containsCatalogEntryByUrl(selectedCatalogItem.url)) {
            clearSelectedSchemaStoreCatalogItem()
        }

        val editorComponent = schemaUrlSearchField.textEditor
        if (editorComponent.text != editorText) {
            setSchemaUrlEditorText(editorText)
            val activeSelectedCatalogItem = selectedSchemaStoreCatalogItem
            if (activeSelectedCatalogItem != null &&
                editorText.isNotBlank() &&
                editorText != activeSelectedCatalogItem.name
            ) {
                clearSelectedSchemaStoreCatalogItem()
            }
        }

        if (showPopupWhenAvailable && shouldShowSchemaUrlSuggestions &&
            schemaUrlSearchField.textEditor.isFocusOwner && schemaUrlSuggestionItems.isNotEmpty()
        ) {
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

    private fun createComponent(): DialogPanel {
        schemaUrlSearchField = createSchemaUrlSearchField()
        schemaUrlSuggestionList = createSchemaUrlSuggestionList()
        schemaEditor = createSchemaEditor()
        schemaEditor.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun beforeDocumentChange(event: com.intellij.openapi.editor.event.DocumentEvent) {
                val selection = schemaEditor.editor?.selectionModel
                val isEntireDocumentSelected = selection?.hasSelection() == true &&
                    selection.selectionStart == 0 && selection.selectionEnd == event.document.textLength
                if (event.isWholeTextReplaced || isEntireDocumentSelected) {
                    onSchemaSourceReplacedCallback?.invoke()
                }
            }

            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                if (event.document.textLength == 0) {
                    onSchemaSourceReplacedCallback?.invoke()
                }
            }
        })

        val result = panel {
            row {
                cell(schemaUrlSearchField)
                    .label(LocalizationBundle.message("dialog.generate.json.schema.url.label"), LabelPosition.TOP)
                    .align(AlignX.FILL)
                    .resizableColumn()
                loadSchemaFromUrlButton = button(LocalizationBundle.message("dialog.generate.json.schema.url.load.button")) {
                    onLoadSchemaFromUrlRequestedCallback?.invoke()
                }.component
            }

            row {
                label(LocalizationBundle.message("dialog.generate.json.schema.editor.label"))
                    .applyToComponent { labelFor = schemaEditor }
                comment(LocalizationBundle.message("dialog.generate.json.schema.editor.comment"))
                    .align(AlignX.RIGHT)
            }.topGap(TopGap.SMALL)

            row {
                cell(schemaEditor).align(Align.FILL).resizableColumn()
            }.resizableRow()

            separator().topGap(TopGap.SMALL)
            panel {
                row(LocalizationBundle.message("dialog.generate.json.schema.output.count")) {
                    schemaOutputCountField = intTextField(1..100, keyboardStep = 1)
                        .applyToComponent { text = initialConfig.schemaOutputCount.toString() }
                        .component
                    label(LocalizationBundle.message("dialog.generate.json.range", 1, 100))
                        .applyToComponent { foreground = UIUtil.getContextHelpForeground() }
                        .gap(RightGap.COLUMNS)
                    label(LocalizationBundle.message("dialog.generate.json.output.format"))
                        .applyToComponent { labelFor = schemaOutputFormatComboBox }
                    cell(schemaOutputFormatComboBox)
                }
                row(LocalizationBundle.message("dialog.generate.json.schema.property.mode.label")) {
                    cell(schemaPropertyModeComboBox)
                }
                outputFormatCommentRow = row {
                    outputFormatComment = comment(LocalizationBundle.message("dialog.generate.json.output.json5.comment"))
                        .component
                }
            }
        }.apply {
            border = JBUI.Borders.empty(16, 12, 12, 12)
        }

        schemaPropertyModeComboBox.addActionListener {
            updateOutputFormatState()
            onOptionsChangedCallback?.invoke()
        }
        schemaOutputFormatComboBox.addActionListener {
            if (schemaOutputFormatComboBox.isEnabled) {
                preferredOutputFormat = schemaOutputFormatComboBox.selectedItem as JsonGenerationOutputFormat
            }
            updateOutputFormatComment()
            onOptionsChangedCallback?.invoke()
        }
        schemaOutputCountField.document.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                onOptionsChangedCallback?.invoke()
            }
        })
        updateOutputFormatState()
        return result
    }

    private fun updateOutputFormatState() {
        val isCommentedMode = getSchemaPropertyGenerationMode() == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
        schemaOutputFormatComboBox.isEnabled = !isCommentedMode
        schemaOutputFormatComboBox.selectedItem = if (isCommentedMode) JsonGenerationOutputFormat.JSON5 else preferredOutputFormat
        updateOutputFormatComment()
    }

    private fun updateOutputFormatComment() {
        val commentKey = if (getSchemaPropertyGenerationMode() == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED) {
            "dialog.generate.json.output.json5.required"
        } else {
            "dialog.generate.json.output.json5.comment"
        }
        outputFormatComment.text = LocalizationBundle.message(commentKey)
        outputFormatCommentRow.visible(isJson5Selected())
    }

    private fun createSchemaUrlSearchField(): SearchTextField {
        val searchTextField = SearchTextField()
        searchTextField.textEditor.emptyText.text = LocalizationBundle.message("dialog.generate.json.schema.url.placeholder")
        searchTextField.textEditor.accessibleContext.accessibleName =
            LocalizationBundle.message("dialog.generate.json.schema.url.label")
        attachSchemaUrlEditorDocumentListener(searchTextField)
        searchTextField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(keyEvent: KeyEvent) {
                when (keyEvent.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        shouldShowSchemaUrlSuggestions = true
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
        searchTextField.textEditor.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(mouseEvent: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(mouseEvent)) return
                shouldShowSchemaUrlSuggestions = true
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
        shouldShowSchemaUrlSuggestions = schemaUrlSearchField.textEditor.isFocusOwner && editorText.isNotBlank()
        if (!shouldShowSchemaUrlSuggestions) {
            hideSchemaUrlSuggestionPopup()
        }
        val selectedCatalogItem = selectedSchemaStoreCatalogItem
        if (selectedCatalogItem != null && editorText.isNotBlank() && editorText != selectedCatalogItem.name) {
            clearSelectedSchemaStoreCatalogItem()
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
        hasPendingSchemaStoreSelectionLoad = true
        setSchemaUrlEditorText(schemaUrlSuggestionItem.schemaStoreCatalogItem.name)
        hideSchemaUrlSuggestionPopup()
        return true
    }

    private fun clearSelectedSchemaStoreCatalogItem() {
        selectedSchemaStoreCatalogItem = null
        hasPendingSchemaStoreSelectionLoad = false
    }

    private fun containsCatalogEntryByUrl(schemaUrl: String): Boolean {
        return schemaUrlSuggestionItems.any { schemaUrlSuggestionItem ->
            schemaUrlSuggestionItem is SchemaUrlComboBoxItem.CatalogEntry &&
                schemaUrlSuggestionItem.schemaStoreCatalogItem.url == schemaUrl
        }
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
            return
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

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(popupContent, schemaUrlSuggestionList)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setCancelOnWindowDeactivation(true)
            .setCancelKeyEnabled(true)
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(false)
            .createPopup()
        schemaUrlSuggestionPopup = popup
        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (schemaUrlSuggestionPopup === event.asPopup()) {
                    schemaUrlSuggestionPopup = null
                    shouldShowSchemaUrlSuggestions = false
                }
            }
        })
        val anchorPoint = RelativePoint(popupAnchorComponent, Point(0, popupAnchorComponent.height))
        popup.show(anchorPoint)
    }

    private fun hideSchemaUrlSuggestionPopup() {
        shouldShowSchemaUrlSuggestions = false
        val popup = schemaUrlSuggestionPopup
        schemaUrlSuggestionPopup = null
        popup?.cancel()
    }

    private fun isSchemaUrlSuggestionPopupVisible(): Boolean {
        return schemaUrlSuggestionPopup?.isVisible == true
    }

    private fun createSchemaEditor(): EditorTextField {
        val initialSchemaText = initialConfig.schemaText.ifBlank {
            LocalizationBundle.message("dialog.generate.json.schema.placeholder")
        }
        return EditorTextFieldFactory.createJsonField(
            project,
            fileExtension = "json",
            initialText = initialSchemaText,
            placeholderText = LocalizationBundle.message("dialog.generate.json.schema.editor.comment"),
            preferredSize = JBUI.size(580, 280),
            shouldShowHorizontalScrollbar = true,
            shouldShowVerticalScrollbar = true,
            configureEditorSettings = {
                applySchemaEditorSettings()
            },
        ).apply {
            minimumSize = JBUI.size(360, 180)
            accessibleContext.accessibleName = LocalizationBundle.message("dialog.generate.json.schema.editor.label")
        }
    }

    private fun EditorSettings.applySchemaEditorSettings() {
        isLineNumbersShown = true
    }
}
