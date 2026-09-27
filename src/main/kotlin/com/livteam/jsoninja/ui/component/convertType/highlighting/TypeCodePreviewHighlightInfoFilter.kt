package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile

class TypeCodePreviewHighlightInfoFilter : HighlightInfoFilter {
    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (file?.virtualFile?.getUserData(TypeCodePreviewHighlighting.IS_TYPE_PREVIEW) != true) return true
        // Generated fragments have no project dependencies; keep native colors, not compile diagnostics.
        return highlightInfo.severity < HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING
    }
}
