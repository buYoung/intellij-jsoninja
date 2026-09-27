package com.livteam.jsoninja.ui.dialog.loadJson

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorSettings
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
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.editor.EditorTextFieldFactory
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiAuthorizationType
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiRequestMethod
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent as SwingDocumentEvent

class LoadJsonFromApiDialogView(
    private val project: Project
) {
    private lateinit var rootPanel: JPanel
    private lateinit var requestMethodComboBox: ComboBox<ApiRequestMethod>
    private lateinit var requestUrlTextField: JBTextField
    private lateinit var authorizationTypeComboBox: ComboBox<ApiAuthorizationType>
    private lateinit var basicUsernameTextField: JBTextField
    private lateinit var basicPasswordTextField: JBPasswordField
    private lateinit var bearerTokenTextField: JBTextField
    private lateinit var requestBodyEditorTextField: EditorTextField
    private lateinit var requestRow: Row
    private lateinit var authorizationRow: Row
    private lateinit var basicUsernameRow: Row
    private lateinit var basicPasswordRow: Row
    private lateinit var bearerTokenRow: Row
    private lateinit var requestBodyRow: Row
    private val summaryLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    private var currentBodyFileType: FileType? = null
    private var onInputsChangedCallback: (() -> Unit)? = null
    private var onLayoutChangedCallback: (() -> Unit)? = null

    val component: JComponent by lazy { createComponent() }

    fun setOnInputsChanged(callback: () -> Unit) {
        onInputsChangedCallback = callback
    }

    fun setOnLayoutChanged(callback: () -> Unit) {
        onLayoutChangedCallback = callback
    }

    fun getRequestUrlComponent(): JComponent = requestUrlTextField

    fun getBasicUsernameComponent(): JComponent = basicUsernameTextField

    fun getBasicPasswordComponent(): JComponent = basicPasswordTextField

    fun getBearerTokenComponent(): JComponent = bearerTokenTextField

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
        return bearerTokenTextField.text.trim()
    }

    fun getRequestBodyText(): String {
        return requestBodyEditorTextField.text
    }

    fun setLoading(isLoading: Boolean) {
        listOf(requestRow, authorizationRow, basicUsernameRow, basicPasswordRow, bearerTokenRow, requestBodyRow)
            .forEach { it.enabled(!isLoading) }
    }

    fun setSummary(summary: String) {
        summaryLabel.text = summary
    }

    fun dispose() {
        (requestBodyEditorTextField as? Disposable)?.let { editorDisposable ->
            Disposer.dispose(editorDisposable)
        }
    }

    private fun createComponent(): JComponent {
        requestMethodComboBox = ComboBox(ApiRequestMethod.entries.toTypedArray()).apply {
            renderer = object : SimpleListCellRenderer<ApiRequestMethod>() {
                override fun customize(list: JList<out ApiRequestMethod>, value: ApiRequestMethod?, index: Int, selected: Boolean, hasFocus: Boolean) {
                    text = value?.name ?: ""
                }
            }
        }
        requestUrlTextField = JBTextField().apply {
            emptyText.text = LocalizationBundle.message("dialog.load.json.api.url.placeholder")
        }
        authorizationTypeComboBox = ComboBox(ApiAuthorizationType.entries.toTypedArray()).apply {
            renderer = object : SimpleListCellRenderer<ApiAuthorizationType>() {
                override fun customize(list: JList<out ApiAuthorizationType>, value: ApiAuthorizationType?, index: Int, selected: Boolean, hasFocus: Boolean) {
                    text = when (value) {
                        ApiAuthorizationType.NONE -> LocalizationBundle.message("dialog.load.json.api.auth.none")
                        ApiAuthorizationType.BASIC -> LocalizationBundle.message("dialog.load.json.api.auth.basic")
                        ApiAuthorizationType.BEARER -> LocalizationBundle.message("dialog.load.json.api.auth.bearer")
                        null -> ""
                    }
                }
            }
        }
        basicUsernameTextField = JBTextField()
        basicPasswordTextField = JBPasswordField()
        bearerTokenTextField = JBTextField()
        requestBodyEditorTextField = createRequestBodyEditorTextField()

        val configPanel = panel {
            requestRow = row {
                cell(requestMethodComboBox)
                    .label(LocalizationBundle.message("dialog.load.json.api.method"), LabelPosition.TOP)
                    .gap(RightGap.SMALL)
                cell(requestUrlTextField)
                    .label(LocalizationBundle.message("dialog.load.json.api.url"), LabelPosition.TOP)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }

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
                cell(bearerTokenTextField).align(AlignX.FILL).resizableColumn()
            }.visible(false)
            row(LocalizationBundle.message("dialog.load.json.api.content.type")) {
                label("application/json")
            }

            requestBodyRow = row {
                cell(requestBodyEditorTextField)
                    .label(LocalizationBundle.message("dialog.load.json.api.body"), LabelPosition.TOP)
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow().topGap(TopGap.SMALL).visible(false)
        }.apply {
            border = JBUI.Borders.empty(16, 12, 12, 12)
        }

        val footer = panel {
            separator()
            row { cell(summaryLabel) }
            row { comment(LocalizationBundle.message("dialog.load.json.api.output.destination")) }
        }.apply {
            border = JBUI.Borders.empty(0, 12, 0, 12)
        }

        rootPanel = object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                val size = super.getPreferredSize()
                return Dimension(maxOf(size.width, JBUI.scale(600)), size.height)
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
        updateRequestBodyHighlightByHeuristic()

        return rootPanel
    }

    private fun createRequestBodyEditorTextField(): EditorTextField {
        return EditorTextFieldFactory.createPlainTextField(
            project = project,
            preferredSize = JBUI.size(560, 220),
            placeholderText = LocalizationBundle.message("dialog.load.json.api.body.placeholder"),
            configureEditorSettings = {
                applyRequestBodyEditorSettings()
            },
        ).apply {
            minimumSize = JBUI.size(0, 160)
        }
    }

    private fun EditorSettings.applyRequestBodyEditorSettings() {
        isLineNumbersShown = true
        isWhitespacesShown = true
        isCaretRowShown = true
        isUseSoftWraps = true
    }

    private fun attachInputListeners() {
        requestMethodComboBox.addActionListener {
            updateRequestBodyVisibility()
            onInputsChangedCallback?.invoke()
        }

        authorizationTypeComboBox.addActionListener {
            updateAuthorizationInputVisibility()
            onInputsChangedCallback?.invoke()
        }

        val inputListener = object : DocumentAdapter() {
            override fun textChanged(event: SwingDocumentEvent) {
                onInputsChangedCallback?.invoke()
            }
        }
        listOf(requestUrlTextField, basicUsernameTextField, basicPasswordTextField, bearerTokenTextField)
            .forEach { it.document.addDocumentListener(inputListener) }

        requestBodyEditorTextField.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                updateRequestBodyHighlightByHeuristic()
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

    private fun updateRequestBodyHighlightByHeuristic() {
        val detectedFileType = detectFileTypeByRequestBodyContent(getRequestBodyText())
        if (currentBodyFileType?.name == detectedFileType.name) {
            return
        }
        currentBodyFileType = detectedFileType

        val bodyEditor = requestBodyEditorTextField.editor as? EditorEx ?: return
        val colorsScheme = EditorColorsManager.getInstance().globalScheme
        bodyEditor.colorsScheme = colorsScheme
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
