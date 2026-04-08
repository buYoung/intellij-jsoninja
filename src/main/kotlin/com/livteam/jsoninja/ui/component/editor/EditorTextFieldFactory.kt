package com.livteam.jsoninja.ui.component.editor

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.EditorTextField
import java.awt.Dimension

internal object EditorTextFieldFactory {
    fun createJsonField(
        project: Project?,
        fileExtension: String? = null,
        initialText: String = "",
        documentCreator: JsonDocumentCreator = SimpleJsonDocumentCreator(),
        placeholderText: String? = null,
        preferredSize: Dimension? = null,
        isViewer: Boolean = false,
        oneLineMode: Boolean = false,
        shouldApplyEditorColors: Boolean = false,
        shouldApplyHighlighter: Boolean = false,
        shouldShowHorizontalScrollbar: Boolean = false,
        shouldShowVerticalScrollbar: Boolean = false,
        shouldEmbedIntoDialogWrapper: Boolean = true,
        configureEditorSettings: EditorSettings.() -> Unit = {},
        customizeEditor: EditorEx.(FileType) -> Unit = {},
    ): EditorTextField {
        val extensionToUse = fileExtension ?: "json5"
        val fileType = resolveJsonFileType(extensionToUse)
        val document = documentCreator.createDocument(initialText, project, extensionToUse)

        return createConfiguredField(
            document = document,
            project = project,
            fileType = fileType,
            placeholderText = placeholderText,
            preferredSize = preferredSize,
            isViewer = isViewer,
            oneLineMode = oneLineMode,
            shouldEnableCodeFolding = true,
            shouldApplyEditorColors = shouldApplyEditorColors,
            shouldApplyHighlighter = shouldApplyHighlighter,
            shouldShowHorizontalScrollbar = shouldShowHorizontalScrollbar,
            shouldShowVerticalScrollbar = shouldShowVerticalScrollbar,
            shouldEmbedIntoDialogWrapper = shouldEmbedIntoDialogWrapper,
            configureEditorSettings = configureEditorSettings,
            customizeEditor = customizeEditor,
        )
    }

    fun createCodeField(
        project: Project,
        fileExtension: String,
        initialText: String = "",
        placeholderText: String? = null,
        preferredSize: Dimension? = null,
        isViewer: Boolean = false,
        oneLineMode: Boolean = false,
        shouldEnableCodeFolding: Boolean? = null,
        shouldApplyEditorColors: Boolean = false,
        shouldApplyHighlighter: Boolean = false,
        shouldShowHorizontalScrollbar: Boolean = false,
        shouldShowVerticalScrollbar: Boolean = false,
        shouldEmbedIntoDialogWrapper: Boolean = true,
        configureEditorSettings: EditorSettings.() -> Unit = {},
        customizeEditor: EditorEx.(FileType) -> Unit = {},
    ): EditorTextField {
        val fileType = resolveCodeFileType(fileExtension)
        val shouldUsePsiDocument = fileType !is PlainTextFileType
        val document = createCodeDocument(
            project = project,
            fileExtension = fileExtension,
            initialText = initialText,
            shouldUsePsiDocument = shouldUsePsiDocument,
        )

        return createConfiguredField(
            document = document,
            project = project,
            fileType = fileType,
            placeholderText = placeholderText,
            preferredSize = preferredSize,
            isViewer = isViewer,
            oneLineMode = oneLineMode,
            shouldEnableCodeFolding = shouldEnableCodeFolding ?: shouldUsePsiDocument,
            shouldApplyEditorColors = shouldApplyEditorColors,
            shouldApplyHighlighter = shouldApplyHighlighter,
            shouldShowHorizontalScrollbar = shouldShowHorizontalScrollbar,
            shouldShowVerticalScrollbar = shouldShowVerticalScrollbar,
            shouldEmbedIntoDialogWrapper = shouldEmbedIntoDialogWrapper,
            configureEditorSettings = configureEditorSettings,
            customizeEditor = customizeEditor,
        )
    }

    fun createPlainTextField(
        project: Project,
        initialText: String = "",
        placeholderText: String? = null,
        preferredSize: Dimension? = null,
        isViewer: Boolean = false,
        oneLineMode: Boolean = false,
        shouldEmbedIntoDialogWrapper: Boolean = true,
        configureEditorSettings: EditorSettings.() -> Unit = {},
        customizeEditor: EditorEx.(FileType) -> Unit = {},
    ): EditorTextField {
        val document = EditorFactory.getInstance().createDocument(initialText)

        return createConfiguredField(
            document = document,
            project = project,
            fileType = PlainTextFileType.INSTANCE,
            placeholderText = placeholderText,
            preferredSize = preferredSize,
            isViewer = isViewer,
            oneLineMode = oneLineMode,
            shouldEnableCodeFolding = false,
            shouldApplyEditorColors = false,
            shouldApplyHighlighter = false,
            shouldShowHorizontalScrollbar = false,
            shouldShowVerticalScrollbar = false,
            shouldEmbedIntoDialogWrapper = shouldEmbedIntoDialogWrapper,
            configureEditorSettings = configureEditorSettings,
            customizeEditor = customizeEditor,
        )
    }

    private fun createConfiguredField(
        document: Document,
        project: Project?,
        fileType: FileType,
        placeholderText: String?,
        preferredSize: Dimension?,
        isViewer: Boolean,
        oneLineMode: Boolean,
        shouldEnableCodeFolding: Boolean,
        shouldApplyEditorColors: Boolean,
        shouldApplyHighlighter: Boolean,
        shouldShowHorizontalScrollbar: Boolean,
        shouldShowVerticalScrollbar: Boolean,
        shouldEmbedIntoDialogWrapper: Boolean,
        configureEditorSettings: EditorSettings.() -> Unit,
        customizeEditor: EditorEx.(FileType) -> Unit,
    ): EditorTextField {
        val editorTextField = if (shouldEnableCodeFolding) {
            FoldingAwareEditorTextField(document, project, fileType, isViewer, oneLineMode)
        } else {
            EditorTextField(document, project, fileType, isViewer, oneLineMode)
        }

        placeholderText?.let(editorTextField::setPlaceholder)
        preferredSize?.let { editorTextField.preferredSize = it }
        editorTextField.putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)
        editorTextField.addSettingsProvider { editor ->
            editor.settings.configureEditorSettings()
            editor.settings.isFoldingOutlineShown = shouldEnableCodeFolding
            editor.isEmbeddedIntoDialogWrapper = shouldEmbedIntoDialogWrapper

            if (shouldApplyEditorColors || shouldApplyHighlighter) {
                applyEditorAppearance(
                    editor = editor,
                    project = project,
                    fileType = fileType,
                    shouldApplyEditorColors = shouldApplyEditorColors,
                    shouldApplyHighlighter = shouldApplyHighlighter,
                )
            }

            if (shouldShowHorizontalScrollbar) {
                editor.setHorizontalScrollbarVisible(true)
            }

            if (shouldShowVerticalScrollbar) {
                editor.setVerticalScrollbarVisible(true)
            }

            editor.customizeEditor(fileType)
        }

        return editorTextField
    }

    private fun createCodeDocument(
        project: Project,
        fileExtension: String,
        initialText: String,
        shouldUsePsiDocument: Boolean,
    ): Document {
        if (!shouldUsePsiDocument) {
            return EditorFactory.getInstance().createDocument(initialText)
        }

        return JsonDocumentFactory.createJsonDocument(
            value = initialText,
            project = project,
            documentCreator = SimpleJsonDocumentCreator(),
            fileExtension = fileExtension,
        )
    }

    private fun applyEditorAppearance(
        editor: EditorEx,
        project: Project?,
        fileType: FileType,
        shouldApplyEditorColors: Boolean,
        shouldApplyHighlighter: Boolean,
    ) {
        val globalScheme = EditorColorsManager.getInstance().globalScheme
        if (shouldApplyEditorColors) {
            editor.colorsScheme = globalScheme
            editor.backgroundColor = globalScheme.defaultBackground
        }

        if (shouldApplyHighlighter) {
            editor.highlighter = HighlighterFactory.createHighlighter(
                resolveHighlighterProject(project),
                fileType,
            )
        }
    }

    private fun resolveJsonFileType(fileExtension: String): FileType {
        return resolveFileType(
            fileExtension = fileExtension,
            fallbackFileType = JsonLanguage.INSTANCE.associatedFileType ?: JsonFileType.INSTANCE,
        )
    }

    private fun resolveCodeFileType(fileExtension: String): FileType {
        return resolveFileType(
            fileExtension = fileExtension,
            fallbackFileType = PlainTextFileType.INSTANCE,
        )
    }

    private fun resolveFileType(
        fileExtension: String,
        fallbackFileType: FileType,
    ): FileType {
        val resolvedFileType = FileTypeManager.getInstance().getFileTypeByExtension(fileExtension)
        return if (resolvedFileType is UnknownFileType) fallbackFileType else resolvedFileType
    }

    private fun resolveHighlighterProject(project: Project?): Project {
        if (project != null && !project.isDisposed) {
            return project
        }

        return ProjectManager.getInstance().defaultProject
    }
}

internal fun setEditorTextAndRefreshCodeFolding(
    project: Project?,
    editorTextField: EditorTextField?,
    text: String,
) {
    val currentEditorTextField = editorTextField ?: return
    currentEditorTextField.text = text

    if (currentEditorTextField is FoldingAwareEditorTextField) {
        refreshFoldRegionsIfAvailable(project, currentEditorTextField)
    }
}
