package com.livteam.jsoninja.ui.dialog.loadJson

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.ide.ui.LafManagerListener
import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.EditorTextField
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.editor.EditorTextFieldFactory
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiAuthorizationType
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiHeaderEntry
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiLoadRequest
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiRequestMethod
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent as SwingDocumentEvent
import javax.swing.table.DefaultTableModel

class LoadJsonFromApiDialogView(
    private val project: Project
) {
    private val viewDisposable = Disposer.newDisposable("LoadJsonFromApiDialogView")
    @Volatile
    private var isDisposed = false
    private var isApplyingProgrammatically = false
    private var isErrorVisible = false

    private lateinit var rootPanel: JPanel
    private lateinit var requestMethodComboBox: ComboBox<ApiRequestMethod>
    private lateinit var requestUrlComboBox: ComboBox<String>
    private lateinit var requestUrlTextField: JBTextField
    private lateinit var authorizationTypeComboBox: ComboBox<ApiAuthorizationType>
    private lateinit var basicUsernameTextField: JBTextField
    private lateinit var basicPasswordTextField: JBPasswordField
    private lateinit var bearerTokenField: JBPasswordField
    private lateinit var headersTableModel: DefaultTableModel
    private lateinit var headersTable: JBTable
    private lateinit var requestBodyEditorTextField: EditorTextField
    private lateinit var requestRow: Row
    private lateinit var authorizationRow: Row
    private lateinit var basicUsernameRow: Row
    private lateinit var basicPasswordRow: Row
    private lateinit var bearerTokenRow: Row
    private lateinit var headersRow: Row
    private lateinit var requestBodyRow: Row
    private lateinit var errorRow: Row

    private val loadingIcon = AsyncProcessIcon("JSONinjaApiLoad").apply {
        isVisible = false
        suspend()
    }
    private val summaryLabel = JBLabel().apply {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }
    private val errorTextArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        foreground = NamedColorUtil.getErrorForeground()
        border = JBUI.Borders.empty(4, 0)
    }
    private val openErrorResponseButton = JButton(LocalizationBundle.message("dialog.load.json.api.open.error.tab")).apply {
        isVisible = false
    }

    private var currentBodyFileType: FileType? = null
    private var onInputsChangedCallback: (() -> Unit)? = null
    private var onLayoutChangedCallback: (() -> Unit)? = null
    private var onHistoryUrlSelectedCallback: ((String) -> Unit)? = null
    private var onCurlPastedCallback: ((String) -> Unit)? = null
    private var onOpenErrorResponseRequestedCallback: (() -> Unit)? = null

    val component: JComponent by lazy { createComponent() }

    fun setOnInputsChanged(callback: () -> Unit) {
        onInputsChangedCallback = callback
    }

    fun setOnLayoutChanged(callback: () -> Unit) {
        onLayoutChangedCallback = callback
    }

    fun setOnHistoryUrlSelected(callback: (String) -> Unit) {
        onHistoryUrlSelectedCallback = callback
    }

    fun setOnCurlPasted(callback: (String) -> Unit) {
        onCurlPastedCallback = callback
    }

    fun setOnOpenErrorResponseRequested(callback: () -> Unit) {
        onOpenErrorResponseRequestedCallback = callback
    }

    fun getRequestUrlComponent(): JComponent = requestUrlTextField

    fun getBasicUsernameComponent(): JComponent = basicUsernameTextField

    fun getBasicPasswordComponent(): JComponent = basicPasswordTextField

    fun getBearerTokenComponent(): JComponent = bearerTokenField

    fun getSelectedRequestMethod(): ApiRequestMethod {
        return requestMethodComboBox.selectedItem as? ApiRequestMethod ?: ApiRequestMethod.GET
    }

    fun getRequestUrlText(): String {
        return requestUrlTextField.text.trim()
    }

    fun getSelectedAuthorizationType(): ApiAuthorizationType {
        return authorizationTypeComboBox.selectedItem as? ApiAuthorizationType ?: ApiAuthorizationType.NONE
    }

    fun getBasicUsernameText(): String {
        return basicUsernameTextField.text.trim()
    }

    fun getBasicPasswordText(): String {
        return String(basicPasswordTextField.password).trim()
    }

    fun getBearerTokenText(): String {
        return String(bearerTokenField.password).trim()
    }

    fun getRequestBodyText(): String {
        return requestBodyEditorTextField.text
    }

    fun getHeaders(): List<ApiHeaderEntry> {
        if (headersTable.isEditing) {
            headersTable.cellEditor?.stopCellEditing()
        }
        val result = mutableListOf<ApiHeaderEntry>()
        for (row in 0 until headersTableModel.rowCount) {
            val name = headersTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
            val value = headersTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                result += ApiHeaderEntry(name, value)
            }
        }
        return result
    }

    fun setHistoryUrls(urls: List<String>) {
        if (!::requestUrlComboBox.isInitialized) return
        val currentText = requestUrlTextField.text
        isApplyingProgrammatically = true
        try {
            val model = DefaultComboBoxModel<String>()
            urls.forEach(model::addElement)
            requestUrlComboBox.model = model
            requestUrlComboBox.selectedItem = currentText
            requestUrlTextField.text = currentText
        } finally {
            isApplyingProgrammatically = false
        }
    }

    fun applyRequest(request: ApiLoadRequest) {
        isApplyingProgrammatically = true
        try {
            requestMethodComboBox.selectedItem = request.requestMethod
            requestUrlTextField.text = request.requestUrl
            authorizationTypeComboBox.selectedItem = request.authorizationType
            basicUsernameTextField.text = request.basicUsername
            basicPasswordTextField.text = request.basicPassword
            bearerTokenField.text = request.bearerToken
            setHeaders(request.headers)
            requestBodyEditorTextField.text = request.requestBodyText
            updateAuthorizationInputVisibility()
            updateRequestBodyVisibility()
            updateRequestBodyHighlightByHeuristic()
        } finally {
            isApplyingProgrammatically = false
        }
    }

    fun setSecretCredentials(basicPassword: String, bearerToken: String) {
        isApplyingProgrammatically = true
        try {
            if (basicPassword.isNotEmpty() || getSelectedAuthorizationType() == ApiAuthorizationType.BASIC) {
                basicPasswordTextField.text = basicPassword
            }
            if (bearerToken.isNotEmpty() || getSelectedAuthorizationType() == ApiAuthorizationType.BEARER) {
                bearerTokenField.text = bearerToken
            }
        } finally {
            isApplyingProgrammatically = false
        }
    }

    fun setLoading(isLoading: Boolean) {
        listOf(requestRow, authorizationRow, basicUsernameRow, basicPasswordRow, bearerTokenRow, headersRow, requestBodyRow)
            .forEach { it.enabled(!isLoading) }
        headersTable.isEnabled = !isLoading
        if (isLoading) {
            loadingIcon.isVisible = true
            loadingIcon.resume()
        } else {
            loadingIcon.suspend()
            loadingIcon.isVisible = false
        }
    }

    fun setSummary(summary: String) {
        summaryLabel.text = summary
    }

    fun showRequestError(message: String, canOpenResponseInTab: Boolean) {
        errorTextArea.text = message
        errorTextArea.caretPosition = 0
        openErrorResponseButton.isVisible = canOpenResponseInTab
        isErrorVisible = true
        errorRow.visible(true)
        onLayoutChangedCallback?.invoke()
    }

    fun clearRequestError() {
        if (!isErrorVisible && errorTextArea.text.isEmpty()) return
        errorTextArea.text = ""
        openErrorResponseButton.isVisible = false
        isErrorVisible = false
        errorRow.visible(false)
        onLayoutChangedCallback?.invoke()
    }

    fun dispose() {
        isDisposed = true
        loadingIcon.suspend()
        Disposer.dispose(viewDisposable)
        if (!::requestBodyEditorTextField.isInitialized) return
        (requestBodyEditorTextField as? Disposable)?.let { editorDisposable ->
            Disposer.dispose(editorDisposable)
        }
    }

    private fun setHeaders(headers: List<ApiHeaderEntry>) {
        if (headersTable.isEditing) {
            headersTable.cellEditor?.cancelCellEditing()
        }
        headersTableModel.rowCount = 0
        headers.forEach { entry ->
            headersTableModel.addRow(arrayOf(entry.name, entry.value))
        }
    }

    private fun createComponent(): JComponent {
        Disposer.register(viewDisposable, loadingIcon)
        requestMethodComboBox = ComboBox(ApiRequestMethod.entries.toTypedArray()).apply {
            renderer = textListCellRenderer<ApiRequestMethod?> { value -> value?.name ?: "" }
        }
        requestUrlComboBox = ComboBox<String>().apply {
            isEditable = true
        }
        requestUrlTextField = (requestUrlComboBox.editor.editorComponent as? JBTextField)
            ?: JBTextField().also { fallbackField ->
                requestUrlComboBox.editor = object : javax.swing.plaf.basic.BasicComboBoxEditor() {
                    override fun createEditorComponent() = fallbackField
                }
            }
        requestUrlTextField.emptyText.text = LocalizationBundle.message("dialog.load.json.api.url.placeholder")

        authorizationTypeComboBox = ComboBox(ApiAuthorizationType.entries.toTypedArray()).apply {
            renderer = textListCellRenderer<ApiAuthorizationType?> { value ->
                when (value) {
                    ApiAuthorizationType.NONE -> LocalizationBundle.message("dialog.load.json.api.auth.none")
                    ApiAuthorizationType.BASIC -> LocalizationBundle.message("dialog.load.json.api.auth.basic")
                    ApiAuthorizationType.BEARER -> LocalizationBundle.message("dialog.load.json.api.auth.bearer")
                    null -> ""
                }
            }
        }
        basicUsernameTextField = JBTextField()
        basicPasswordTextField = JBPasswordField()
        bearerTokenField = JBPasswordField()
        val headersPanel = createHeadersPanel()
        requestBodyEditorTextField = createRequestBodyEditorTextField()
        val errorPanel = createErrorPanel()

        val configPanel = panel {
            requestRow = row {
                cell(requestMethodComboBox)
                    .label(LocalizationBundle.message("dialog.load.json.api.method"), LabelPosition.TOP)
                    .gap(RightGap.SMALL)
                cell(requestUrlComboBox)
                    .label(LocalizationBundle.message("dialog.load.json.api.url"), LabelPosition.TOP)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }

            errorRow = row {
                cell(errorPanel).align(AlignX.FILL).resizableColumn()
            }.visible(false)

            separator().topGap(TopGap.SMALL)

            authorizationRow = row(LocalizationBundle.message("dialog.load.json.api.auth.type")) {
                cell(authorizationTypeComboBox)
            }
            basicUsernameRow = row(LocalizationBundle.message("dialog.load.json.api.auth.username")) {
                cell(basicUsernameTextField).align(AlignX.FILL).resizableColumn()
            }.visible(false)
            basicPasswordRow = row(LocalizationBundle.message("dialog.load.json.api.auth.password")) {
                cell(basicPasswordTextField).align(AlignX.FILL).resizableColumn()
            }.visible(false)
            bearerTokenRow = row(LocalizationBundle.message("dialog.load.json.api.auth.token")) {
                cell(bearerTokenField).align(AlignX.FILL).resizableColumn()
            }.visible(false)

            headersRow = row {
                cell(headersPanel)
                    .label(LocalizationBundle.message("dialog.load.json.api.headers"), LabelPosition.TOP)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }.topGap(TopGap.SMALL)

            requestBodyRow = row {
                cell(requestBodyEditorTextField)
                    .label(LocalizationBundle.message("dialog.load.json.api.body"), LabelPosition.TOP)
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow().topGap(TopGap.SMALL).visible(false)
        }.apply {
            border = JBUI.Borders.empty(16, 12, 12, 12)
        }

        val statusRowPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            isOpaque = false
            add(loadingIcon)
            add(summaryLabel)
        }

        val footer = panel {
            separator()
            row { cell(statusRowPanel) }
        }.apply {
            border = JBUI.Borders.empty(0, 12, 0, 12)
        }

        rootPanel = object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                val size = super.getPreferredSize()
                return Dimension(maxOf(size.width, JBUI.scale(620)), size.height)
            }

            override fun getMinimumSize(): Dimension {
                val size = super.getMinimumSize()
                return Dimension(maxOf(size.width, JBUI.scale(560)), size.height)
            }
        }.apply {
            add(configPanel, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }

        attachInputListeners()
        subscribeThemeChanges()
        updateRequestBodyHighlightByHeuristic()

        return rootPanel
    }

    private fun createHeadersPanel(): JComponent {
        headersTableModel = DefaultTableModel(
            arrayOf(
                LocalizationBundle.message("dialog.load.json.api.header.name"),
                LocalizationBundle.message("dialog.load.json.api.header.value"),
            ),
            0,
        )
        headersTable = JBTable(headersTableModel).apply {
            setShowGrid(false)
            putClientProperty("terminateEditOnFocusLost", true)
            emptyText.text = ""
        }
        headersTableModel.addTableModelListener {
            if (!isApplyingProgrammatically) {
                onInputsChangedCallback?.invoke()
            }
        }
        return ToolbarDecorator.createDecorator(headersTable)
            .disableUpDownActions()
            .setAddAction {
                headersTableModel.addRow(arrayOf("", ""))
                val newRow = headersTableModel.rowCount - 1
                headersTable.setRowSelectionInterval(newRow, newRow)
                headersTable.editCellAt(newRow, 0)
                headersTable.editorComponent?.requestFocusInWindow()
            }
            .setRemoveAction {
                if (headersTable.isEditing) {
                    headersTable.cellEditor?.cancelCellEditing()
                }
                headersTable.selectedRows.sortedDescending().forEach { rowIndex ->
                    if (rowIndex in 0 until headersTableModel.rowCount) {
                        headersTableModel.removeRow(rowIndex)
                    }
                }
                onInputsChangedCallback?.invoke()
            }
            .createPanel()
            .apply {
                preferredSize = Dimension(0, JBUI.scale(96))
                minimumSize = Dimension(0, JBUI.scale(80))
            }
    }

    private fun createErrorPanel(): JComponent {
        openErrorResponseButton.addActionListener {
            onOpenErrorResponseRequestedCallback?.invoke()
        }
        val scrollPane = JBScrollPane(errorTextArea).apply {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
            isOpaque = false
            viewport.isOpaque = false
            preferredSize = Dimension(0, JBUI.scale(64))
        }
        val buttonWrapper = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            add(openErrorResponseButton)
        }
        return JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(8), JBUI.scale(4))).apply {
            isOpaque = false
            add(scrollPane, BorderLayout.CENTER)
            add(buttonWrapper, BorderLayout.SOUTH)
        }
    }

    private fun createRequestBodyEditorTextField(): EditorTextField {
        return EditorTextFieldFactory.createPlainTextField(
            project = project,
            preferredSize = JBUI.size(560, 200),
            placeholderText = LocalizationBundle.message("dialog.load.json.api.body.placeholder"),
            configureEditorSettings = {
                applyRequestBodyEditorSettings()
            },
            customizeEditor = {
                applyRequestBodyAppearance(this, detectFileTypeByRequestBodyContent(getRequestBodyText()))
            },
        ).apply {
            minimumSize = JBUI.size(0, 140)
        }
    }

    private fun EditorSettings.applyRequestBodyEditorSettings() {
        isLineNumbersShown = true
        isWhitespacesShown = false
        isCaretRowShown = true
        isUseSoftWraps = true
    }

    private fun attachInputListeners() {
        requestMethodComboBox.addActionListener {
            if (isApplyingProgrammatically) return@addActionListener
            updateRequestBodyVisibility()
            onInputsChangedCallback?.invoke()
        }

        requestUrlComboBox.addActionListener {
            if (isApplyingProgrammatically) return@addActionListener
            val selected = requestUrlComboBox.selectedItem as? String ?: return@addActionListener
            if (selected.isNotBlank() && requestUrlComboBox.selectedIndex >= 0) {
                onHistoryUrlSelectedCallback?.invoke(selected)
            }
        }

        authorizationTypeComboBox.addActionListener {
            if (isApplyingProgrammatically) return@addActionListener
            updateAuthorizationInputVisibility()
            onInputsChangedCallback?.invoke()
        }

        requestUrlTextField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: SwingDocumentEvent) {
                if (isApplyingProgrammatically) return
                val rawText = requestUrlTextField.text
                if (CurlCommandParser.looksLikeCurl(rawText)) {
                    SwingUtilities.invokeLater {
                        if (!isDisposed && !isApplyingProgrammatically && CurlCommandParser.looksLikeCurl(requestUrlTextField.text)) {
                            onCurlPastedCallback?.invoke(requestUrlTextField.text)
                        }
                    }
                    return
                }
                onInputsChangedCallback?.invoke()
            }
        })

        val inputListener = object : DocumentAdapter() {
            override fun textChanged(event: SwingDocumentEvent) {
                if (!isApplyingProgrammatically) {
                    onInputsChangedCallback?.invoke()
                }
            }
        }
        listOf(basicUsernameTextField, basicPasswordTextField, bearerTokenField)
            .forEach { it.document.addDocumentListener(inputListener) }

        requestBodyEditorTextField.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                updateRequestBodyHighlightByHeuristic()
                if (!isApplyingProgrammatically) {
                    onInputsChangedCallback?.invoke()
                }
            }
        })
    }

    private fun updateAuthorizationInputVisibility() {
        val selectedAuthorizationType = getSelectedAuthorizationType()
        basicUsernameRow.visible(selectedAuthorizationType == ApiAuthorizationType.BASIC)
        basicPasswordRow.visible(selectedAuthorizationType == ApiAuthorizationType.BASIC)
        bearerTokenRow.visible(selectedAuthorizationType == ApiAuthorizationType.BEARER)
        onLayoutChangedCallback?.invoke()
    }

    private fun updateRequestBodyVisibility() {
        val isRequestBodyVisible = getSelectedRequestMethod().supportsRequestBody
        if (requestBodyEditorTextField.isVisible == isRequestBodyVisible) return
        requestBodyRow.visible(isRequestBodyVisible)
        onLayoutChangedCallback?.invoke()
    }

    private fun subscribeThemeChanges() {
        val connection = ApplicationManager.getApplication().messageBus.connect(viewDisposable)
        connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { refreshThemeAppearance() })
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { refreshThemeAppearance() })
    }

    private fun refreshThemeAppearance() {
        if (isDisposed || project.isDisposed) return
        val applyChanges = {
            if (!isDisposed && !project.isDisposed) {
                summaryLabel.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                errorTextArea.foreground = NamedColorUtil.getErrorForeground()
                val bodyEditor = requestBodyEditorTextField.editor as? EditorEx
                if (bodyEditor != null && !bodyEditor.isDisposed) {
                    val fileType = currentBodyFileType ?: detectFileTypeByRequestBodyContent(getRequestBodyText())
                    applyRequestBodyAppearance(bodyEditor, fileType)
                }
            }
        }
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            applyChanges()
        } else {
            app.invokeLater(applyChanges, ModalityState.stateForComponent(rootPanel))
        }
    }

    private fun updateRequestBodyHighlightByHeuristic() {
        val detectedFileType = detectFileTypeByRequestBodyContent(getRequestBodyText())
        if (currentBodyFileType?.name == detectedFileType.name) {
            return
        }
        currentBodyFileType = detectedFileType

        val bodyEditor = requestBodyEditorTextField.editor as? EditorEx ?: return
        if (!bodyEditor.isDisposed) {
            applyRequestBodyAppearance(bodyEditor, detectedFileType)
        }
    }

    private fun applyRequestBodyAppearance(bodyEditor: EditorEx, detectedFileType: FileType) {
        currentBodyFileType = detectedFileType
        val colorsScheme = EditorColorsManager.getInstance().globalScheme
        bodyEditor.colorsScheme = bodyEditor.createBoundColorSchemeDelegate(colorsScheme)
        bodyEditor.backgroundColor = colorsScheme.defaultBackground
        bodyEditor.highlighter = HighlighterFactory.createHighlighter(
            project.takeIf { !it.isDisposed } ?: ProjectManager.getInstance().defaultProject,
            detectedFileType
        )
    }

    private fun detectFileTypeByRequestBodyContent(requestBodyText: String): FileType {
        val normalizedRequestBodyText = requestBodyText.trimStart()
        if (normalizedRequestBodyText.startsWith("{") || normalizedRequestBodyText.startsWith("[")) {
            return JsonLanguage.INSTANCE.associatedFileType ?: JsonFileType.INSTANCE
        }
        if (normalizedRequestBodyText.startsWith("<")) {
            val xmlFileType = FileTypeManager.getInstance().getFileTypeByExtension("xml")
            if (xmlFileType !is UnknownFileType) {
                return xmlFileType
            }
        }
        return PlainTextFileType.INSTANCE
    }
}
