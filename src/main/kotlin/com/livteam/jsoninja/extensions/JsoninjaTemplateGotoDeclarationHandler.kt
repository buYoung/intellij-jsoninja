package com.livteam.jsoninja.extensions

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.livteam.jsoninja.services.TemplatePlaceholderSupport
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory

/**
 * Template placeholder 내부의 텍스트가 JSON key로 인식되어
 * Ctrl/Cmd+hover 시 navigation UI(밑줄, 손 커서)가 표시되는 문제를 방지.
 *
 * JSONinja 에디터에서 offset이 {{...}} value placeholder 범위 안에 있으면
 * 빈 배열을 반환하여 IntelliJ CtrlMouseHandler의 navigation 동작을 억제한다.
 */
class JsoninjaTemplateGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (editor == null) return null

        val document = editor.document
        if (document.getUserData(JsonDocumentFactory.JSONINJA_EDITOR_KEY) != true) return null

        val documentText = document.text
        if (!isOffsetInPlaceholderRegion(documentText, offset)) return null

        val result = TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders(documentText)
        if (!result.isSuccessful || result.mappings.isEmpty()) return null

        val isInValuePlaceholder = result.mappings.any { offset in it.originalStartIndex until it.originalEndIndex }
        if (!isInValuePlaceholder) return null

        return PsiElement.EMPTY_ARRAY
    }

    private fun isOffsetInPlaceholderRegion(text: String, offset: Int): Boolean {
        if (offset < 0 || offset >= text.length) return false
        val openIdx = text.lastIndexOf("{{", offset)
        if (openIdx < 0) return false
        val closeIdx = text.indexOf("}}", openIdx + 2)
        return closeIdx >= 0 && offset < closeIdx + 2
    }
}
