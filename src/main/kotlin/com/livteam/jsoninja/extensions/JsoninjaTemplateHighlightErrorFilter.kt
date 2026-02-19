package com.livteam.jsoninja.extensions

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.TemplatePlaceholderSupport
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import java.util.Collections
import java.util.WeakHashMap

class JsoninjaTemplateHighlightErrorFilter : HighlightErrorFilter() {
    private data class CachedSuppressionDecision(
        val modificationStamp: Long,
        val shouldSuppress: Boolean
    )

    private val suppressionCache = Collections.synchronizedMap(
        WeakHashMap<Document, CachedSuppressionDecision>()
    )

    override fun shouldHighlightErrorElement(errorElement: PsiErrorElement): Boolean {
        val containingFile = errorElement.containingFile ?: return true
        val project = containingFile.project ?: return true
        if (project.isDisposed) return true

        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile) ?: return true
        if (document.getUserData(JsonDocumentFactory.JSONINJA_EDITOR_KEY) != true) {
            return true
        }

        val shouldSuppress = getSuppressionDecision(document)
        return !shouldSuppress
    }

    private fun getSuppressionDecision(document: Document): Boolean {
        val currentModificationStamp = document.modificationStamp
        val cachedSuppressionDecision = suppressionCache[document]
        if (cachedSuppressionDecision != null &&
            cachedSuppressionDecision.modificationStamp == currentModificationStamp
        ) {
            return cachedSuppressionDecision.shouldSuppress
        }

        val computedSuppressionDecision = shouldSuppressForDocumentText(document.text)
        suppressionCache[document] = CachedSuppressionDecision(
            modificationStamp = currentModificationStamp,
            shouldSuppress = computedSuppressionDecision
        )
        return computedSuppressionDecision
    }

    private fun shouldSuppressForDocumentText(documentText: String): Boolean {
        if (documentText.isBlank()) return false

        val replacementResult = TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders(documentText)
        if (!replacementResult.isSuccessful || replacementResult.mappings.isEmpty()) {
            return false
        }

        return try {
            val objectMapper = ApplicationManager.getApplication()
                .getService(JsonObjectMapperService::class.java)
                .objectMapper

            objectMapper.factory.createParser(replacementResult.replacedText).use { parser ->
                parser.nextToken()
                parser.skipChildren()
                parser.nextToken() == null
            }
        } catch (_: Exception) {
            // Fail-open: keep default highlighting behavior when validation fails unexpectedly.
            false
        }
    }
}
