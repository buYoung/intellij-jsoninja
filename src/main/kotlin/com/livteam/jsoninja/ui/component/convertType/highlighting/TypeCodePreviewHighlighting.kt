package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.livteam.jsoninja.model.SupportedLanguage

internal object TypeCodePreviewHighlighting {
    val IS_TYPE_PREVIEW = Key.create<Boolean>("JSONINJA_TYPE_PREVIEW")

    fun enable(editor: EditorEx, extension: String) {
        if (SupportedLanguage.entries.none { it.fileExtension.equals(extension, ignoreCase = true) }) return
        val project = editor.project ?: return
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        val virtualFile = file.virtualFile ?: return
        virtualFile.putUserData(IS_TYPE_PREVIEW, true)
        // EditorTextField disables PSI highlighting for viewers before calling settings providers.
        // Reuse native language colors without making the generated code editable.
        DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, true)
    }
}

class TypeCodePreviewHighlightingSettingProvider : DefaultHighlightingSettingProvider(), DumbAware {
    override fun getDefaultSetting(project: Project, file: VirtualFile): FileHighlightingSetting? =
        if (file.getUserData(TypeCodePreviewHighlighting.IS_TYPE_PREVIEW) == true) FileHighlightingSetting.SKIP_INSPECTION else null
}
