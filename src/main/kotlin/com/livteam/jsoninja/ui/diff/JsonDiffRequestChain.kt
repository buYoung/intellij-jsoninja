package com.livteam.jsoninja.ui.diff

import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.project.Project
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
    val semantic: Boolean = false
) : SimpleDiffRequestChain(createInitialRequest(diffService, project, leftJson, rightJson, semantic)) {
    
    init {
        // Add context actions to the diff request
        val contextActions = listOf(SwitchDiffDisplayModeAction(this))
        putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, contextActions)
    }
    
    companion object {
        private fun createInitialRequest(
            diffService: JsonDiffService,
            project: Project,
            leftJson: String,
            rightJson: String,
            semantic: Boolean
        ): SimpleDiffRequest {
            return diffService.createDiffRequest(leftJson, rightJson, semantic = semantic)
        }
    }
}