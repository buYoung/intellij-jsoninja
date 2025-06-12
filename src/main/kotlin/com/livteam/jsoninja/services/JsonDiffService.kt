package com.livteam.jsoninja.services

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonFormatState

@Service(Service.Level.PROJECT)
class JsonDiffService(private val project: Project) {
    
    private val formatterService = project.service<JsonFormatterService>()

    fun showJsonDiff(leftJson: String, rightJson: String, title: String? = null, semantic: Boolean = false) {
        val diffTitle = title ?: LocalizationBundle.message("dialog.json.diff.title")
        
        val contentFactory = DiffContentFactory.getInstance()
        
        // Format JSON before showing diff
        val leftFormatted = if (semantic) {
            formatterService.formatJson(leftJson, JsonFormatState.PRETTIFY_SORTED)
        } else {
            formatterService.formatJson(leftJson, JsonFormatState.PRETTIFY)
        }
        
        val rightFormatted = if (semantic) {
            formatterService.formatJson(rightJson, JsonFormatState.PRETTIFY_SORTED)
        } else {
            formatterService.formatJson(rightJson, JsonFormatState.PRETTIFY)
        }
        
        val leftContent = createDiffContent(leftFormatted)
        val rightContent = createDiffContent(rightFormatted)
        
        val request = SimpleDiffRequest(
            diffTitle, 
            leftContent, 
            rightContent,
            LocalizationBundle.message("dialog.json.diff.left"),
            LocalizationBundle.message("dialog.json.diff.right")
        )
        
        DiffManager.getInstance().showDiff(project, request)
    }
    
    fun validateJson(json: String): Pair<Boolean, String?> {
        return try {
            if (formatterService.isValidJson(json)) {
                Pair(true, null)
            } else {
                Pair(false, LocalizationBundle.message("dialog.json.diff.invalid.json.format"))
            }
        } catch (e: Exception) {
            Pair(false, e.message)
        }
    }
    
    private fun createDiffContent(json: String): DiffContent {
        return DiffContentFactory.getInstance().create(project, json, null, false)
    }
    
    fun createDiffRequest(leftJson: String, rightJson: String, title: String? = null, semantic: Boolean = false): SimpleDiffRequest {
        val diffTitle = title ?: LocalizationBundle.message("dialog.json.diff.title")
        
        // Format JSON before showing diff
        val leftFormatted = if (semantic) {
            formatterService.formatJson(leftJson, JsonFormatState.PRETTIFY_SORTED)
        } else {
            formatterService.formatJson(leftJson, JsonFormatState.PRETTIFY)
        }
        
        val rightFormatted = if (semantic) {
            formatterService.formatJson(rightJson, JsonFormatState.PRETTIFY_SORTED)
        } else {
            formatterService.formatJson(rightJson, JsonFormatState.PRETTIFY)
        }
        
        val leftContent = createDiffContent(leftFormatted)
        val rightContent = createDiffContent(rightFormatted)
        
        return SimpleDiffRequest(
            diffTitle, 
            leftContent, 
            rightContent,
            LocalizationBundle.message("dialog.json.diff.left"),
            LocalizationBundle.message("dialog.json.diff.right")
        )
    }
}