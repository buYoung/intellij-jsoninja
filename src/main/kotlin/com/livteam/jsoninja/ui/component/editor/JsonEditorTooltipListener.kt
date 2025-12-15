package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.util.JsonPathHelper
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

        if (isModifierDown) {
            val offset = e.offset
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(e.editor.document)
            if (psiFile != null) {
                val element = psiFile.findElementAt(offset)

                if (element != null) {
                    val queryType = JsonQueryType.fromString(settings.jsonQueryType)
                    val path = when (queryType) {
                        JsonQueryType.JMESPATH -> JsonPathHelper.getJmesPath(element)
                        JsonQueryType.JAYWAY_JSONPATH -> JsonPathHelper.getJsonPath(element)
                    }

                    if (path != null) {
                        val label = when (queryType) {
                            JsonQueryType.JMESPATH -> "JMESPath"
                            JsonQueryType.JAYWAY_JSONPATH -> "Jayway JsonPath"
                        }
                        val text = "<html>$label: <b>$path</b></html>"
                        (e.editor as? EditorEx)?.contentComponent?.toolTipText = text
                    } else {
                        (e.editor as? EditorEx)?.contentComponent?.toolTipText = null
                    }
                }
            } else {
                (e.editor as? EditorEx)?.contentComponent?.toolTipText = null
            }
        } else {
            (e.editor as? EditorEx)?.contentComponent?.toolTipText = null
        }
    }
}
