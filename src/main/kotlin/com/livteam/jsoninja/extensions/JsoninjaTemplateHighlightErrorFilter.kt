package com.livteam.jsoninja.extensions

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.lang.annotation.HighlightSeverity
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.TemplatePlaceholderSupport
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import java.util.Collections
import java.util.WeakHashMap

class JsoninjaTemplateHighlightErrorFilter : HighlightInfoFilter {
    private data class CachedSuppressionDecision(
        val modificationStamp: Long,
        val shouldSuppress: Boolean
    )

    private val suppressionCache = Collections.synchronizedMap(
        WeakHashMap<Document, CachedSuppressionDecision>()
    )

    private val objectMapper by lazy {
        val application = ApplicationManager.getApplication()
        if (application != null) {
            application.getService(JsonObjectMapperService::class.java)?.objectMapper
                ?: JsonObjectMapperService().objectMapper
        } else {
            JsonObjectMapperService().objectMapper
        }
    }

    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (file == null) return true

        if (highlightInfo.severity != HighlightSeverity.ERROR) {
            return true
        }

        val project = file.project
        if (project.isDisposed) return true

        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        if (!isJsoninjaEditorFile(file, document)) {
            return true
        }

        val shouldSuppress = if (document != null) {
            getSuppressionDecision(document)
        } else {
            shouldSuppressForDocumentText(file.text)
        }
        return !shouldSuppress
    }

    private fun isJsoninjaEditorFile(file: PsiFile, document: Document?): Boolean {
        if (document?.getUserData(JsonDocumentFactory.JSONINJA_EDITOR_KEY) == true) {
            return true
        }

        if (file.getUserData(JsonDocumentFactory.JSONINJA_PSI_FILE_KEY) == true) {
            return true
        }

        val isJsonLikeFileType = file.fileType.defaultExtension == "json" || file.fileType.defaultExtension == "json5"
        val isJsoninjaDummyFile = file.virtualFile == null && file.name.startsWith("Dummy.") && isJsonLikeFileType
        return isJsoninjaDummyFile
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
