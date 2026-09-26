package com.livteam.jsoninja.ui.diff

import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.openapi.editor.Document
import com.livteam.jsoninja.diff.JsonDiffSession
import com.livteam.jsoninja.services.JsonDiffService

/**
 * Custom DiffRequestChain for JSON diff that maintains context and provides actions
 */
class JsonDiffRequestChain(
    diffService: JsonDiffService,
    val session: JsonDiffSession
) : SimpleDiffRequestChain(diffService.createDiffRequest(session)) {

    val leftDocument: Document
        get() = session.leftDocument

    val rightDocument: Document
        get() = session.rightDocument
}
