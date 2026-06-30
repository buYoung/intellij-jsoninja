package com.livteam.jsoninja.ui.diff

import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService

/**
 * VirtualFile for JSON diff that can be opened in editor tab
 */
class JsonDiffVirtualFile(
    project: Project,
    diffService: JsonDiffService,
    leftDocument: Document,
    rightDocument: Document,
    sortKeys: Boolean = false,
    name: String = LocalizationBundle.message("dialog.json.diff.title")
) : ChainDiffVirtualFile(
    JsonDiffRequestChain(diffService, leftDocument, rightDocument, sortKeys),
    name
) {
    // Store the chain for potential updates
    val jsonDiffChain: JsonDiffRequestChain
        get() = chain as JsonDiffRequestChain
}
