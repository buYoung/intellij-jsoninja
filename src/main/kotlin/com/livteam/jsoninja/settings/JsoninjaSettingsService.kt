package com.livteam.jsoninja.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "JsoninjaSettings", storages = [Storage("jsoninja_settings.xml")])
class JsoninjaSettingsService : PersistentStateComponent<JsoninjaSettingsState> {
    private var state = JsoninjaSettingsState()

    override fun getState(): JsoninjaSettingsState = state

    override fun loadState(state: JsoninjaSettingsState) {
        this.state = state
    }

    var indentSize: Int
        get() = state.indentSize
        set(value) { state.indentSize = value }

    var sortKeys: Boolean
        get() = state.sortKeys
        set(value) { state.sortKeys = value }

    var compactArrays: Boolean
        get() = state.compactArrays
        set(value) { state.compactArrays = value }
}

data class JsoninjaSettingsState(
    var indentSize: Int = 2,
    var sortKeys: Boolean = false,
    var compactArrays: Boolean = false
)
