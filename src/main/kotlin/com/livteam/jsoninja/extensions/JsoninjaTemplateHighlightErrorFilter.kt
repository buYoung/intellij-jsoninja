package com.livteam.jsoninja.extensions

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.TemplatePlaceholderSupport
import com.livteam.jsoninja.ui.component.editor.JsonDocumentFactory
import java.util.Collections
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException

class JsoninjaTemplateHighlightErrorFilter : HighlightInfoFilter {
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
            project.service<TemplateHighlightErrorCacheService>().getOrCompute(document) {
                shouldSuppressForDocumentText(document.text)
            }
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

    private fun resolveObjectMapper(): ObjectMapper {
        val application = ApplicationManager.getApplication()
        if (application != null && !application.isDisposed) {
            application.getService(JsonObjectMapperService::class.java)?.objectMapper?.let { return it }
        }
        return JsonObjectMapperService().objectMapper
    }

    private fun shouldSuppressForDocumentText(documentText: String): Boolean {
        if (documentText.isBlank()) return false

        val replacementResult = TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders(documentText)
        if (!replacementResult.isSuccessful || replacementResult.mappings.isEmpty()) {
            return false
        }

        return try {
            resolveObjectMapper().factory.createParser(replacementResult.replacedText).use { parser ->
                parser.nextToken()
                parser.skipChildren()
                parser.nextToken() == null
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (cancellationException: ProcessCanceledException) {
            throw cancellationException
        } catch (_: Exception) {
            // Fail-open: keep default highlighting behavior when validation fails unexpectedly.
            false
        }
    }
}

@Service(Service.Level.PROJECT)
internal class TemplateHighlightErrorCacheService : Disposable {
    private data class CachedSuppressionDecision(
        val modificationStamp: Long,
        val shouldSuppress: Boolean
    )

    private val suppressionCache = Collections.synchronizedMap(
        WeakHashMap<Document, CachedSuppressionDecision>()
    )

    fun getOrCompute(document: Document, compute: () -> Boolean): Boolean {
        val currentModificationStamp = document.modificationStamp
        val cachedSuppressionDecision = suppressionCache[document]
        if (cachedSuppressionDecision != null &&
            cachedSuppressionDecision.modificationStamp == currentModificationStamp
        ) {
            return cachedSuppressionDecision.shouldSuppress
        }

        val computedSuppressionDecision = compute()
        suppressionCache[document] = CachedSuppressionDecision(
            modificationStamp = currentModificationStamp,
            shouldSuppress = computedSuppressionDecision
        )
        return computedSuppressionDecision
    }

    override fun dispose() {
        suppressionCache.clear()
    }
}
