package com.livteam.jsoninja.ui.component.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.LightweightHint
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.utils.JsonPathHelper
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JsonEditorTooltipListener(
    private val project: Project,
    private val editorContainer: JComponent,
    parentDisposable: Disposable,
) : EditorMouseMotionListener {

    companion object {
        private const val HINT_DELAY_MS = 200
    }

    private val settings = JsoninjaSettingsState.getInstance(project)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tooltipJob: Job? = null

    @Volatile
    private var pendingTooltip: PendingTooltip? = null
    private var activeHint: LightweightHint? = null

    init {
        Disposer.register(parentDisposable) {
            tooltipJob?.cancel()
            coroutineScope.cancel()
            activeHint?.hide()
            activeHint = null
            pendingTooltip = null
        }
    }

    override fun mouseMoved(e: EditorMouseEvent) {
        if (e.editor.project != project) return

        if (!SwingUtilities.isDescendingFrom(e.editor.component, editorContainer)) {
            return
        }

        val event = e.mouseEvent
        val isModifierDown = if (SystemInfo.isMac) event.isMetaDown else event.isControlDown

        val editorEx = e.editor as? EditorEx ?: return
        if (!isModifierDown) {
            tooltipJob?.cancel()
            cancelPending()
            if (editorEx.contentComponent.toolTipText != null) {
                editorEx.contentComponent.toolTipText = null
            }
            return
        }

        val offset = e.offset
        tooltipJob?.cancel()
        tooltipJob = coroutineScope.launch {
            try {
                val result = readAction {
                    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(e.editor.document) ?: return@readAction null
                    val element = psiFile.findElementAt(offset)
                    val queryType = JsonQueryType.fromString(settings.jsonQueryType)
                    val useDotNotation = queryType == JsonQueryType.JMESPATH || queryType == JsonQueryType.JACKSON_JQ

                    val templateResult = JsonPathHelper.getPathFromTemplateText(
                        documentText = e.editor.document.text,
                        offset = offset,
                        project = project,
                        isJmes = useDotNotation,
                    )

                    val resolvedPath = templateResult?.path ?: if (element != null) {
                        when (queryType) {
                            JsonQueryType.JMESPATH -> JsonPathHelper.getJmesPath(element)
                            JsonQueryType.JAYWAY_JSONPATH -> JsonPathHelper.getJsonPath(element)
                            JsonQueryType.JACKSON_JQ -> JsonPathHelper.getJqPath(element)
                        }
                    } else null

                    if (resolvedPath == null) return@readAction null

                    val label = when (queryType) {
                        JsonQueryType.JMESPATH -> "JMESPath"
                        JsonQueryType.JAYWAY_JSONPATH -> "Jayway JsonPath"
                        JsonQueryType.JACKSON_JQ -> "jq"
                    }

                    TooltipResult(
                        text = "<html>$label: <b>$resolvedPath</b></html>",
                        isTemplatePlaceholder = templateResult?.isInsidePlaceholder == true,
                    )
                }

                if (result != null && result.isTemplatePlaceholder) {
                    val shouldSkip = withContext(Dispatchers.EDT) {
                        if (editorEx.isDisposed) {
                            return@withContext true
                        }
                        editorEx.contentComponent.toolTipText = null
                        activeHint?.isVisible == true && pendingTooltip?.text == result.text
                    }
                    if (shouldSkip) {
                        return@launch
                    }

                    val pending = PendingTooltip(offset, result.text)
                    pendingTooltip = pending
                    delay(HINT_DELAY_MS.toLong())
                    withContext(Dispatchers.EDT) {
                        if (editorEx.isDisposed) {
                            return@withContext
                        }
                        if (pendingTooltip == pending) {
                            showTemplateHint(editorEx, result.text, offset)
                        }
                    }
                } else {
                    withContext(Dispatchers.EDT) {
                        if (editorEx.isDisposed) {
                            return@withContext
                        }
                        cancelPending()
                        editorEx.contentComponent.toolTipText = result?.text
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            }
        }
    }

    private fun showTemplateHint(editor: EditorEx, text: String, offset: Int) {
        activeHint?.hide()
        activeHint = null

        val label = HintUtil.createInformationLabel(text)
        val hint = LightweightHint(label)
        activeHint = hint

        val logicalPos = editor.offsetToLogicalPosition(offset)
        val point = HintManagerImpl.getHintPosition(hint, editor, logicalPos, HintManager.UNDER)

        val flags = HintManagerImpl.HIDE_BY_ANY_KEY or
            HintManagerImpl.HIDE_BY_TEXT_CHANGE or
            HintManagerImpl.HIDE_BY_SCROLLING or
            HintManagerImpl.HIDE_BY_OTHER_HINT
        HintManagerImpl.getInstanceImpl().showEditorHint(
            hint, editor, point, flags, 0, false,
        )
    }

    private fun cancelPending() {
        pendingTooltip = null
        activeHint?.hide()
        activeHint = null
    }

    private data class TooltipResult(
        val text: String,
        val isTemplatePlaceholder: Boolean,
    )

    private data class PendingTooltip(
        val offset: Int,
        val text: String?,
    )
}
