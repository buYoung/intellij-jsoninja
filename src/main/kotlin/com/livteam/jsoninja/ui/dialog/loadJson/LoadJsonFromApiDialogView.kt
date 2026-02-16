package com.livteam.jsoninja.ui.dialog.loadJson

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
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
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiAuthorizationType
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiRequestMethod
import java.awt.BorderLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingConstants

class LoadJsonFromApiDialogView(
    private val project: Project
) {
    private lateinit var rootPanel: JPanel
    private lateinit var requestMethodComboBox: JComboBox<ApiRequestMethod>
    private lateinit var requestUrlTextField: JBTextField
    private lateinit var sendButton: JButton
    private lateinit var authorizationTypeComboBox: JComboBox<ApiAuthorizationType>
    private lateinit var basicUsernameTextField: JBTextField
    private lateinit var basicPasswordTextField: JBPasswordField
    private lateinit var bearerTokenTextField: JBTextField
    private lateinit var contentTypeTextField: JBTextField
    private lateinit var requestBodyEditorTextField: EditorTextField
    private lateinit var basicAuthPanel: JPanel
    private lateinit var bearerAuthPanel: JPanel
    private lateinit var requestBodyPanel: JPanel
    private lateinit var errorMessageLabel: JBLabel

    private var currentBodyFileType: FileType? = null
    private var onSendRequestedCallback: (() -> Unit)? = null

    val component: JComponent by lazy { createComponent() }

    fun setOnSendRequested(callback: () -> Unit) {
        onSendRequestedCallback = callback
    }

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
        sendButton.isEnabled = !isLoading
        sendButton.text = if (isLoading) {
            LocalizationBundle.message("dialog.load.json.api.loading")
        } else {
            LocalizationBundle.message("dialog.load.json.api.send")
        }
    }

    fun showErrorMessage(errorMessage: String) {
        errorMessageLabel.text = errorMessage
    }

    fun clearErrorMessage() {
        errorMessageLabel.text = " "
    }

    fun dispose() {
        (requestBodyEditorTextField as? Disposable)?.let { editorDisposable ->
            Disposer.dispose(editorDisposable)
        }
    }

    private fun createComponent(): JComponent {
        requestMethodComboBox = JComboBox(ApiRequestMethod.values()).apply {
            renderer = createRequestMethodRenderer()
        }
        requestUrlTextField = JBTextField()
        sendButton = JButton(LocalizationBundle.message("dialog.load.json.api.send"))
        authorizationTypeComboBox = JComboBox(ApiAuthorizationType.values()).apply {
            renderer = createAuthorizationTypeRenderer()
        }
        basicUsernameTextField = JBTextField()
        basicPasswordTextField = JBPasswordField()
        bearerTokenTextField = JBTextField()
        contentTypeTextField = JBTextField("application/json").apply {
            isEditable = false
            horizontalAlignment = SwingConstants.LEFT
        }
        requestBodyEditorTextField = createRequestBodyEditorTextField()
        errorMessageLabel = JBLabel(" ").apply {
            foreground = UIUtil.getErrorForeground()
        }

        basicAuthPanel = createBasicAuthPanel()
        bearerAuthPanel = createBearerAuthPanel()

        val configPanel = panel {
            row {
                cell(requestMethodComboBox)
                    .gap(RightGap.SMALL)
                cell(requestUrlTextField)
                    .resizableColumn()
                    .align(AlignX.FILL)
                cell(sendButton)
            }

            separator()

            row(LocalizationBundle.message("dialog.load.json.api.auth.type")) {
                cell(authorizationTypeComboBox)
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)

            row {
                cell(basicAuthPanel)
                    .align(AlignX.FILL)
            }
            row {
                cell(bearerAuthPanel)
                    .align(AlignX.FILL)
            }

            separator()

            row(LocalizationBundle.message("dialog.load.json.api.content.type")) {
                cell(contentTypeTextField)
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
        }

        requestBodyPanel = JPanel(BorderLayout()).apply {
            val bodyLabel = JLabel(LocalizationBundle.message("dialog.load.json.api.body")).apply {
                border = JBUI.Borders.empty(8, 2, 4, 0)
            }
            add(bodyLabel, BorderLayout.NORTH)
            add(requestBodyEditorTextField, BorderLayout.CENTER)
        }

        rootPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            preferredSize = java.awt.Dimension(JBUI.scale(760), preferredSize.height)
            add(configPanel, BorderLayout.NORTH)
            add(requestBodyPanel, BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                border = JBUI.Borders.emptyTop(6)
                add(errorMessageLabel, BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
        }

        attachInputListeners()
        updateAuthorizationInputVisibility()
        updateRequestBodyVisibility()
        updateRequestBodyHighlightByHeuristic()

        return rootPanel
    }

    private fun createBasicAuthPanel(): JPanel {
        return panel {
            indent {
                row(LocalizationBundle.message("dialog.load.json.api.auth.username")) {
                    cell(basicUsernameTextField)
                        .align(AlignX.FILL)
                        .resizableColumn()
                }
                row(LocalizationBundle.message("dialog.load.json.api.auth.password")) {
                    cell(basicPasswordTextField)
                        .align(AlignX.FILL)
                        .resizableColumn()
                }
            }
        }
    }

    private fun createBearerAuthPanel(): JPanel {
        return panel {
            indent {
                row(LocalizationBundle.message("dialog.load.json.api.auth.token")) {
                    cell(bearerTokenTextField)
                        .align(AlignX.FILL)
                        .resizableColumn()
                }
            }
        }
    }

    private fun createRequestBodyEditorTextField(): EditorTextField {
        val document = EditorFactory.getInstance().createDocument("")
        return EditorTextField(document, project, PlainTextFileType.INSTANCE, false, false).apply {
            preferredSize = JBUI.size(620, 220)
            setPlaceholder(LocalizationBundle.message("dialog.load.json.api.body.placeholder"))
            addSettingsProvider { editor ->
                editor.settings.applyRequestBodyEditorSettings()
                editor.isEmbeddedIntoDialogWrapper = true
            }
            putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)
        }
    }

    private fun EditorSettings.applyRequestBodyEditorSettings() {
        isLineNumbersShown = true
        isWhitespacesShown = true
        isCaretRowShown = true
        isUseSoftWraps = true
    }

    private fun createRequestMethodRenderer(): DefaultListCellRenderer {
        return object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (component is JLabel && value is ApiRequestMethod) {
                    component.text = value.name
                }
                return component
            }
        }
    }

    private fun createAuthorizationTypeRenderer(): DefaultListCellRenderer {
        return object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (component is JLabel && value is ApiAuthorizationType) {
                    component.text = when (value) {
                        ApiAuthorizationType.NONE -> LocalizationBundle.message("dialog.load.json.api.auth.none")
                        ApiAuthorizationType.BASIC -> LocalizationBundle.message("dialog.load.json.api.auth.basic")
                        ApiAuthorizationType.BEARER -> LocalizationBundle.message("dialog.load.json.api.auth.bearer")
                    }
                }
                return component
            }
        }
    }

    private fun attachInputListeners() {
        requestMethodComboBox.addActionListener {
            clearErrorMessage()
            updateRequestBodyVisibility()
        }

        authorizationTypeComboBox.addActionListener {
            clearErrorMessage()
            updateAuthorizationInputVisibility()
        }

        sendButton.addActionListener {
            onSendRequestedCallback?.invoke()
        }

        requestBodyEditorTextField.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                updateRequestBodyHighlightByHeuristic()
            }
        })
    }

    private fun updateAuthorizationInputVisibility() {
        val selectedAuthorizationType = getSelectedAuthorizationType()
        basicAuthPanel.isVisible = selectedAuthorizationType == ApiAuthorizationType.BASIC
        bearerAuthPanel.isVisible = selectedAuthorizationType == ApiAuthorizationType.BEARER
        if (::rootPanel.isInitialized) {
            rootPanel.revalidate()
            rootPanel.repaint()
        }
    }

    private fun updateRequestBodyVisibility() {
        val isRequestBodyVisible = getSelectedRequestMethod().supportsRequestBody
        requestBodyPanel.isVisible = isRequestBodyVisible
        if (::rootPanel.isInitialized) {
            rootPanel.revalidate()
            rootPanel.repaint()
        }
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
