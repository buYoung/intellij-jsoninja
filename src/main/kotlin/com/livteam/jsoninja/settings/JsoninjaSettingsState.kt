package com.livteam.jsoninja.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.model.JsonIconPack

@Service(Service.Level.PROJECT)
@State(name = "JsoninjaSettingsState", storages = [Storage("jsoninja.xml")])
data class JsoninjaSettingsState(
    var indentSize: Int = 2,
    var sortKeys: Boolean = false,
    var jsonFormatState: String = JsonFormatState.PRETTIFY.name, // Store enum as string
    var iconPack: String = JsonIconPack.VERSION_2.name, // Icon pack preference
    var pasteFormatState: String = JsonFormatState.PRETTIFY.name, // Format state for paste operations
    var diffDisplayMode: String = JsonDiffDisplayMode.WINDOW.name, // Diff display mode preference
    var diffSortKeys: Boolean = false, // Diff auto sort keys
    var jsonQueryType: String = JsonQueryType.JAYWAY_JSONPATH.name, // Query type preference
    var largeFileThresholdMB: Int = 2, // Threshold in MB for large file warning
    var showLargeFileWarning: Boolean = true // Whether to show warning for large files
) : PersistentStateComponent<JsoninjaSettingsState> {

    override fun getState(): JsoninjaSettingsState {
        return this
    }

    override fun loadState(state: JsoninjaSettingsState) {
        this.indentSize = state.indentSize
        this.sortKeys = state.sortKeys
        this.jsonFormatState = state.jsonFormatState
        this.iconPack = state.iconPack
        this.pasteFormatState = state.pasteFormatState
        this.diffDisplayMode = state.diffDisplayMode
        this.diffSortKeys = state.diffSortKeys
        this.jsonQueryType = state.jsonQueryType
        this.largeFileThresholdMB = state.largeFileThresholdMB
        this.showLargeFileWarning = state.showLargeFileWarning
    }

    companion object {
        fun getInstance(project: Project): JsoninjaSettingsState {
            return project.service<JsoninjaSettingsState>()
        }
    }
}
