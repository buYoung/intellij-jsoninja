package com.livteam.jsoninja.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.JsonDiffDisplayMode

@Service(Service.Level.PROJECT)
@State(name = "JsoninjaSettingsState", storages = [Storage("jsoninja.xml")])
data class JsoninjaSettingsState(
    var indentSize: Int = 2,
    var sortKeys: Boolean = false,
    var jsonFormatState: String = JsonFormatState.PRETTIFY.name, // Store enum as string
    var pasteFormatState: String = JsonFormatState.PRETTIFY.name, // Format state for paste operations
    var diffDisplayMode: String = JsonDiffDisplayMode.WINDOW.name // Diff display mode preference
) : PersistentStateComponent<JsoninjaSettingsState> {

    override fun getState(): JsoninjaSettingsState {
        return this
    }

    override fun loadState(state: JsoninjaSettingsState) {
        this.indentSize = state.indentSize
        this.sortKeys = state.sortKeys
        this.jsonFormatState = state.jsonFormatState
        this.pasteFormatState = state.pasteFormatState
        this.diffDisplayMode = state.diffDisplayMode
    }

    companion object {
        fun getInstance(project: Project): JsoninjaSettingsState {
            return project.service<JsoninjaSettingsState>()
        }
    }
}
