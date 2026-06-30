package com.livteam.jsoninja.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.JsonIconPack
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode

@Service(Service.Level.APP)
class JsoninjaSettingsState {

    var indentSize: Int
        get() = activeSettings().indentSize
        set(value) {
            activeSettings().indentSize = value
        }

    var sortKeys: Boolean
        get() = activeSettings().sortKeys
        set(value) {
            activeSettings().sortKeys = value
        }

    var jsonFormatState: String
        get() = activeSettings().jsonFormatState
        set(value) {
            activeSettings().jsonFormatState = value
        }

    var iconPack: String
        get() = activeSettings().iconPack
        set(value) {
            activeSettings().iconPack = value
        }

    var pasteFormatState: String
        get() = activeSettings().pasteFormatState
        set(value) {
            activeSettings().pasteFormatState = value
        }

    var diffDisplayMode: String
        get() = activeSettings().diffDisplayMode
        set(value) {
            activeSettings().diffDisplayMode = value
        }

    var diffSortKeys: Boolean
        get() = activeSettings().diffSortKeys
        set(value) {
            activeSettings().diffSortKeys = value
        }

    var jsonQueryType: String
        get() = activeSettings().jsonQueryType
        set(value) {
            activeSettings().jsonQueryType = value
        }

    var jsonToTypeLastLanguage: String
        get() = activeSettings().jsonToTypeLastLanguage
        set(value) {
            activeSettings().jsonToTypeLastLanguage = value
        }

    var jsonToTypeDefaultNaming: String
        get() = activeSettings().jsonToTypeDefaultNaming
        set(value) {
            activeSettings().jsonToTypeDefaultNaming = value
        }

    var jsonToTypeNullableByDefault: Boolean
        get() = activeSettings().jsonToTypeNullableByDefault
        set(value) {
            activeSettings().jsonToTypeNullableByDefault = value
        }

    var jsonToTypeAnnotationStyle: String
        get() = activeSettings().jsonToTypeAnnotationStyle
        set(value) {
            activeSettings().jsonToTypeAnnotationStyle = value
        }

    var jsonToTypeUsesExperimentalGoUnionTypes: Boolean
        get() = activeSettings().jsonToTypeUsesExperimentalGoUnionTypes
        set(value) {
            activeSettings().jsonToTypeUsesExperimentalGoUnionTypes = value
        }

    var typeToJsonLastLanguage: String
        get() = activeSettings().typeToJsonLastLanguage
        set(value) {
            activeSettings().typeToJsonLastLanguage = value
        }

    var typeToJsonFieldsMode: String
        get() = activeSettings().typeToJsonFieldsMode
        set(value) {
            activeSettings().typeToJsonFieldsMode = value
        }

    var typeToJsonIncludesNullableFieldWithNullValue: Boolean
        get() = activeSettings().typeToJsonIncludesNullableFieldWithNullValue
        set(value) {
            activeSettings().typeToJsonIncludesNullableFieldWithNullValue = value
        }

    var typeToJsonUsesRealisticSampleData: Boolean
        get() = activeSettings().typeToJsonUsesRealisticSampleData
        set(value) {
            activeSettings().typeToJsonUsesRealisticSampleData = value
        }

    var typeToJsonOutputCount: Int
        get() = activeSettings().typeToJsonOutputCount
        set(value) {
            activeSettings().typeToJsonOutputCount = value
        }

    var typeToJsonFormatState: String
        get() = activeSettings().typeToJsonFormatState
        set(value) {
            activeSettings().typeToJsonFormatState = value
        }

    var largeFileThresholdMB: Int
        get() = activeSettings().largeFileThresholdMB
        set(value) {
            activeSettings().largeFileThresholdMB = value
        }

    var showLargeFileWarning: Boolean
        get() = activeSettings().showLargeFileWarning
        set(value) {
            activeSettings().showLargeFileWarning = value
        }

    private fun activeSettings(): JsoninjaSettingsData {
        val application = ApplicationManager.getApplication()
        return if (isSettingsSyncEnabled()) {
            application.service<JsoninjaSyncedSettingsState>().settings
        } else {
            application.service<JsoninjaLocalSettingsState>().settings
        }
    }

    private fun migrateFromLegacyProjectSettings(project: Project) {
        val settings = activeSettings()
        if (settings.hasMigratedProjectSettings) return
        if (settings.hasCustomSettings()) {
            settings.hasMigratedProjectSettings = true
            return
        }

        val legacySettings = project.service<JsoninjaLegacyProjectSettingsState>()
        if (!legacySettings.hasCustomSettings()) {
            settings.hasMigratedProjectSettings = true
            return
        }

        settings.copySettingsFrom(legacySettings)
        settings.hasMigratedProjectSettings = true
    }

    companion object {
        fun isSettingsSyncEnabled(): Boolean {
            return JsoninjaSettingsSyncPreferenceState.getInstance().isSettingsSyncEnabled
        }

        fun setSettingsSyncEnabled(project: Project, isEnabled: Boolean): JsoninjaSettingsState {
            val instance = getApplicationInstance()
            val syncPreference = JsoninjaSettingsSyncPreferenceState.getInstance()
            val currentSettings = instance.activeSettings().copy()

            if (syncPreference.isSettingsSyncEnabled != isEnabled) {
                syncPreference.isSettingsSyncEnabled = isEnabled
                instance.activeSettings().copySettingsFrom(currentSettings)
                instance.activeSettings().hasMigratedProjectSettings = true
            }

            instance.migrateFromLegacyProjectSettings(project)
            return instance
        }

        fun getInstance(project: Project): JsoninjaSettingsState {
            return getApplicationInstance().also { it.migrateFromLegacyProjectSettings(project) }
        }

        private fun getApplicationInstance(): JsoninjaSettingsState {
            return ApplicationManager.getApplication().service<JsoninjaSettingsState>()
        }
    }
}

data class JsoninjaSettingsData(
    var indentSize: Int = 2,
    var sortKeys: Boolean = false,
    var jsonFormatState: String = JsonFormatState.PRETTIFY.name,
    var iconPack: String = JsonIconPack.VERSION_2.name,
    var pasteFormatState: String = JsonFormatState.PRETTIFY.name,
    var diffDisplayMode: String = JsonDiffDisplayMode.WINDOW.name,
    var diffSortKeys: Boolean = false,
    var jsonQueryType: String = JsonQueryType.JAYWAY_JSONPATH.name,
    var jsonToTypeLastLanguage: String = SupportedLanguage.KOTLIN.name,
    var jsonToTypeDefaultNaming: String = "AUTO",
    var jsonToTypeNullableByDefault: Boolean = true,
    var jsonToTypeAnnotationStyle: String = "NONE",
    var jsonToTypeUsesExperimentalGoUnionTypes: Boolean = false,
    var typeToJsonLastLanguage: String = SupportedLanguage.KOTLIN.name,
    var typeToJsonFieldsMode: String = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL.name,
    var typeToJsonIncludesNullableFieldWithNullValue: Boolean = true,
    var typeToJsonUsesRealisticSampleData: Boolean = true,
    var typeToJsonOutputCount: Int = 1,
    var typeToJsonFormatState: String = JsonFormatState.PRETTIFY.name,
    var largeFileThresholdMB: Int = 2,
    var showLargeFileWarning: Boolean = true,
    var hasMigratedProjectSettings: Boolean = false
) {

    fun copySettingsFrom(settings: JsoninjaSettingsData) {
        indentSize = settings.indentSize
        sortKeys = settings.sortKeys
        jsonFormatState = settings.jsonFormatState
        iconPack = settings.iconPack
        pasteFormatState = settings.pasteFormatState
        diffDisplayMode = settings.diffDisplayMode
        diffSortKeys = settings.diffSortKeys
        jsonQueryType = settings.jsonQueryType
        jsonToTypeLastLanguage = settings.jsonToTypeLastLanguage
        jsonToTypeDefaultNaming = settings.jsonToTypeDefaultNaming
        jsonToTypeNullableByDefault = settings.jsonToTypeNullableByDefault
        jsonToTypeAnnotationStyle = settings.jsonToTypeAnnotationStyle
        jsonToTypeUsesExperimentalGoUnionTypes = settings.jsonToTypeUsesExperimentalGoUnionTypes
        typeToJsonLastLanguage = settings.typeToJsonLastLanguage
        typeToJsonFieldsMode = settings.typeToJsonFieldsMode
        typeToJsonIncludesNullableFieldWithNullValue = settings.typeToJsonIncludesNullableFieldWithNullValue
        typeToJsonUsesRealisticSampleData = settings.typeToJsonUsesRealisticSampleData
        typeToJsonOutputCount = settings.typeToJsonOutputCount
        typeToJsonFormatState = settings.typeToJsonFormatState
        largeFileThresholdMB = settings.largeFileThresholdMB
        showLargeFileWarning = settings.showLargeFileWarning
        hasMigratedProjectSettings = settings.hasMigratedProjectSettings
    }

    fun copySettingsFrom(settings: JsoninjaLegacyProjectSettingsState) {
        indentSize = settings.indentSize
        sortKeys = settings.sortKeys
        jsonFormatState = settings.jsonFormatState
        iconPack = settings.iconPack
        pasteFormatState = settings.pasteFormatState
        diffDisplayMode = settings.diffDisplayMode
        diffSortKeys = settings.diffSortKeys
        jsonQueryType = settings.jsonQueryType
        jsonToTypeLastLanguage = settings.jsonToTypeLastLanguage
        jsonToTypeDefaultNaming = settings.jsonToTypeDefaultNaming
        jsonToTypeNullableByDefault = settings.jsonToTypeNullableByDefault
        jsonToTypeAnnotationStyle = settings.jsonToTypeAnnotationStyle
        jsonToTypeUsesExperimentalGoUnionTypes = settings.jsonToTypeUsesExperimentalGoUnionTypes
        typeToJsonLastLanguage = settings.typeToJsonLastLanguage
        typeToJsonFieldsMode = settings.typeToJsonFieldsMode
        typeToJsonIncludesNullableFieldWithNullValue = settings.typeToJsonIncludesNullableFieldWithNullValue
        typeToJsonUsesRealisticSampleData = settings.typeToJsonUsesRealisticSampleData
        typeToJsonOutputCount = settings.typeToJsonOutputCount
        typeToJsonFormatState = settings.typeToJsonFormatState
        largeFileThresholdMB = settings.largeFileThresholdMB
        showLargeFileWarning = settings.showLargeFileWarning
    }

    fun hasCustomSettings(): Boolean {
        val defaultSettings = JsoninjaSettingsData()
        return indentSize != defaultSettings.indentSize ||
                sortKeys != defaultSettings.sortKeys ||
                jsonFormatState != defaultSettings.jsonFormatState ||
                iconPack != defaultSettings.iconPack ||
                pasteFormatState != defaultSettings.pasteFormatState ||
                diffDisplayMode != defaultSettings.diffDisplayMode ||
                diffSortKeys != defaultSettings.diffSortKeys ||
                jsonQueryType != defaultSettings.jsonQueryType ||
                jsonToTypeLastLanguage != defaultSettings.jsonToTypeLastLanguage ||
                jsonToTypeDefaultNaming != defaultSettings.jsonToTypeDefaultNaming ||
                jsonToTypeNullableByDefault != defaultSettings.jsonToTypeNullableByDefault ||
                jsonToTypeAnnotationStyle != defaultSettings.jsonToTypeAnnotationStyle ||
                jsonToTypeUsesExperimentalGoUnionTypes != defaultSettings.jsonToTypeUsesExperimentalGoUnionTypes ||
                typeToJsonLastLanguage != defaultSettings.typeToJsonLastLanguage ||
                typeToJsonFieldsMode != defaultSettings.typeToJsonFieldsMode ||
                typeToJsonIncludesNullableFieldWithNullValue != defaultSettings.typeToJsonIncludesNullableFieldWithNullValue ||
                typeToJsonUsesRealisticSampleData != defaultSettings.typeToJsonUsesRealisticSampleData ||
                typeToJsonOutputCount != defaultSettings.typeToJsonOutputCount ||
                typeToJsonFormatState != defaultSettings.typeToJsonFormatState ||
                largeFileThresholdMB != defaultSettings.largeFileThresholdMB ||
                showLargeFileWarning != defaultSettings.showLargeFileWarning
    }
}

@Service(Service.Level.APP)
@State(
    name = "JsoninjaApplicationSettingsState",
    storages = [Storage("jsoninja.xml")],
    category = SettingsCategory.PLUGINS
)
class JsoninjaSyncedSettingsState : PersistentStateComponent<JsoninjaSettingsData> {
    var settings = JsoninjaSettingsData()
        private set

    override fun getState(): JsoninjaSettingsData {
        return settings
    }

    override fun loadState(state: JsoninjaSettingsData) {
        settings = state
    }
}

@Service(Service.Level.APP)
@State(
    name = "JsoninjaLocalSettingsState",
    storages = [Storage("jsoninja-local.xml", roamingType = RoamingType.DISABLED)]
)
class JsoninjaLocalSettingsState : PersistentStateComponent<JsoninjaSettingsData> {
    var settings = JsoninjaSettingsData()
        private set

    override fun getState(): JsoninjaSettingsData {
        return settings
    }

    override fun loadState(state: JsoninjaSettingsData) {
        settings = state
    }
}

@Service(Service.Level.APP)
@State(
    name = "JsoninjaSettingsSyncPreferenceState",
    storages = [Storage("jsoninja-sync-preferences.xml", roamingType = RoamingType.DISABLED)]
)
class JsoninjaSettingsSyncPreferenceState : PersistentStateComponent<JsoninjaSettingsSyncPreferenceState> {
    var isSettingsSyncEnabled: Boolean = true

    override fun getState(): JsoninjaSettingsSyncPreferenceState {
        return this
    }

    override fun loadState(state: JsoninjaSettingsSyncPreferenceState) {
        isSettingsSyncEnabled = state.isSettingsSyncEnabled
    }

    companion object {
        fun getInstance(): JsoninjaSettingsSyncPreferenceState {
            return ApplicationManager.getApplication().service<JsoninjaSettingsSyncPreferenceState>()
        }
    }
}

@Service(Service.Level.PROJECT)
@State(name = "JsoninjaSettingsState", storages = [Storage("jsoninja.xml")])
data class JsoninjaLegacyProjectSettingsState(
    var indentSize: Int = 2,
    var sortKeys: Boolean = false,
    var jsonFormatState: String = JsonFormatState.PRETTIFY.name,
    var iconPack: String = JsonIconPack.VERSION_2.name,
    var pasteFormatState: String = JsonFormatState.PRETTIFY.name,
    var diffDisplayMode: String = JsonDiffDisplayMode.WINDOW.name,
    var diffSortKeys: Boolean = false,
    var jsonQueryType: String = JsonQueryType.JAYWAY_JSONPATH.name,
    var jsonToTypeLastLanguage: String = SupportedLanguage.KOTLIN.name,
    var jsonToTypeDefaultNaming: String = "AUTO",
    var jsonToTypeNullableByDefault: Boolean = true,
    var jsonToTypeAnnotationStyle: String = "NONE",
    var jsonToTypeUsesExperimentalGoUnionTypes: Boolean = false,
    var typeToJsonLastLanguage: String = SupportedLanguage.KOTLIN.name,
    var typeToJsonFieldsMode: String = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL.name,
    var typeToJsonIncludesNullableFieldWithNullValue: Boolean = true,
    var typeToJsonUsesRealisticSampleData: Boolean = true,
    var typeToJsonOutputCount: Int = 1,
    var typeToJsonFormatState: String = JsonFormatState.PRETTIFY.name,
    var largeFileThresholdMB: Int = 2,
    var showLargeFileWarning: Boolean = true
) : PersistentStateComponent<JsoninjaLegacyProjectSettingsState> {

    override fun getState(): JsoninjaLegacyProjectSettingsState {
        return this
    }

    override fun loadState(state: JsoninjaLegacyProjectSettingsState) {
        indentSize = state.indentSize
        sortKeys = state.sortKeys
        jsonFormatState = state.jsonFormatState
        iconPack = state.iconPack
        pasteFormatState = state.pasteFormatState
        diffDisplayMode = state.diffDisplayMode
        diffSortKeys = state.diffSortKeys
        jsonQueryType = state.jsonQueryType
        jsonToTypeLastLanguage = state.jsonToTypeLastLanguage
        jsonToTypeDefaultNaming = state.jsonToTypeDefaultNaming
        jsonToTypeNullableByDefault = state.jsonToTypeNullableByDefault
        jsonToTypeAnnotationStyle = state.jsonToTypeAnnotationStyle
        jsonToTypeUsesExperimentalGoUnionTypes = state.jsonToTypeUsesExperimentalGoUnionTypes
        typeToJsonLastLanguage = state.typeToJsonLastLanguage
        typeToJsonFieldsMode = state.typeToJsonFieldsMode
        typeToJsonIncludesNullableFieldWithNullValue = state.typeToJsonIncludesNullableFieldWithNullValue
        typeToJsonUsesRealisticSampleData = state.typeToJsonUsesRealisticSampleData
        typeToJsonOutputCount = state.typeToJsonOutputCount
        typeToJsonFormatState = state.typeToJsonFormatState
        largeFileThresholdMB = state.largeFileThresholdMB
        showLargeFileWarning = state.showLargeFileWarning
    }

    fun hasCustomSettings(): Boolean {
        val defaultSettings = JsoninjaLegacyProjectSettingsState()
        return indentSize != defaultSettings.indentSize ||
                sortKeys != defaultSettings.sortKeys ||
                jsonFormatState != defaultSettings.jsonFormatState ||
                iconPack != defaultSettings.iconPack ||
                pasteFormatState != defaultSettings.pasteFormatState ||
                diffDisplayMode != defaultSettings.diffDisplayMode ||
                diffSortKeys != defaultSettings.diffSortKeys ||
                jsonQueryType != defaultSettings.jsonQueryType ||
                jsonToTypeLastLanguage != defaultSettings.jsonToTypeLastLanguage ||
                jsonToTypeDefaultNaming != defaultSettings.jsonToTypeDefaultNaming ||
                jsonToTypeNullableByDefault != defaultSettings.jsonToTypeNullableByDefault ||
                jsonToTypeAnnotationStyle != defaultSettings.jsonToTypeAnnotationStyle ||
                jsonToTypeUsesExperimentalGoUnionTypes != defaultSettings.jsonToTypeUsesExperimentalGoUnionTypes ||
                typeToJsonLastLanguage != defaultSettings.typeToJsonLastLanguage ||
                typeToJsonFieldsMode != defaultSettings.typeToJsonFieldsMode ||
                typeToJsonIncludesNullableFieldWithNullValue != defaultSettings.typeToJsonIncludesNullableFieldWithNullValue ||
                typeToJsonUsesRealisticSampleData != defaultSettings.typeToJsonUsesRealisticSampleData ||
                typeToJsonOutputCount != defaultSettings.typeToJsonOutputCount ||
                typeToJsonFormatState != defaultSettings.typeToJsonFormatState ||
                largeFileThresholdMB != defaultSettings.largeFileThresholdMB ||
                showLargeFileWarning != defaultSettings.showLargeFileWarning
    }
}
