package com.livteam.jsoninja.ui.diff

import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.editor.Document
import com.livteam.jsoninja.services.JsonDiffService

/**
 * Custom DiffRequestChain for JSON diff that maintains context and provides actions
 */
class JsonDiffRequestChain(
    private val diffService: JsonDiffService,
    val leftDocument: Document,
    val rightDocument: Document,
    val sortKeys: Boolean = false
) : SimpleDiffRequestChain(createInitialRequest(diffService, leftDocument, rightDocument, sortKeys)) {

    companion object {
        private fun createInitialRequest(
            diffService: JsonDiffService,
            leftDocument: Document,
            rightDocument: Document,
            sortKeys: Boolean
        ): SimpleDiffRequest {
            return diffService.createDiffRequest(leftDocument, rightDocument, semantic = sortKeys)
        }
    }
}
