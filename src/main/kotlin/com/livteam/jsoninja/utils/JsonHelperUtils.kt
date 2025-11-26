package com.livteam.jsoninja.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.ui.component.JsonHelperPanel

/**
 * JSONinja plugin에서 자주 쓰는 유틸 함수
 */
object JsonHelperUtils {
    /**
     * JSONinja tool window의 활성 탭에서 현재 JSON 내용을 가져온다.
     * @param project 현재 project
     * @return 활성 탭 JSON 내용, 없으면 null
     */
    fun getCurrentJsonFromToolWindow(project: Project): String? {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("JSONinja") ?: return null

        if (!toolWindow.isVisible) return null

        val content = toolWindow.contentManager.selectedContent ?: return null
        val component = content.component as? JsonHelperPanel ?: return null

        return component.getCurrentEditor()?.getText()
    }

    /**
     * Heuristically checks if the given text appears to be in JSON Lines (JSONL) format.
     *
     * This function uses a set of heuristics to quickly determine if a text is likely JSONL
     * without parsing the entire file. This is crucial for performance with large files.
     *
     * The heuristics are:
     * 1.  **Multiple Non-Empty Lines**: A valid JSONL file must contain at least two non-empty
     *     lines. A single-line document is more likely to be a standard JSON file.
     * 2.  **Strict JSON Parsing of Sample Lines**: It samples up to the first 10 non-empty
     *     lines and tries to parse each as a strict JSON object. If all sampled lines parse
     *     correctly, it's considered JSONL. This check uses a strict `ObjectMapper` that
     *     disallows JSON5 features like comments or trailing commas.
     * 3.  **Memory Efficiency**: It processes the text line by line using `lineSequence` to avoid
     *     loading the entire file into memory.
     *
     * @param text The text content to check.
     * @param jsonService A service that provides the strict `ObjectMapper` for JSONL parsing.
     * @return `true` if the text is likely JSONL, `false` otherwise.
     */
    fun isJsonL(text: String, jsonService: JsonObjectMapperService): Boolean {
        if (text.isBlank()) return false

        // Use a sequence for memory-efficient line processing, especially for large files.
        val linesSequence = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Take up to 11 lines to check two conditions:
        // 1. Are there at least 2 non-empty lines?
        // 2. Are the first 10 lines valid JSON?
        val sampleLines = linesSequence.take(11).toList()

        // If there are fewer than two non-empty lines, it's not considered JSONL.
        if (sampleLines.size < 2) return false

        // Use a strict ObjectMapper to ensure each line is a valid, standard JSON object.
        val mapper = jsonService.jsonLObjectMapper

        // Check if the first 10 non-empty lines are all valid JSON objects.
        return sampleLines.take(10).all { line ->
            try {
                // Attempt to parse the line. If it fails, it's not a valid JSONL line.
                mapper.readTree(line)
                true
            } catch (e: Exception) {
                // Any parsing failure means it's not a well-formed JSONL file.
                false
            }
        }
    }
}
