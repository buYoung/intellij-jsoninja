package com.livteam.jsoninja.ui.component.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.LightweightHint
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.utils.JsonPathHelper
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlinx.coroutines.*

class JsonEditorTooltipListener(
    private val project: Project,
    private val editorContainer: JComponent,
    parentDisposable: Disposable
) : EditorMouseMotionListener {

    companion object {
        private const val HINT_DELAY_MS = 200
    }

    private val settings: JsoninjaSettingsState
        get() = JsoninjaSettingsState.getInstance(project)
    private var tooltipJob: Job? = null
    @Volatile
    private var pendingTooltip: PendingTooltip? = null
    private var activeHint: LightweightHint? = null

    init {
        Disposer.register(parentDisposable) {
            cancelPending()
        }
    }

    override fun mouseMoved(e: EditorMouseEvent) {
        if (e.editor.project != project) return

        // Check if the event comes from our editor
        // We check if the editor component is a child of the editorContainer
        if (!SwingUtilities.isDescendingFrom(e.editor.component, editorContainer)) {
            return
        }

        val event = e.mouseEvent
        val isModifierDown = if (SystemInfo.isMac) event.isMetaDown else event.isControlDown

        val editorEx = e.editor as? EditorEx ?: return
        if (!isModifierDown) {
            cancelPending()
            if (editorEx.contentComponent.toolTipText != null) {
                editorEx.contentComponent.toolTipText = null
            }
            return
        }

        val offset = e.offset
        if (pendingTooltip?.offset == offset) {
            return
        }

        cancelPending()

        val pending = PendingTooltip(offset, null)
        pendingTooltip = pending

        tooltipJob = project.service<JsoninjaCoroutineScopeService>().launch {
            delay(HINT_DELAY_MS.toLong())
            if (pendingTooltip != pending) return@launch

            val result = try {
                readAction {
                    if (project.isDisposed || editorEx.isDisposed) return@readAction null
                    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editorEx.document) ?: return@readAction null
                    val element = psiFile.findElementAt(offset)
                    val queryType = JsonQueryType.fromString(settings.jsonQueryType)
                    val useDotNotation = queryType == JsonQueryType.JMESPATH || queryType == JsonQueryType.JACKSON_JQ

                    val templateResult = JsonPathHelper.getPathFromTemplateText(
                        documentText = editorEx.document.text,
                        offset = offset,
                        project = project,
                        isJmes = useDotNotation
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
                        isTemplatePlaceholder = templateResult?.isInsidePlaceholder == true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }

            if (project.isDisposed || editorEx.isDisposed) return@launch

            withContext(Dispatchers.EDT) {
                if (pendingTooltip != pending || project.isDisposed || editorEx.isDisposed) return@withContext
                if (result != null) {
                    if (result.isTemplatePlaceholder) {
                        editorEx.contentComponent.toolTipText = null
                        showTemplateHint(editorEx, result.text, offset)
                    } else {
                        editorEx.contentComponent.toolTipText = result.text
                    }
                } else {
                    editorEx.contentComponent.toolTipText = null
                }
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
            hint, editor, point, flags, 0, false
        )
    }

    private fun cancelPending() {
        pendingTooltip = null
        tooltipJob?.cancel()
        tooltipJob = null
        activeHint?.hide()
        activeHint = null
    }

    private data class TooltipResult(
        val text: String,
        val isTemplatePlaceholder: Boolean
    )

    private data class PendingTooltip(
        val offset: Int,
        val text: String?
    )
}
