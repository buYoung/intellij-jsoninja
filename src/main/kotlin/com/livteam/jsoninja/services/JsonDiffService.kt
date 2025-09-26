package com.livteam.jsoninja.services

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.json.JsonFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.diff.JsonDiffKeys
import com.livteam.jsoninja.model.JsonFormatState

@Service(Service.Level.PROJECT)
class JsonDiffService(private val project: Project) {

    private val formatterService = project.service<JsonFormatterService>()


    /**
     * Validates and formats JSON in a single operation to improve performance
     * @param json The JSON string to validate and format
     * @param semantic Whether to use semantic comparison (sorted keys)
     * @return Pair of (isValid, formattedJson) - formattedJson is null if invalid
     */
    fun validateAndFormat(json: String, semantic: Boolean): Pair<Boolean, String?> {
        return try {
            // First check if JSON is valid
            if (!formatterService.isValidJson(json)) {
                return Pair(false, null)
            }

            // Single parsing operation for both validation and formatting
            val formatState = if (semantic) JsonFormatState.PRETTIFY_SORTED else JsonFormatState.PRETTIFY
            val formatted = formatterService.formatJson(json, formatState)

            // If formatting succeeds, the JSON is valid
            Pair(true, formatted)
        } catch (e: Exception) {
            // If formatting fails, the JSON is invalid
            Pair(false, null)
        }
    }

    private fun createDiffContent(json: String, editable: Boolean = true) =
        if (editable) {
            DiffContentFactory.getInstance().createEditable(project, json, JsonFileType.INSTANCE)
        } else {
            DiffContentFactory.getInstance().create(project, json, JsonFileType.INSTANCE, false)
        }

    fun createDiffRequest(
        leftJson: String,
        rightJson: String,
        title: String? = null,
        semantic: Boolean = false
    ): SimpleDiffRequest {
        val diffTitle = title ?: LocalizationBundle.message("dialog.json.diff.title")

        // Use validateAndFormat for better performance
        val (leftValid, leftFormatted) = validateAndFormat(leftJson, semantic)
        val (rightValid, rightFormatted) = validateAndFormat(rightJson, semantic)

        // Use original JSON if formatting failed
        val leftFinal = if (leftValid && leftFormatted != null) leftFormatted else leftJson
        val rightFinal = if (rightValid && rightFormatted != null) rightFormatted else rightJson

        val leftContent = createDiffContent(leftFinal)
        val rightContent = createDiffContent(rightFinal)

        val request = SimpleDiffRequest(
            diffTitle,
            leftContent,
            rightContent,
            LocalizationBundle.message("dialog.json.diff.left"),
            LocalizationBundle.message("dialog.json.diff.right")
        )

        request.putUserData(JsonDiffKeys.JSON_DIFF_REQUEST_MARKER, true)

        return request
    }
}
