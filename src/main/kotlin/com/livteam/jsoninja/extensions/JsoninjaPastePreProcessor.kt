package com.livteam.jsoninja.extensions

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.json.JsonElementTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.psi.tree.IElementType
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pre-processes pasted content in JSONinja editors.
 * Automatically formats valid JSON content upon paste according to plugin settings.
 */
class JsoninjaPastePreProcessor : CopyPastePreProcessor {

    companion object {
        // Above this threshold, return raw text synchronously so PasteHandler never spins a nested modal
        // progress loop inside CommandProcessor / WriteIntentReadAction, and defer formatting off-EDT.
        private const val SYNC_PROCESSING_THRESHOLD = 500_000
        private val PASTE_FORMAT_SEQUENCE_KEY = Key.create<Long>("JSONINJA_PASTE_FORMAT_SEQUENCE")
    }

    override fun requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean = false

    override fun preprocessOnPaste(
        project: Project,
        file: PsiFile,
        editor: Editor,
        text: String,
        rawText: RawText?
    ): String {
        val document = editor.document
        if (document.getUserData(JsonDocumentFactory.JSONINJA_EDITOR_KEY) != true) {
            return text
        }

        val requestSequence = (document.getUserData(PASTE_FORMAT_SEQUENCE_KEY) ?: 0L) + 1L
        document.putUserData(PASTE_FORMAT_SEQUENCE_KEY, requestSequence)

        if (text.isBlank() || isInsideStringOrComment(editor)) {
            return text
        }

        val formatterService = project.service<JsonFormatterService>()
        val settings = JsoninjaSettingsState.getInstance(project)
        val pasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)

        if (text.length < SYNC_PROCESSING_THRESHOLD) {
            return formatSafely(formatterService, text, pasteFormatState)
        }

        scheduleDeferredFormatting(
            project = project,
            editor = editor,
            originalText = text,
            pasteFormatState = pasteFormatState,
            formatterService = formatterService,
            requestSequence = requestSequence
        )
        return text
    }

    override fun preprocessOnCopy(
        file: PsiFile,
        startOffsets: IntArray,
        endOffsets: IntArray,
        text: String
    ): String? {
        // We don't need to modify copied text
        return null
    }

    private fun formatSafely(
        formatterService: JsonFormatterService,
        text: String,
        pasteFormatState: JsonFormatState
    ): String {
        return try {
            formatterService.formatJsonIfValid(text, pasteFormatState)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            text
        }
    }

    private fun scheduleDeferredFormatting(
        project: Project,
        editor: Editor,
        originalText: String,
        pasteFormatState: JsonFormatState,
        formatterService: JsonFormatterService,
        requestSequence: Long
    ) {
        val document = editor.document
        val modalityState = ModalityState.stateForComponent(editor.contentComponent)
        project.service<JsoninjaCoroutineScopeService>().launch {
            val formattedText = withContext(Dispatchers.Default) {
                formatSafely(formatterService, originalText, pasteFormatState)
            }
            if (formattedText == originalText) return@launch

            withContext(Dispatchers.EDT + modalityState.asContextElement()) {
                if (project.isDisposed || editor.isDisposed) return@withContext
                if (document.getUserData(PASTE_FORMAT_SEQUENCE_KEY) != requestSequence) return@withContext

                val pastedRange = editor.getUserData(EditorEx.LAST_PASTED_REGION) ?: return@withContext
                if (pastedRange.startOffset < 0 || pastedRange.endOffset > document.textLength) return@withContext

                val pastedSegment = document.immutableCharSequence.subSequence(
                    pastedRange.startOffset,
                    pastedRange.endOffset
                )
                if (!pastedSegment.contentEquals(originalText)) return@withContext

                CommandProcessor.getInstance().runUndoTransparentAction {
                    ApplicationManager.getApplication().runWriteAction {
                        if (project.isDisposed || editor.isDisposed) return@runWriteAction
                        if (document.getUserData(PASTE_FORMAT_SEQUENCE_KEY) != requestSequence) return@runWriteAction
                        if (pastedRange.endOffset > document.textLength) return@runWriteAction

                        val currentSegment = document.immutableCharSequence.subSequence(
                            pastedRange.startOffset,
                            pastedRange.endOffset
                        )
                        if (!currentSegment.contentEquals(originalText)) return@runWriteAction

                        document.replaceString(pastedRange.startOffset, pastedRange.endOffset, formattedText)
                        val newEndOffset = pastedRange.startOffset + formattedText.length
                        editor.caretModel.moveToOffset(newEndOffset)
                        editor.putUserData(
                            EditorEx.LAST_PASTED_REGION,
                            TextRange(pastedRange.startOffset, newEndOffset)
                        )
                    }
                }
            }
        }
    }

    private fun isInsideStringOrComment(editor: Editor): Boolean {
        val document = editor.document
        val textLength = document.textLength
        if (textLength == 0) return false

        val selectionModel = editor.selectionModel
        val selectionStart = selectionModel.selectionStart
        val selectionEnd = selectionModel.selectionEnd
        // Full-document replacement (e.g., Select All + Paste) always starts a fresh JSON document.
        if (selectionStart == 0 && selectionEnd == textLength) return false
        if (selectionStart <= 0) return false

        val probeOffset = (selectionStart - 1).coerceIn(0, textLength - 1)
        val iterator = editor.highlighter.createIterator(probeOffset)
        if (iterator.atEnd()) return false

        val tokenType = iterator.tokenType
        val chars = document.immutableCharSequence

        if (isStringToken(tokenType)) {
            val (literalStart, literalEnd) = resolveFullStringLiteralBounds(iterator)
            if (literalEnd <= literalStart) return false
            val openingQuote = chars[literalStart]
            if (openingQuote != '"' && openingQuote != '\'') return false

            val isClosed = literalEnd - literalStart >= 2 &&
                chars[literalEnd - 1] == openingQuote &&
                !isEscapedQuote(chars, literalStart, literalEnd - 1)
            return if (isClosed) {
                selectionStart > literalStart && selectionEnd < literalEnd
            } else {
                selectionStart > literalStart
            }
        }

        val tokenStart = iterator.start
        val tokenEnd = iterator.end
        return when (tokenType) {
            JsonElementTypes.LINE_COMMENT -> selectionStart > tokenStart && selectionStart <= tokenEnd
            JsonElementTypes.BLOCK_COMMENT -> {
                val isClosed = tokenEnd - tokenStart >= 4 &&
                    chars[tokenEnd - 2] == '*' &&
                    chars[tokenEnd - 1] == '/'
                if (isClosed) {
                    selectionStart > tokenStart + 1 && selectionEnd < tokenEnd - 1
                } else {
                    selectionStart > tokenStart + 1
                }
            }
            else -> false
        }
    }

    private fun isStringToken(tokenType: IElementType?): Boolean {
        return tokenType == JsonElementTypes.DOUBLE_QUOTED_STRING ||
            tokenType == JsonElementTypes.SINGLE_QUOTED_STRING ||
            isStringEscapeToken(tokenType)
    }

    private fun isStringEscapeToken(tokenType: IElementType?): Boolean {
        return tokenType == StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN ||
            tokenType == StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN ||
            tokenType == StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN
    }

    private fun resolveFullStringLiteralBounds(iterator: HighlighterIterator): Pair<Int, Int> {
        var literalStart = iterator.start
        var literalEnd = iterator.end

        // Walk backward only across StringLiteralLexer escape boundaries so adjacent literals ("a""b") stay separate.
        while (!iterator.atEnd() && isStringToken(iterator.tokenType)) {
            val currentWasEscape = isStringEscapeToken(iterator.tokenType)
            literalStart = iterator.start
            if (iterator.start == 0) break
            iterator.retreat()
            if (iterator.atEnd() || !isStringToken(iterator.tokenType) ||
                (!currentWasEscape && !isStringEscapeToken(iterator.tokenType))
            ) {
                iterator.advance()
                break
            }
        }

        while (!iterator.atEnd() && isStringToken(iterator.tokenType)) {
            val currentWasEscape = isStringEscapeToken(iterator.tokenType)
            literalEnd = iterator.end
            iterator.advance()
            if (iterator.atEnd() || !isStringToken(iterator.tokenType) ||
                (!currentWasEscape && !isStringEscapeToken(iterator.tokenType))
            ) {
                break
            }
        }

        return literalStart to literalEnd
    }

    private fun isEscapedQuote(chars: CharSequence, literalStart: Int, quoteIndex: Int): Boolean {
        var backslashCount = 0
        var index = quoteIndex - 1
        while (index > literalStart && chars[index] == '\\') {
            backslashCount++
            index--
        }
        return backslashCount % 2 == 1
    }
}
