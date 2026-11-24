package com.livteam.jsoninja.ui.component.editor

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.ComponentUtil
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorTextField
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.PopupHandler
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.actions.CopyJsonArrayQueryAction
import com.livteam.jsoninja.actions.CopyJsonQueryAction
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelView
import com.livteam.jsoninja.ui.onboarding.OnboardingTutorialTargetIds
import java.awt.BorderLayout
import javax.swing.JPanel

class JsonEditorTextView(
    private val project: Project,
    private val fileExtension: String? = null,
    private val documentCreator: JsonDocumentCreator = SimpleJsonDocumentCreator(),
    private val isEmbeddedInDialog: Boolean = false,
) : JPanel(), Disposable, UiDataProvider {

    companion object {
        private const val EMPTY_TEXT = ""
        private const val LARGE_DOCUMENT_FAST_MODE_THRESHOLD = 512_000
        private const val HUGE_DOCUMENT_HIGHLIGHTER_THRESHOLD = 2_000_000
        private const val LONG_LINE_FAST_MODE_THRESHOLD = 10_000
        private var PLACEHOLDER_TEXT = LocalizationBundle.message("enterJsonHere")
    }

    val editor: EditorTextField
    private var editorFileType: FileType? = null
    private var isLargeDocumentFastMode = false
    private var isEmptyHighlighterActive = false
    private var isLongLineActive = false
    private var fastModeBanner: EditorNotificationPanel? = null
    private var fastModePrettifyLink: HyperlinkLabel? = null
    @Volatile
    private var isDisposed = false

    private val largeDocumentDisplayModeListener = object : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {
            if (isLargeDocumentFastMode && isEmptyHighlighterActive) {
                return
            }

            // Disable heavy lexer/daemon work before DocumentImpl updates on huge or long-line pastes.
            val document = event.document
            val projectedLength = document.textLength - event.oldLength + event.newLength
            val isHugeProjected = projectedLength >= HUGE_DOCUMENT_HIGHLIGHTER_THRESHOLD
            val hasLongIncomingLine = if (isHugeProjected) {
                false
            } else {
                isLongIncomingEdit(event, document, projectedLength)
            }

            val nextLargeMode = isLargeDocumentFastMode ||
                projectedLength >= LARGE_DOCUMENT_FAST_MODE_THRESHOLD ||
                hasLongIncomingLine
            val nextEmptyHighlighter = isEmptyHighlighterActive ||
                isHugeProjected ||
                hasLongIncomingLine
            val nextLongLine = isLongLineActive || hasLongIncomingLine

            if (nextLargeMode != isLargeDocumentFastMode ||
                nextEmptyHighlighter != isEmptyHighlighterActive ||
                nextLongLine != isLongLineActive
            ) {
                applyDocumentDisplayMode(
                    useFastMode = nextLargeMode,
                    useEmptyHighlighter = nextEmptyHighlighter,
                    hasLongLine = nextLongLine,
                    document = document,
                )
            }
        }

        override fun documentChanged(event: DocumentEvent) {
            val document = event.document
            val textLength = document.textLength
            val isHuge = textLength >= HUGE_DOCUMENT_HIGHLIGHTER_THRESHOLD
            val hasLongLine = if (isHuge) false else hasLongLine(document)
            val shouldUseFastMode = isHuge || textLength >= LARGE_DOCUMENT_FAST_MODE_THRESHOLD || hasLongLine
            val shouldUseEmptyHighlighter = isHuge || hasLongLine
            if (shouldUseFastMode != isLargeDocumentFastMode ||
                shouldUseEmptyHighlighter != isEmptyHighlighterActive ||
                hasLongLine != isLongLineActive
            ) {
                applyDocumentDisplayMode(
                    useFastMode = shouldUseFastMode,
                    useEmptyHighlighter = shouldUseEmptyHighlighter,
                    hasLongLine = hasLongLine,
                    document = document,
                )
            }
        }
    }

    init {
        layout = BorderLayout()
        if (!isEmbeddedInDialog) {
            val banner = EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
                isVisible = false
            }
            fastModePrettifyLink = banner.createActionLabel(LocalizationBundle.message("prettify")) {
                ComponentUtil.getParentOfType(JsoninjaPanelView::class.java, this@JsonEditorTextView)
                    ?.presenter
                    ?.formatJson()
            }
            fastModeBanner = banner
            add(banner, BorderLayout.NORTH)
        }
        editor = createJsonEditor()
        editor.setDisposedWith(this)
        editor.addDocumentListener(largeDocumentDisplayModeListener)
        add(editor, BorderLayout.CENTER)
        subscribeThemeChanges()
    }

    private fun subscribeThemeChanges() {
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { scheduleThemeRefresh() })
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { scheduleThemeRefresh() })
    }

    private fun scheduleThemeRefresh() {
        if (isDisposed || project.isDisposed) return
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            refreshEditorTheme()
        } else {
            app.invokeLater({
                if (!isDisposed && !project.isDisposed) {
                    refreshEditorTheme()
                }
            }, ModalityState.stateForComponent(this))
        }
    }

    private fun refreshEditorTheme() {
        val editorEx = editor.editor as? EditorEx ?: return
        if (editorEx.isDisposed) return
        val scheme = EditorColorsManager.getInstance().globalScheme
        editorEx.colorsScheme = editorEx.createBoundColorSchemeDelegate(scheme)
        editorEx.backgroundColor = scheme.defaultBackground
        applyEditorHighlighterMode(editorEx, isEmptyHighlighterActive, forceRecreate = true)
        editorEx.component.repaint()
    }

    private fun createJsonEditor(): EditorTextField {
        return EditorTextFieldFactory.createJsonField(
            project = project,
            fileExtension = fileExtension,
            initialText = EMPTY_TEXT,
            documentCreator = documentCreator,
            placeholderText = PLACEHOLDER_TEXT,
            shouldApplyEditorColors = true,
            shouldApplyHighlighter = true,
            shouldShowHorizontalScrollbar = true,
            shouldShowVerticalScrollbar = true,
            shouldEmbedIntoDialogWrapper = isEmbeddedInDialog,
            isSupplementary = false,
            configureEditorSettings = {
                applyEditorSettings(isLargeDocumentFastMode)
            },
            customizeEditor = { fileType ->
                editorFileType = fileType
                contentComponent.name = OnboardingTutorialTargetIds.JSON_EDITOR
                installEditorContextMenu(this)
                val textLength = document.textLength
                val isHuge = textLength >= HUGE_DOCUMENT_HIGHLIGHTER_THRESHOLD
                val hasLongLine = if (isHuge) false else hasLongLine(document)
                val shouldUseFastMode = isHuge || textLength >= LARGE_DOCUMENT_FAST_MODE_THRESHOLD || hasLongLine
                val shouldUseEmptyHighlighter = isHuge || hasLongLine
                if (shouldUseFastMode || shouldUseEmptyHighlighter || isLargeDocumentFastMode || isEmptyHighlighterActive || hasLongLine || isLongLineActive) {
                    applyDocumentDisplayMode(
                        useFastMode = shouldUseFastMode,
                        useEmptyHighlighter = shouldUseEmptyHighlighter,
                        hasLongLine = hasLongLine,
                        document = document,
                        editorEx = this,
                        forceApply = true,
                    )
                }
            },
        )
    }

    private fun installEditorContextMenu(editor: EditorEx) {
        val actionManager = ActionManager.getInstance()
        val group = DefaultActionGroup()

        val clipboardActions = listOfNotNull(
            actionManager.getAction(IdeActions.ACTION_CUT),
            actionManager.getAction(IdeActions.ACTION_COPY),
            actionManager.getAction(IdeActions.ACTION_PASTE),
            actionManager.getAction(IdeActions.ACTION_SELECT_ALL),
        )
        clipboardActions.forEach(group::add)

        val editorUtilityActions = listOfNotNull(
            actionManager.getAction(IdeActions.ACTION_FIND),
            actionManager.getAction(IdeActions.ACTION_UNDO),
            actionManager.getAction(IdeActions.ACTION_REDO),
        )
        if (editorUtilityActions.isNotEmpty()) {
            if (group.childrenCount > 0) {
                group.addSeparator()
            }
            editorUtilityActions.forEach(group::add)
        }

        val copyJsonQueryAction = actionManager.getAction("com.livteam.jsoninja.action.CopyJsonQueryAction")
            ?: CopyJsonQueryAction().apply {
                templatePresentation.text = LocalizationBundle.message("action.copy.json.query")
                templatePresentation.description =
                    LocalizationBundle.message("action.copy.json.query.description")
            }
        val copyJsonArrayQueryAction = actionManager.getAction("com.livteam.jsoninja.action.CopyJsonArrayQueryAction")
            ?: CopyJsonArrayQueryAction()

        if (group.childrenCount > 0) {
            group.addSeparator()
        }
        group.add(copyJsonQueryAction)
        group.add(copyJsonArrayQueryAction)

        if (group.childrenCount > 0) {
            PopupHandler.installPopupMenu(
                editor.contentComponent,
                group,
                ActionPlaces.EDITOR_POPUP
            )
        }
    }

    private fun EditorSettings.applyEditorSettings(useFastMode: Boolean) {
        val enableRichVisuals = !useFastMode
        isLineNumbersShown = true
        isWhitespacesShown = enableRichVisuals && EditorSettingsExternalizable.getInstance().isWhitespacesShown
        isCaretRowShown = true
        isRightMarginShown = true
        // Always keep soft wraps enabled so EditorImpl's TextLayoutCache avoids laying out huge unwrapped lines.
        isUseSoftWraps = true
        isIndentGuidesShown = enableRichVisuals
    }

    private fun EditorSettings.applyDocumentDisplayModeSettings(useFastMode: Boolean) {
        val enableRichVisuals = !useFastMode
        val shouldShowWhitespaces = enableRichVisuals && EditorSettingsExternalizable.getInstance().isWhitespacesShown
        if (isWhitespacesShown != shouldShowWhitespaces) {
            isWhitespacesShown = shouldShowWhitespaces
        }
        if (isIndentGuidesShown != enableRichVisuals) {
            isIndentGuidesShown = enableRichVisuals
        }
    }

    private fun applyDocumentDisplayMode(
        useFastMode: Boolean,
        useEmptyHighlighter: Boolean,
        hasLongLine: Boolean,
        document: Document,
        editorEx: EditorEx? = editor.editor as? EditorEx,
        forceApply: Boolean = false,
    ) {
        val fastModeChanged = forceApply || isLargeDocumentFastMode != useFastMode
        val highlighterChanged = forceApply || isEmptyHighlighterActive != useEmptyHighlighter
        val longLineChanged = forceApply || isLongLineActive != hasLongLine
        if (!fastModeChanged && !highlighterChanged && !longLineChanged) {
            return
        }

        isLargeDocumentFastMode = useFastMode
        isEmptyHighlighterActive = useEmptyHighlighter
        isLongLineActive = hasLongLine

        if (editorEx != null && !editorEx.isDisposed) {
            if (fastModeChanged) {
                editorEx.settings.applyDocumentDisplayModeSettings(useFastMode)
            }
            if (highlighterChanged) {
                applyEditorHighlighterMode(editorEx, useEmptyHighlighter)
            }
        }
        if (fastModeChanged) {
            setDaemonHighlightingEnabled(document, enabled = !useFastMode)
        }
        updateFastModeBanner(
            useFastMode = useFastMode,
            useEmptyHighlighter = useEmptyHighlighter,
            hasLongLine = hasLongLine,
        )
    }

    private fun updateFastModeBanner(
        useFastMode: Boolean,
        useEmptyHighlighter: Boolean,
        hasLongLine: Boolean,
    ) {
        val banner = fastModeBanner ?: return
        val shouldShow = useFastMode || useEmptyHighlighter
        if (!shouldShow) {
            if (banner.isVisible) {
                banner.isVisible = false
                revalidate()
                repaint()
            }
            return
        }

        val messageKey = if (useEmptyHighlighter && hasLongLine) {
            "editor.fast.mode.long.line"
        } else {
            "editor.fast.mode.large.file"
        }
        banner.text = LocalizationBundle.message(messageKey)
        fastModePrettifyLink?.isVisible = hasLongLine
        if (!banner.isVisible) {
            banner.isVisible = true
        }
        revalidate()
        repaint()
    }

    private fun applyEditorHighlighterMode(
        editorEx: EditorEx,
        useEmptyHighlighter: Boolean,
        forceRecreate: Boolean = false,
    ) {
        if (useEmptyHighlighter) {
            if (forceRecreate || editorEx.highlighter !is EmptyEditorHighlighter) {
                editorEx.highlighter = EmptyEditorHighlighter(editorEx.colorsScheme, HighlighterColors.TEXT)
            }
            return
        }

        if (forceRecreate || editorEx.highlighter is EmptyEditorHighlighter) {
            val fileType = editorFileType ?: return
            val highlighterProject = if (!project.isDisposed) project else ProjectManager.getInstance().defaultProject
            editorEx.highlighter = EditorHighlighterFactory.getInstance()
                .createEditorHighlighter(fileType, editorEx.colorsScheme, highlighterProject)
        }
    }

    private fun setDaemonHighlightingEnabled(document: Document, enabled: Boolean) {
        if (project.isDisposed) {
            return
        }
        val psiFile = PsiDocumentManager.getInstance(project).getCachedPsiFile(document) ?: return
        if (!psiFile.isValid || psiFile.project !== project) {
            return
        }
        DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, enabled)
    }

    private fun isLongIncomingEdit(event: DocumentEvent, document: Document, projectedLength: Int): Boolean {
        if (event.newLength >= LONG_LINE_FAST_MODE_THRESHOLD && hasLongLine(event.newFragment)) {
            return true
        }
        if (projectedLength >= LONG_LINE_FAST_MODE_THRESHOLD && event.newLength > 0 && document.lineCount > 0) {
            val lineNumber = document.getLineNumber(event.offset)
            val currentLineLength = document.getLineEndOffset(lineNumber) - document.getLineStartOffset(lineNumber)
            if (currentLineLength + event.newLength >= LONG_LINE_FAST_MODE_THRESHOLD &&
                event.newFragment.indexOf('\n') < 0
            ) {
                return true
            }
        }
        return false
    }

    private fun hasLongLine(document: Document): Boolean {
        if (document.textLength < LONG_LINE_FAST_MODE_THRESHOLD) {
            return false
        }
        val lineCount = document.lineCount
        for (line in 0 until lineCount) {
            if (document.getLineEndOffset(line) - document.getLineStartOffset(line) >= LONG_LINE_FAST_MODE_THRESHOLD) {
                return true
            }
        }
        return false
    }

    private fun hasLongLine(text: CharSequence): Boolean {
        val length = text.length
        if (length < LONG_LINE_FAST_MODE_THRESHOLD) {
            return false
        }
        var lineStart = 0
        if (text is String) {
            while (lineStart < length) {
                val newlineIndex = text.indexOf('\n', lineStart)
                if (newlineIndex < 0) {
                    return length - lineStart >= LONG_LINE_FAST_MODE_THRESHOLD
                }
                if (newlineIndex - lineStart >= LONG_LINE_FAST_MODE_THRESHOLD) {
                    return true
                }
                lineStart = newlineIndex + 1
            }
            return false
        }
        for (index in 0 until length) {
            if (text[index] == '\n') {
                if (index - lineStart >= LONG_LINE_FAST_MODE_THRESHOLD) {
                    return true
                }
                lineStart = index + 1
            }
        }
        return length - lineStart >= LONG_LINE_FAST_MODE_THRESHOLD
    }

    fun getText(): String = editor.text

    override fun uiDataSnapshot(sink: DataSink) {
        val currentEditor = editor.editor ?: return
        if (!currentEditor.isDisposed) {
            sink[PlatformCoreDataKeys.FILE_EDITOR] = TextEditorProvider.getInstance().getTextEditor(currentEditor)
        }
    }

    fun setText(text: String) {
        // Normalize CR/CRLF before EditorTextField.setText so LINE_SEPARATOR_KEY does not latch CRLF across clears/pastes.
        val normalizedText = if (text.indexOf('\r') >= 0) StringUtil.convertLineSeparators(text) else text
        val isHuge = normalizedText.length >= HUGE_DOCUMENT_HIGHLIGHTER_THRESHOLD
        val hasLongLine = if (isHuge) false else hasLongLine(normalizedText)
        val nextFastMode = isLargeDocumentFastMode ||
            normalizedText.length >= LARGE_DOCUMENT_FAST_MODE_THRESHOLD ||
            hasLongLine
        val nextEmptyHighlighter = isEmptyHighlighterActive || isHuge || hasLongLine
        val nextLongLine = isLongLineActive || hasLongLine
        if (nextFastMode != isLargeDocumentFastMode ||
            nextEmptyHighlighter != isEmptyHighlighterActive ||
            nextLongLine != isLongLineActive
        ) {
            applyDocumentDisplayMode(
                useFastMode = nextFastMode,
                useEmptyHighlighter = nextEmptyHighlighter,
                hasLongLine = nextLongLine,
                document = editor.document,
            )
        }
        setEditorTextAndRefreshCodeFolding(project, editor, normalizedText)
    }

    override fun dispose() {
        isDisposed = true
        editor.removeDocumentListener(largeDocumentDisplayModeListener)
        (editor as? Disposable)?.let { Disposer.dispose(it) }
        removeAll()
    }
}
