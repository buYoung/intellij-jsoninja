package com.livteam.jsoninja.services

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.json.JsonFileType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.actions.SortJsonDiffKeysOnceAction
import com.livteam.jsoninja.diff.JsonDiffKeys
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.ui.diff.JsonDiffVirtualFile
import com.livteam.jsoninja.ui.diff.JsonDiffWindowDialog

@Service(Service.Level.PROJECT)
class JsonDiffService(private val project: Project) {

    private val formatterService = project.service<JsonFormatterService>()

    private data class ActiveDiffContext(
        var displayMode: JsonDiffDisplayMode,
        var sortKeys: Boolean,
        val leftDocument: Document,
        val rightDocument: Document,
        var editorTabFile: JsonDiffVirtualFile? = null,
        var windowDialog: JsonDiffWindowDialog? = null
    )

    private var activeDiffContext: ActiveDiffContext? = null

    fun openDiff(
        displayMode: JsonDiffDisplayMode,
        currentJson: String?,
        defaultSortKeys: Boolean
    ) {
        val leftJson = currentJson ?: "{}"
        val diffContext = getOrCreateContext(leftJson, displayMode, defaultSortKeys)
        val currentDisplayMode = getCurrentDisplayMode(diffContext)

        if (currentDisplayMode != displayMode) {
            closeOpenHosts(diffContext)
            diffContext.displayMode = displayMode
        }

        openCurrentHost(diffContext)
    }

    /**
     * Validates and formats JSON in a single operation to improve performance
     * @param json The JSON string to validate and format
     * @param semantic Whether to use semantic comparison (sorted keys)
     * @return Pair of (isValid, formattedJson) - formattedJson is null if invalid
     */
    fun validateAndFormat(json: String, semantic: Boolean): Pair<Boolean, String?> {
        return try {
            if (!formatterService.isValidJson(json)) {
                return Pair(false, null)
            }

            val formatState = if (semantic) JsonFormatState.PRETTIFY_SORTED else JsonFormatState.PRETTIFY
            val formatted = formatterService.formatJson(json, formatState, semantic)
            Pair(true, formatted)
        } catch (e: Exception) {
            Pair(false, null)
        }
    }

    fun createDiffRequest(
        leftJson: String,
        rightJson: String,
        title: String? = null,
        semantic: Boolean = false
    ): SimpleDiffRequest {
        val leftFinal = prepareDiffText(leftJson, semantic)
        val rightFinal = prepareDiffText(rightJson, semantic)

        return createDiffRequest(
            leftDocument = createDiffDocument(leftFinal),
            rightDocument = createDiffDocument(rightFinal),
            title = title,
            semantic = semantic
        )
    }

    fun createDiffRequest(
        leftDocument: Document,
        rightDocument: Document,
        title: String? = null,
        semantic: Boolean = false
    ): SimpleDiffRequest {
        val diffTitle = title ?: LocalizationBundle.message("dialog.json.diff.title")
        val leftContent = createDiffContent(leftDocument)
        val rightContent = createDiffContent(rightDocument)

        val request = SimpleDiffRequest(
            diffTitle,
            leftContent,
            rightContent,
            LocalizationBundle.message("dialog.json.diff.left"),
            LocalizationBundle.message("dialog.json.diff.right")
        )

        request.putUserData(JsonDiffKeys.JSON_DIFF_REQUEST_MARKER, true)
        request.putUserData(JsonDiffKeys.JSON_DIFF_SORT_KEYS, semantic)
        request.putUserData(
            DiffUserDataKeys.CONTEXT_ACTIONS,
            listOf(
                SortJsonDiffKeysOnceAction()
            )
        )

        return request
    }

    private fun getOrCreateContext(
        leftJson: String,
        displayMode: JsonDiffDisplayMode,
        defaultSortKeys: Boolean
    ): ActiveDiffContext {
        val existingContext = activeDiffContext

        if (existingContext != null && hasOpenHost(existingContext)) {
            replaceDocumentText(existingContext.leftDocument, prepareDiffText(leftJson, existingContext.sortKeys))
            return existingContext
        }

        return ActiveDiffContext(
            displayMode = displayMode,
            sortKeys = defaultSortKeys,
            leftDocument = createDiffDocument(prepareDiffText(leftJson, defaultSortKeys)),
            rightDocument = createDiffDocument(prepareDiffText("{}", defaultSortKeys))
        ).also { activeDiffContext = it }
    }

    private fun hasOpenHost(diffContext: ActiveDiffContext): Boolean {
        return isEditorTabOpen(diffContext) || isWindowOpen(diffContext)
    }

    private fun getCurrentDisplayMode(diffContext: ActiveDiffContext): JsonDiffDisplayMode {
        return getOpenDisplayMode(diffContext) ?: diffContext.displayMode
    }

    private fun getOpenDisplayMode(diffContext: ActiveDiffContext): JsonDiffDisplayMode? {
        val isEditorTabOpen = isEditorTabOpen(diffContext)
        val isWindowOpen = isWindowOpen(diffContext)

        return when {
            isEditorTabOpen && !isWindowOpen -> JsonDiffDisplayMode.EDITOR_TAB
            isWindowOpen && !isEditorTabOpen -> JsonDiffDisplayMode.WINDOW
            isEditorTabOpen && isWindowOpen -> diffContext.displayMode
            else -> null
        }
    }

    private fun isEditorTabOpen(diffContext: ActiveDiffContext): Boolean {
        val diffFile = diffContext.editorTabFile ?: return false
        return FileEditorManager.getInstance(project).isFileOpen(diffFile)
    }

    private fun isWindowOpen(diffContext: ActiveDiffContext): Boolean {
        return diffContext.windowDialog?.isOpen() == true
    }

    private fun openCurrentHost(diffContext: ActiveDiffContext) {
        when (diffContext.displayMode) {
            JsonDiffDisplayMode.EDITOR_TAB -> openEditorTab(diffContext)
            JsonDiffDisplayMode.WINDOW -> openWindowDialog(diffContext)
        }
    }

    private fun closeOpenHosts(diffContext: ActiveDiffContext) {
        if (isEditorTabOpen(diffContext)) {
            diffContext.editorTabFile?.let { FileEditorManager.getInstance(project).closeFile(it) }
        }

        if (isWindowOpen(diffContext)) {
            diffContext.windowDialog?.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    private fun openEditorTab(diffContext: ActiveDiffContext) {
        val diffFile = diffContext.editorTabFile ?: JsonDiffVirtualFile(
            project = project,
            diffService = this,
            leftDocument = diffContext.leftDocument,
            rightDocument = diffContext.rightDocument,
            sortKeys = diffContext.sortKeys
        ).also { diffContext.editorTabFile = it }

        DiffEditorTabFilesManager.getInstance(project).showDiffFile(diffFile, true)
    }

    private fun openWindowDialog(diffContext: ActiveDiffContext) {
        val existingDialog = diffContext.windowDialog
        if (existingDialog != null && existingDialog.isOpen()) {
            existingDialog.showOrFocus()
            return
        }

        val dialog = JsonDiffWindowDialog(
            project = project,
            diffRequest = createDiffRequest(
                leftDocument = diffContext.leftDocument,
                rightDocument = diffContext.rightDocument,
                semantic = diffContext.sortKeys
            ),
            onClosed = {
                if (activeDiffContext === diffContext) {
                    diffContext.windowDialog = null
                }
            }
        )

        diffContext.windowDialog = dialog
        dialog.showOrFocus()
    }

    private fun createDiffDocument(initialText: String): Document {
        return EditorFactory.getInstance().createDocument(initialText)
    }

    private fun replaceDocumentText(document: Document, newText: String) {
        if (document.text == newText) return

        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(newText)
        }
    }

    private fun prepareDiffText(json: String, semantic: Boolean): String {
        val (isValid, formattedJson) = validateAndFormat(json, semantic)
        return if (isValid && formattedJson != null) formattedJson else json
    }

    private fun createDiffContent(document: Document): com.intellij.diff.contents.DocumentContent {
        val json5FileType = FileTypeManager.getInstance().getFileTypeByExtension("json5")
        val fileType = if (json5FileType is UnknownFileType) JsonFileType.INSTANCE else json5FileType
        return DiffContentFactory.getInstance().create(project, document, fileType)
    }
}
