package com.livteam.jsoninja.ui.dialog.generateJson.schema

import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.editor.EditorTextFieldFactory
import com.livteam.jsoninja.ui.component.editor.setEditorTextAndRefreshCodeFolding
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationOutputFormat
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.DefaultListModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import com.intellij.ui.awt.RelativePoint

class GenerateSchemaJsonTabView(
    private val project: Project,
    private val initialConfig: JsonGenerationConfig,
    private val initialSchemaUrlText: String = "",
) {
    private val viewDisposable = Disposer.newDisposable("GenerateSchemaJsonTabView")
    private lateinit var schemaEditor: EditorTextField
    private lateinit var schemaUrlSearchField: SearchTextField
    private lateinit var schemaUrlSuggestionList: JBList<SchemaUrlComboBoxItem>
    private lateinit var loadSchemaFromUrlButton: JButton
    private lateinit var urlErrorRow: Row
    private var isUrlErrorVisible = false
    private val urlLoadingIcon = AsyncProcessIcon("JSONinjaSchemaUrlLoad").apply {
        isVisible = false
        suspend()
    }
    private val urlErrorTextArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        foreground = NamedColorUtil.getErrorForeground()
        border = JBUI.Borders.empty(2, 0)
    }
    private lateinit var schemaOutputCountField: JBTextField
    private val schemaPropertyModeComboBox = ComboBox(SchemaPropertyGenerationMode.entries.toTypedArray()).apply {
        selectedItem = initialConfig.schemaPropertyGenerationMode
        renderer = textListCellRenderer<SchemaPropertyGenerationMode?> { mode ->
            when (mode) {
                SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL ->
                    LocalizationBundle.message("dialog.generate.json.schema.property.mode.required.optional")
                SchemaPropertyGenerationMode.REQUIRED_ONLY ->
                    LocalizationBundle.message("dialog.generate.json.schema.property.mode.required.only")
                SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED ->
                    LocalizationBundle.message("dialog.generate.json.schema.property.mode.required.optional.commented")
                null -> ""
            }
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
    private var onLoadSchemaFromFileRequestedCallback: (() -> Unit)? = null
    private var onLoadSchemaFromTabRequestedCallback: (() -> Unit)? = null
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

    fun setOnLoadSchemaFromFileRequested(callback: () -> Unit) {
        onLoadSchemaFromFileRequestedCallback = callback
    }

    fun setOnLoadSchemaFromTabRequested(callback: () -> Unit) {
        onLoadSchemaFromTabRequestedCallback = callback
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

    fun navigateToOffset(offset: Int) {
        val editor = schemaEditor.editor ?: return
        if (editor.isDisposed) return
        val clampedOffset = offset.coerceIn(0, editor.document.textLength)
        editor.caretModel.moveToOffset(clampedOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    fun setUrlLoading(isLoading: Boolean) {
        loadSchemaFromUrlButton.isEnabled = true
        loadSchemaFromUrlButton.text = LocalizationBundle.message(
            if (isLoading) "dialog.generate.json.schema.url.cancel" else "dialog.generate.json.schema.url.load.button"
        )
        if (isLoading) {
            urlLoadingIcon.isVisible = true
            urlLoadingIcon.resume()
        } else {
            urlLoadingIcon.suspend()
            urlLoadingIcon.isVisible = false
        }
    }

    fun showUrlError(errorMessage: String) {
        urlErrorTextArea.text = errorMessage
        urlErrorTextArea.caretPosition = 0
        isUrlErrorVisible = true
        urlErrorRow.visible(true)
        onOptionsChangedCallback?.invoke()
    }

    fun clearUrlError() {
        if (urlErrorTextArea.text.isEmpty() && !isUrlErrorVisible) return
        urlErrorTextArea.text = ""
        isUrlErrorVisible = false
        urlErrorRow.visible(false)
        onOptionsChangedCallback?.invoke()
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
        urlLoadingIcon.suspend()
        Disposer.dispose(viewDisposable)
        hideSchemaUrlSuggestionPopup()
        (schemaEditor as? Disposable)?.let { disposableEditor ->
            Disposer.dispose(disposableEditor)
        }
    }

    private fun createComponent(): DialogPanel {
        Disposer.register(viewDisposable, urlLoadingIcon)
        schemaUrlSearchField = createSchemaUrlSearchField()
        if (initialSchemaUrlText.isNotBlank()) {
            setSchemaUrlEditorText(initialSchemaUrlText)
        }
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

        val schemaSourceToolbar = createSchemaSourceToolbar()
        val urlErrorPanel = createUrlErrorPanel()

        val result = panel {
            row {
                cell(schemaUrlSearchField)
                    .label(LocalizationBundle.message("dialog.generate.json.schema.url.label"), LabelPosition.TOP)
                    .align(AlignX.FILL)
                    .resizableColumn()
                cell(urlLoadingIcon)
                loadSchemaFromUrlButton = button(LocalizationBundle.message("dialog.generate.json.schema.url.load.button")) {
                    onLoadSchemaFromUrlRequestedCallback?.invoke()
                }.component
            }

            urlErrorRow = row {
                cell(urlErrorPanel).align(AlignX.FILL).resizableColumn()
            }.visible(false)

            row {
                label(LocalizationBundle.message("dialog.generate.json.schema.editor.label"))
                    .applyToComponent { labelFor = schemaEditor }
                cell(schemaSourceToolbar).align(AlignX.RIGHT)
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
                        .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }
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
        subscribeThemeChanges()
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
        searchTextField.textEditor.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            "jsoninja.schemaUrl.enter"
        )
        searchTextField.textEditor.actionMap.put("jsoninja.schemaUrl.enter", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) {
                handleSchemaUrlEnter()
            }
        })
        searchTextField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(keyEvent: KeyEvent) {
                when (keyEvent.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        moveSuggestionSelection(1)
                        keyEvent.consume()
                    }

                    KeyEvent.VK_UP -> {
                        moveSuggestionSelection(-1)
                        keyEvent.consume()
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
                renderedComponent.foreground = JBUI.CurrentTheme.Label.disabledForeground()
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
        onLoadSchemaFromUrlRequestedCallback?.invoke()
        return true
    }

    private fun handleSchemaUrlEnter(): Boolean {
        if (isSchemaUrlSuggestionPopupVisible() && applySelectedSchemaUrlSuggestion()) {
            return true
        }
        if (getSchemaUrlInputText().isNotBlank()) {
            onLoadSchemaFromUrlRequestedCallback?.invoke()
            return true
        }
        return false
    }

    private fun moveSuggestionSelection(delta: Int) {
        shouldShowSchemaUrlSuggestions = true
        val selectableIndices = schemaUrlSuggestionItems.indices.filter { index ->
            schemaUrlSuggestionItems[index] is SchemaUrlComboBoxItem.CatalogEntry
        }
        if (selectableIndices.isEmpty()) {
            if (schemaUrlSuggestionItems.isNotEmpty() && !isSchemaUrlSuggestionPopupVisible()) {
                showSchemaUrlSuggestionPopup()
            }
            schemaUrlSuggestionList.clearSelection()
            return
        }

        if (!isSchemaUrlSuggestionPopupVisible()) {
            showSchemaUrlSuggestionPopup()
            val initialIndex = if (delta >= 0) selectableIndices.first() else selectableIndices.last()
            schemaUrlSuggestionList.selectedIndex = initialIndex
            schemaUrlSuggestionList.ensureIndexIsVisible(initialIndex)
            return
        }

        val currentPosition = selectableIndices.indexOf(schemaUrlSuggestionList.selectedIndex)
        val nextPosition = if (currentPosition < 0) {
            if (delta >= 0) 0 else selectableIndices.lastIndex
        } else {
            Math.floorMod(currentPosition + delta, selectableIndices.size)
        }
        val nextIndex = selectableIndices[nextPosition]
        schemaUrlSuggestionList.selectedIndex = nextIndex
        schemaUrlSuggestionList.ensureIndexIsVisible(nextIndex)
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
            shouldApplyEditorColors = true,
            shouldApplyHighlighter = true,
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

    private fun subscribeThemeChanges() {
        val connection = ApplicationManager.getApplication().messageBus.connect(viewDisposable)
        connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { refreshThemeAppearance() })
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { refreshThemeAppearance() })
    }

    private fun createSchemaSourceToolbar(): JComponent {
        val loadFromFileAction = object : DumbAwareAction(
            LocalizationBundle.messagePointer("dialog.generate.json.schema.load.file"),
            LocalizationBundle.messagePointer("dialog.generate.json.schema.load.file"),
            AllIcons.Actions.MenuOpen,
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                onLoadSchemaFromFileRequestedCallback?.invoke()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val loadFromTabAction = object : DumbAwareAction(
            LocalizationBundle.messagePointer("dialog.generate.json.schema.load.tab"),
            LocalizationBundle.messagePointer("dialog.generate.json.schema.load.tab"),
            AllIcons.Actions.Download,
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                onLoadSchemaFromTabRequestedCallback?.invoke()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val toolbar = ActionManager.getInstance().createActionToolbar(
            ActionPlaces.EDITOR_TOOLBAR,
            DefaultActionGroup(loadFromFileAction, loadFromTabAction),
            true,
        )
        toolbar.targetComponent = schemaEditor
        toolbar.component.isOpaque = false
        toolbar.component.border = JBUI.Borders.empty()
        return toolbar.component
    }

    private fun createUrlErrorPanel(): JComponent {
        val closeAction = object : DumbAwareAction(
            LocalizationBundle.messagePointer("dialog.load.json.api.button.close"),
            LocalizationBundle.messagePointer("dialog.load.json.api.button.close"),
            AllIcons.Actions.Close,
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                clearUrlError()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val closeToolbar = ActionManager.getInstance().createActionToolbar(
            ActionPlaces.EDITOR_TOOLBAR,
            DefaultActionGroup(closeAction),
            true,
        ).apply {
            targetComponent = urlErrorTextArea
            component.isOpaque = false
            component.border = JBUI.Borders.empty()
        }
        val scrollPane = JBScrollPane(urlErrorTextArea).apply {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
            isOpaque = false
            viewport.isOpaque = false
            preferredSize = Dimension(0, JBUI.scale(48))
        }
        return JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(scrollPane, BorderLayout.CENTER)
            add(closeToolbar.component, BorderLayout.EAST)
        }
    }

    private fun refreshThemeAppearance() {
        if (isDisposed || project.isDisposed) return
        val applyChanges = {
            if (!isDisposed && !project.isDisposed) {
                urlErrorTextArea.foreground = NamedColorUtil.getErrorForeground()
                val editor = schemaEditor.editor as? EditorEx
                if (editor != null && !editor.isDisposed) {
                    val scheme = EditorColorsManager.getInstance().globalScheme
                    editor.colorsScheme = editor.createBoundColorSchemeDelegate(scheme)
                    editor.backgroundColor = scheme.defaultBackground
                    editor.highlighter = HighlighterFactory.createHighlighter(project, schemaEditor.fileType)
                }
                schemaUrlSuggestionList.repaint()
            }
        }
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            applyChanges()
        } else {
            app.invokeLater(applyChanges, ModalityState.stateForComponent(component))
        }
    }

    private fun EditorSettings.applySchemaEditorSettings() {
        isLineNumbersShown = true
    }
}
