package com.livteam.jsoninja.ui.diff

import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.actions.SortJsonDiffKeysOnceAction
import com.livteam.jsoninja.actions.SwitchDiffDisplayModeAction
import com.livteam.jsoninja.services.JsonDiffService

/**
 * Custom DiffRequestChain for JSON diff that maintains context and provides actions
 */
class JsonDiffRequestChain(
    private val project: Project,
    private val diffService: JsonDiffService,
    val leftJson: String,
    val rightJson: String,
    val sortKeys: Boolean = false
) : SimpleDiffRequestChain(createInitialRequest(diffService, project, leftJson, rightJson, sortKeys)) {
    
    init {
        // Add context actions to the diff request
        val contextActions = listOf(
            SwitchDiffDisplayModeAction(this),
            SortJsonDiffKeysOnceAction()
        )
        putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, contextActions)
    }
    
    companion object {
        private fun createInitialRequest(
            diffService: JsonDiffService,
            project: Project,
            leftJson: String,
            rightJson: String,
            sortKeys: Boolean
        ): SimpleDiffRequest {
            return diffService.createDiffRequest(leftJson, rightJson, semantic = sortKeys)
        }
    }
}
