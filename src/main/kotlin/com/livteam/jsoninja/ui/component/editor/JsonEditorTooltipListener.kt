package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.utils.JsonPathHelper
import javax.swing.JComponent
import javax.swing.SwingUtilities

class JsonEditorTooltipListener(
    private val project: Project,
    private val editorContainer: JComponent
) : EditorMouseMotionListener {

    private val settings = JsoninjaSettingsState.getInstance(project)

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
            if (editorEx.contentComponent.toolTipText != null) {
                editorEx.contentComponent.toolTipText = null
            }
            return
        }

        val offset = e.offset
        val tooltipText = ReadAction.compute<String?, RuntimeException> {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(e.editor.document) ?: return@compute null
            val element = psiFile.findElementAt(offset) ?: return@compute null
            val queryType = JsonQueryType.fromString(settings.jsonQueryType)
            val path = when (queryType) {
                JsonQueryType.JMESPATH -> JsonPathHelper.getJmesPath(element)
                JsonQueryType.JAYWAY_JSONPATH -> JsonPathHelper.getJsonPath(element)
            } ?: return@compute null

            val label = when (queryType) {
                JsonQueryType.JMESPATH -> "JMESPath"
                JsonQueryType.JAYWAY_JSONPATH -> "Jayway JsonPath"
            }

            "<html>$label: <b>$path</b></html>"
        }

        if (editorEx.contentComponent.toolTipText != tooltipText) {
            editorEx.contentComponent.toolTipText = tooltipText
        }
    }
}
