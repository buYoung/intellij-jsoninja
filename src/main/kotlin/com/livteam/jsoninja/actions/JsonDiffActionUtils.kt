package com.livteam.jsoninja.actions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.diff.JsonDiffKeys
import com.livteam.jsoninja.diff.JsonDiffSession

/**
 * JSON Diff toolbar 액션이 공유하는 세션 대상
 */
class JsonDiffActionTarget(
    val project: Project,
    val session: JsonDiffSession,
    private val editors: List<Editor>
) {
    fun isHostActive(): Boolean {
        return editors.none { it.isDisposed }
    }

    fun showSortUnavailableHint() {
        val editor = editors.firstOrNull { !it.isDisposed } ?: return
        HintManager.getInstance().showErrorHint(
            editor,
            "JSONinja: ${LocalizationBundle.message("action.diff.sort.unavailable")}"
        )
    }
}

object JsonDiffActionUtils {

    /**
     * 현재 diff viewer가 JSONinja 세션의 두 Document를 표시할 때만 대상을 반환합니다.
     */
    fun getTarget(e: AnActionEvent): JsonDiffActionTarget? {
        val project = e.project ?: return null
        val viewer = e.getData(DiffDataKeys.DIFF_VIEWER) as? EditorDiffViewer ?: return null
        val session = e.getData(DiffDataKeys.DIFF_REQUEST)?.getUserData(JsonDiffKeys.JSON_DIFF_SESSION) ?: return null
        val editors = viewer.editors.toList()
        if (editors.size != 2) return null
        if (editors[0].document !== session.leftDocument || editors[1].document !== session.rightDocument) return null
        return JsonDiffActionTarget(project, session, editors)
    }
}
