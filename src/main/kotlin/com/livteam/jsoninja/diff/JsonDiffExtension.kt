package com.livteam.jsoninja.diff

import com.fasterxml.jackson.core.JsonProcessingException
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.json.JsonFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Extension for JSON diff viewer that provides automatic JSON formatting.
 * 
 * This extension detects JSON content in diff viewers and applies automatic formatting
 * with performance optimizations including:
 * - Fast JSON content detection with early exits
 * - Per-document state tracking to prevent memory leaks
 * - Debounced formatting to avoid excessive processing
 * - Re-entrant update protection
 * 
 * Threading: All document operations are performed on EDT. Heavy JSON parsing
 * is moved to background threads where possible.
 */
class JsonDiffExtension : DiffExtension() {
    
    private object Constants {
        const val DEBOUNCE_DELAY = 300 // milliseconds
        const val SMALL_EDIT_THRESHOLD = 3 // characters
        const val MAX_BYTES_FOR_JSON_DETECTION = 1_500_000 // 1.5 MB
        const val MAX_BYTES_FOR_JSON_PROCESSING = 2_000_000 // 2 MB
        val CHANGE_GUARD_KEY: Key<Boolean> = Key.create("JSONINJA_DIFF_CHANGE_GUARD")
    }
    
    /**
     * Lightweight per-document state to optimize processing and prevent memory leaks.
     * Access should be synchronized through documentStates map.
     */
    private data class DocumentState(
        var lastContentHash: Int = 0,
        var lastChangeTime: Long = 0L,
        var lastEditSize: Int = 0,
        val isSelfUpdate: AtomicBoolean = AtomicBoolean(false),
        var detectionResult: JsonDetectionResult = JsonDetectionResult.UNKNOWN
    )
    
    private enum class JsonDetectionResult {
        YES, NO, UNKNOWN
    }
    
    companion object {
        private val LOG = Logger.getInstance(JsonDiffExtension::class.java)
        
        // Synchronized WeakHashMap to track per-document state without strong references
        private val documentStates = Collections.synchronizedMap(WeakHashMap<Document, DocumentState>())
        
        /**
         * Get or create document state for the given document.
         * Thread-safe access to per-document state.
         */
        private fun getDocumentState(document: Document): DocumentState {
            return documentStates.computeIfAbsent(document) { DocumentState() }
        }
    }
    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        // Only apply to EditorDiffViewer (text diffs)
        if (viewer !is EditorDiffViewer) {
            LOG.debug("Skipping non-EditorDiffViewer: ${viewer.javaClass.simpleName}")
            return
        }

        val editors = viewer.editors
        if (editors.size != 2) {
            LOG.debug("Skipping diff with ${editors.size} editors (expected 2)")
            return
        }

        // Get project and services
        val project = context.project
        if (project == null) {
            LOG.debug("No project available in DiffContext, skipping JSON diff extension")
            return
        }
        
        val formatterService = project.service<JsonFormatterService>()
        val projectName = project.name

        // Determine if both sides are JSON; final validation goes through JsonFormatterService.isValidJson
        val startTime = System.currentTimeMillis()
        val isJsonDiff = editors.all { editor -> isJsonContent(editor, formatterService, projectName) }
        val detectionTime = System.currentTimeMillis() - startTime
        
        if (LOG.isDebugEnabled) {
            LOG.debug("JSON detection completed in ${detectionTime}ms for project '$projectName', result: $isJsonDiff")
        }
        
        if (!isJsonDiff) return

        // Install listeners for both editors
        editors.forEach { editor ->
            installAutoFormatter(project, editor, viewer, formatterService)
        }
        
        LOG.debug("JSON diff extension activated for project '$projectName' with ${editors.size} editors")
    }

    /**
     * Determines if editor content is JSON using a fast, multi-phase approach.
     * Final validation always goes through JsonFormatterService.isValidJson as required.
     * 
     * @param editor The editor to check
     * @param formatterService Service for JSON validation
     * @param projectName Project name for logging context
     * @return true if content is detected as JSON
     */
    private fun isJsonContent(editor: Editor, formatterService: JsonFormatterService, projectName: String): Boolean {
        val document = editor.document
        val state = getDocumentState(document)
        val fileName = editor.virtualFile?.name ?: "<unknown>"
        
        // Check cached detection result first
        if (state.detectionResult != JsonDetectionResult.UNKNOWN) {
            LOG.debug("Using cached JSON detection result for '$fileName': ${state.detectionResult}")
            return state.detectionResult == JsonDetectionResult.YES
        }
        
        try {
            // Phase 1: Fast path - file type check
            if (editor.virtualFile?.fileType == JsonFileType.INSTANCE) {
                LOG.debug("File '$fileName' detected as JSON via file type")
                state.detectionResult = JsonDetectionResult.YES
                return true
            }

            val text = document.text
            if (text.isBlank()) {
                LOG.debug("File '$fileName' is blank, not JSON")
                state.detectionResult = JsonDetectionResult.NO
                return false
            }
            
            // Phase 2: Size check
            if (text.length > Constants.MAX_BYTES_FOR_JSON_DETECTION) {
                LOG.debug("File '$fileName' too large (${text.length} bytes) for JSON detection, skipping")
                state.detectionResult = JsonDetectionResult.NO
                return false
            }

            // Phase 3: Heuristic check
            val trimmed = text.trim()
            if (!(trimmed.startsWith('{') || trimmed.startsWith('['))) {
                LOG.debug("File '$fileName' does not start with JSON delimiters")
                state.detectionResult = JsonDetectionResult.NO
                return false
            }

            // Phase 4: Mandatory validation via JsonFormatterService
            val isValid = formatterService.isValidJson(trimmed)
            state.detectionResult = if (isValid) JsonDetectionResult.YES else JsonDetectionResult.NO
            
            if (LOG.isDebugEnabled) {
                LOG.debug("File '$fileName' JSON validation result: $isValid (project: '$projectName')")
            }
            
            return isValid
            
        } catch (e: OutOfMemoryError) {
            LOG.error("OutOfMemoryError during JSON detection for file '$fileName' (${document.textLength} chars)", e)
            state.detectionResult = JsonDetectionResult.NO
            return false
        } catch (e: Exception) {
            LOG.warn("Error during JSON detection for file '$fileName'", e)
            state.detectionResult = JsonDetectionResult.NO
            return false
        }
    }
    /**
     * Installs document change listener with optimized debouncing and small edit detection.
     * 
     * @param project The project context
     * @param editor The editor to monitor
     * @param viewer The diff viewer for disposal registration
     * @param formatterService Service for JSON formatting
     */
    private fun installAutoFormatter(
        project: Project,
        editor: Editor,
        viewer: FrameDiffTool.DiffViewer,
        formatterService: JsonFormatterService
    ) {
        val document = editor.document
        val state = getDocumentState(document)
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, viewer)
        val fileName = editor.virtualFile?.name ?: "<unknown>"

        val documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                // Skip if this change originated from our own formatter
                if (document.getUserData(Constants.CHANGE_GUARD_KEY) == true) {
                    LOG.debug("Skipping self-update for '$fileName'")
                    return
                }
                
                if (state.isSelfUpdate.get()) {
                    LOG.debug("Skipping self-update (atomic flag) for '$fileName'")
                    return
                }
                
                val currentTime = System.currentTimeMillis()
                val editSize = abs(event.newLength - event.oldLength)
                state.lastEditSize = editSize
                state.lastChangeTime = currentTime
                
                // Skip small whitespace-only edits
                if (editSize <= Constants.SMALL_EDIT_THRESHOLD) {
                    val changedText = event.newFragment.toString()
                    if (changedText.isBlank() || changedText.all { it.isWhitespace() }) {
                        LOG.debug("Skipping small whitespace edit ($editSize chars) for '$fileName'")
                        return
                    }
                }
                
                // Skip if document is too large for processing
                if (document.textLength > Constants.MAX_BYTES_FOR_JSON_PROCESSING) {
                    LOG.debug("Document '$fileName' too large (${document.textLength} chars) for JSON processing")
                    return
                }

                // Cancel any pending formatting
                alarm.cancelAllRequests()

                // Schedule new formatting with debounce
                alarm.addRequest({
                    if (!project.isDisposed) {
                        scheduleJsonFormatting(project, document, formatterService, fileName)
                    }
                }, Constants.DEBOUNCE_DELAY)
            }
        }

        document.addDocumentListener(documentListener)

        // Remove listener and cleanup when viewer is disposed
        Disposer.register(viewer) {
            document.removeDocumentListener(documentListener)
            alarm.cancelAllRequests()
            // Let WeakHashMap naturally clean up document state
            LOG.debug("Disposed JSON diff extension for '$fileName'")
        }

        // Format initially if there's content
        if (document.text.isNotBlank()) {
            scheduleJsonFormatting(project, document, formatterService, fileName)
        }
    }

    /**
     * Schedules JSON formatting work on a background thread, then applies changes on EDT.
     * Uses ReadAction for safe document access and WriteCommandAction for changes.
     */
    private fun scheduleJsonFormatting(
        project: Project,
        document: Document,
        formatterService: JsonFormatterService,
        fileName: String
    ) {
        if (project.isDisposed) return
        
        ApplicationManager.getApplication().executeOnPooledThread {
            val formattedResult = formatJsonInBackground(document, formatterService, fileName)
            if (formattedResult != null && !project.isDisposed) {
                ApplicationManager.getApplication().invokeLater({
                    applyJsonFormatting(project, document, formattedResult, fileName)
                }, ModalityState.defaultModalityState())
            }
        }
    }
    
    /**
     * Performs JSON formatting computation in background thread.
     * Returns null if formatting should be skipped or failed.
     */
    private fun formatJsonInBackground(
        document: Document,
        formatterService: JsonFormatterService,
        fileName: String
    ): String? {
        // Avoid re-entrant updates
        if (document.getUserData(Constants.CHANGE_GUARD_KEY) == true) {
            LOG.debug("Skipping formatting due to change guard for '$fileName'")
            return null
        }
        
        val state = getDocumentState(document)
        if (state.isSelfUpdate.get()) {
            LOG.debug("Skipping formatting due to self-update flag for '$fileName'")
            return null
        }

        val text = document.text
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            LOG.debug("Document '$fileName' is empty, skipping formatting")
            return null
        }
        
        // Check content hash to avoid unnecessary work
        val contentHash = trimmed.hashCode()
        if (state.lastContentHash == contentHash) {
            LOG.debug("Document '$fileName' content unchanged, skipping formatting")
            return null
        }

        return try {
            val startTime = System.currentTimeMillis()
            // JsonFormatterService internally validates via isValidJson as required
            val formatted = formatterService.formatJson(trimmed, JsonFormatState.PRETTIFY)
            val formatTime = System.currentTimeMillis() - startTime
            
            state.lastContentHash = contentHash
            
            if (LOG.isDebugEnabled) {
                LOG.debug("JSON formatting completed in ${formatTime}ms for '$fileName'")
            }
            
            if (formatted != trimmed) formatted else null
            
        } catch (e: ProcessCanceledException) {
            // Re-throw cancellation to properly handle background task interruption
            throw e
        } catch (e: JsonProcessingException) {
            LOG.debug("JSON processing failed for '$fileName': ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            LOG.error("OutOfMemoryError during JSON formatting for '$fileName' (${document.textLength} chars)", e)
            // Set flag to prevent retries for this document
            state.detectionResult = JsonDetectionResult.NO
            null
        } catch (e: IOException) {
            LOG.warn("IO error during JSON formatting for '$fileName'", e)
            null
        } catch (e: Exception) {
            LOG.warn("Unexpected error during JSON formatting for '$fileName'", e)
            null
        }
    }
    
    /**
     * Applies formatted JSON result to document on EDT with proper write action and guards.
     */
    private fun applyJsonFormatting(
        project: Project,
        document: Document,
        formatted: String,
        fileName: String
    ) {
        if (project.isDisposed) return
        
        val state = getDocumentState(document)
        
        // Final guard check on EDT
        if (document.getUserData(Constants.CHANGE_GUARD_KEY) == true || state.isSelfUpdate.get()) {
            LOG.debug("Skipping document update due to guard for '$fileName'")
            return
        }

        try {
            document.putUserData(Constants.CHANGE_GUARD_KEY, true)
            state.isSelfUpdate.set(true)
            
            WriteCommandAction.runWriteCommandAction(project) {
                document.setText(formatted)
            }
            
            LOG.debug("Applied JSON formatting to '$fileName'")
            
        } finally {
            document.putUserData(Constants.CHANGE_GUARD_KEY, false)
            state.isSelfUpdate.set(false)
        }
    }
}
