package com.livteam.jsoninja.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.bind
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.model.JsonIconPack
import javax.swing.*

class JsoninjaSettingsConfigurable(private val project: Project) : Configurable {

    private val settings: JsoninjaSettingsState = JsoninjaSettingsState.getInstance(project)

    private var indentSizeSpinner: JSpinner? = null
    private var sortKeysCheckBox: JBCheckBox? = null
    private var jsonFormatStateComboBox: ComboBox<JsonFormatStateWrapper>? = null
    private var iconPackComboBox: ComboBox<JsonIconPackWrapper>? = null
    private var pasteFormatStateComboBox: ComboBox<JsonFormatStateWrapper>? = null
    private var diffDisplayModeComboBox: ComboBox<JsonDiffDisplayModeWrapper>? = null
    private var diffSortKeysCheckBox: JBCheckBox? = null
    private var jsonQueryTypeComboBox: ComboBox<JsonQueryTypeWrapper>? = null
    private var largeFileThresholdSpinner: JSpinner? = null
    private var showLargeFileWarningCheckBox: JBCheckBox? = null
    private var mainPanel: JPanel? = null

    // Wrapper class for JComboBox to display enum values nicely
    data class JsonFormatStateWrapper(val state: JsonFormatState) {
        override fun toString(): String {
            // This could be localized in the future if needed
            return state.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " ")
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is JsonFormatStateWrapper) return false
            return state == other.state
        }

        override fun hashCode(): Int {
            return state.hashCode()
        }
    }
    
    // Wrapper class for JsonDiffDisplayMode
    data class JsonDiffDisplayModeWrapper(val mode: JsonDiffDisplayMode) {
        override fun toString(): String {
            return when (mode) {
                JsonDiffDisplayMode.EDITOR_TAB -> LocalizationBundle.message("settings.diff.display.editor.tab")
                JsonDiffDisplayMode.WINDOW -> LocalizationBundle.message("settings.diff.display.window")
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is JsonDiffDisplayModeWrapper) return false
            return mode == other.mode
        }

        override fun hashCode(): Int {
            return mode.hashCode()
        }
    }

    // Wrapper class for JsonQueryType
    data class JsonQueryTypeWrapper(val type: JsonQueryType) {
        override fun toString(): String {
            return when (type) {
                JsonQueryType.JAYWAY_JSONPATH -> "Jayway JsonPath"
                JsonQueryType.JMESPATH -> "JMESPath"
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is JsonQueryTypeWrapper) return false
            return type == other.type
        }

        override fun hashCode(): Int {
            return type.hashCode()
        }
    }

    // Wrapper class for JsonIconPack
    data class JsonIconPackWrapper(val pack: JsonIconPack) {
        override fun toString(): String {
            return when (pack) {
                JsonIconPack.VERSION_1 -> LocalizationBundle.message("settings.icon.pack.v1")
                JsonIconPack.VERSION_2 -> LocalizationBundle.message("settings.icon.pack.v2")
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is JsonIconPackWrapper) return false
            return pack == other.pack
        }

        override fun hashCode(): Int {
            return pack.hashCode()
        }
    }

    override fun getDisplayName(): String {
        return LocalizationBundle.message("settings.display.name")
    }

    override fun createComponent(): JComponent? {
        if (mainPanel == null) {
            indentSizeSpinner = JSpinner(SpinnerNumberModel(settings.indentSize, 0, 32, 1))
            sortKeysCheckBox = JBCheckBox(LocalizationBundle.message("settings.sortkeys.label"), settings.sortKeys)
            diffSortKeysCheckBox = JBCheckBox(LocalizationBundle.message("settings.diff.sort.label"), settings.diffSortKeys)
            largeFileThresholdSpinner = JSpinner(SpinnerNumberModel(settings.largeFileThresholdMB, 1, 100, 1))
            showLargeFileWarningCheckBox = JBCheckBox(LocalizationBundle.message("settings.show.large.file.warning.label"), settings.showLargeFileWarning)

            // Filter out UGLIFY for default format dropdown
            val defaultFormatStates = JsonFormatState.entries
                .filter { it != JsonFormatState.UGLIFY }
                .map { JsonFormatStateWrapper(it) }
                .toTypedArray()

            // Include all states for paste format dropdown
            val allFormatStates = JsonFormatState.entries
                .map { JsonFormatStateWrapper(it) }
                .toTypedArray()

            jsonFormatStateComboBox = ComboBox(defaultFormatStates)
            
            // Create icon pack combobox
            val iconPacks = JsonIconPack.entries
                .map { JsonIconPackWrapper(it) }
                .toTypedArray()
            iconPackComboBox = ComboBox(iconPacks)
            val currentIconPack = try {
                JsonIconPack.valueOf(settings.iconPack)
            } catch (e: IllegalArgumentException) {
                JsonIconPack.VERSION_2
            }
            val selectedIconPackWrapper = iconPacks.find { it.pack == currentIconPack }
            iconPackComboBox?.selectedItem = selectedIconPackWrapper

            pasteFormatStateComboBox = ComboBox(allFormatStates)

            // Find the matching wrapper for the current state
            val currentFormatState = JsonFormatState.fromString(settings.jsonFormatState)
            val selectedWrapper = defaultFormatStates.find { it.state == currentFormatState }
            jsonFormatStateComboBox?.selectedItem = selectedWrapper

            // Find the matching wrapper for the paste format state
            val currentPasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)
            val selectedPasteWrapper = allFormatStates.find { it.state == currentPasteFormatState }
            pasteFormatStateComboBox?.selectedItem = selectedPasteWrapper
            
            // Create diff display mode combobox
            val diffDisplayModes = JsonDiffDisplayMode.entries
                .map { JsonDiffDisplayModeWrapper(it) }
                .toTypedArray()
            
            diffDisplayModeComboBox = ComboBox(diffDisplayModes)
            
            // Find the matching wrapper for the current diff display mode
            val currentDiffDisplayMode = try {
                JsonDiffDisplayMode.valueOf(settings.diffDisplayMode)
            } catch (e: IllegalArgumentException) {
                JsonDiffDisplayMode.EDITOR_TAB
            }
            val selectedDiffModeWrapper = diffDisplayModes.find { it.mode == currentDiffDisplayMode }
            diffDisplayModeComboBox?.selectedItem = selectedDiffModeWrapper

            // Create json query type combobox
            val jsonQueryTypes = JsonQueryType.entries
                .map { JsonQueryTypeWrapper(it) }
                .toTypedArray()
            jsonQueryTypeComboBox = ComboBox(jsonQueryTypes)
            val currentJsonQueryType = JsonQueryType.fromString(settings.jsonQueryType)
            val selectedJsonQueryTypeWrapper = jsonQueryTypes.find { it.type == currentJsonQueryType }
            jsonQueryTypeComboBox?.selectedItem = selectedJsonQueryTypeWrapper

            mainPanel = panel {
                row(LocalizationBundle.message("settings.indent.label")) {
                    cell(indentSizeSpinner!!)
                }
                row {
                    cell(sortKeysCheckBox!!)
                }
                row(LocalizationBundle.message("settings.format.label")) {
                    cell(jsonFormatStateComboBox!!)
                }
                row(LocalizationBundle.message("settings.icon.pack.label")) {
                    cell(iconPackComboBox!!)
                }
                row(LocalizationBundle.message("settings.paste.format.label")) {
                    cell(pasteFormatStateComboBox!!)
                }
                row(LocalizationBundle.message("settings.diff.display.label")) {
                    cell(diffDisplayModeComboBox!!)
                }
                row {
                    cell(diffSortKeysCheckBox!!)
                }
                row(LocalizationBundle.message("settings.query.type.label")) {
                    cell(jsonQueryTypeComboBox!!)
                }
                separator()
                row(LocalizationBundle.message("settings.large.file.threshold.label")) {
                    cell(largeFileThresholdSpinner!!)
                }
                row {
                    cell(showLargeFileWarningCheckBox!!)
                }
            }
        }

        return mainPanel
    }

    override fun isModified(): Boolean {
        val currentFormatState = JsonFormatState.fromString(settings.jsonFormatState)
        val currentIconPack = try {
            JsonIconPack.valueOf(settings.iconPack)
        } catch (e: IllegalArgumentException) {
            JsonIconPack.VERSION_2
        }
        val currentPasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)
        val currentDiffDisplayMode = try {
            JsonDiffDisplayMode.valueOf(settings.diffDisplayMode)
        } catch (e: IllegalArgumentException) {
            JsonDiffDisplayMode.EDITOR_TAB
        }
        val currentJsonQueryType = JsonQueryType.fromString(settings.jsonQueryType)
        return indentSizeSpinner?.value != settings.indentSize ||
                sortKeysCheckBox?.isSelected != settings.sortKeys ||
                (jsonFormatStateComboBox?.selectedItem as? JsonFormatStateWrapper)?.state?.name != currentFormatState.name ||
                (iconPackComboBox?.selectedItem as? JsonIconPackWrapper)?.pack?.name != currentIconPack.name ||
                (pasteFormatStateComboBox?.selectedItem as? JsonFormatStateWrapper)?.state?.name != currentPasteFormatState.name ||
                (diffDisplayModeComboBox?.selectedItem as? JsonDiffDisplayModeWrapper)?.mode?.name != currentDiffDisplayMode.name ||
                diffSortKeysCheckBox?.isSelected != settings.diffSortKeys ||
                (jsonQueryTypeComboBox?.selectedItem as? JsonQueryTypeWrapper)?.type?.name != currentJsonQueryType.name ||
                largeFileThresholdSpinner?.value != settings.largeFileThresholdMB ||
                showLargeFileWarningCheckBox?.isSelected != settings.showLargeFileWarning
    }

    override fun apply() {
        settings.indentSize = indentSizeSpinner?.value as? Int ?: settings.indentSize
        settings.sortKeys = sortKeysCheckBox?.isSelected ?: settings.sortKeys
        val selectedFormatWrapper = jsonFormatStateComboBox?.selectedItem as? JsonFormatStateWrapper
        settings.jsonFormatState = selectedFormatWrapper?.state?.name ?: settings.jsonFormatState
        val selectedIconPackWrapper = iconPackComboBox?.selectedItem as? JsonIconPackWrapper
        settings.iconPack = selectedIconPackWrapper?.pack?.name ?: settings.iconPack
        val selectedPasteFormatWrapper = pasteFormatStateComboBox?.selectedItem as? JsonFormatStateWrapper
        settings.pasteFormatState = selectedPasteFormatWrapper?.state?.name ?: settings.pasteFormatState
        val selectedDiffModeWrapper = diffDisplayModeComboBox?.selectedItem as? JsonDiffDisplayModeWrapper
        settings.diffDisplayMode = selectedDiffModeWrapper?.mode?.name ?: settings.diffDisplayMode
        settings.diffSortKeys = diffSortKeysCheckBox?.isSelected ?: settings.diffSortKeys
        val selectedJsonQueryTypeWrapper = jsonQueryTypeComboBox?.selectedItem as? JsonQueryTypeWrapper
        settings.jsonQueryType = selectedJsonQueryTypeWrapper?.type?.name ?: settings.jsonQueryType
        settings.largeFileThresholdMB = largeFileThresholdSpinner?.value as? Int ?: settings.largeFileThresholdMB
        settings.showLargeFileWarning = showLargeFileWarningCheckBox?.isSelected ?: settings.showLargeFileWarning
    }

    override fun reset() {
        indentSizeSpinner?.value = settings.indentSize
        sortKeysCheckBox?.isSelected = settings.sortKeys

        // Find the matching wrapper for the current state
        val currentFormatState = JsonFormatState.fromString(settings.jsonFormatState)
        val defaultFormatStates = JsonFormatState.entries
            .filter { it != JsonFormatState.UGLIFY }
            .map { JsonFormatStateWrapper(it) }
            .toTypedArray()
        val selectedWrapper = defaultFormatStates.find { it.state == currentFormatState }
        jsonFormatStateComboBox?.selectedItem = selectedWrapper

        // Find the matching wrapper for the current icon pack
        val currentIconPack = try {
            JsonIconPack.valueOf(settings.iconPack)
        } catch (e: IllegalArgumentException) {
            JsonIconPack.VERSION_2
        }
        val iconPacks = JsonIconPack.entries
            .map { JsonIconPackWrapper(it) }
            .toTypedArray()
        val selectedIconPackWrapper = iconPacks.find { it.pack == currentIconPack }
        iconPackComboBox?.selectedItem = selectedIconPackWrapper

        // Find the matching wrapper for the paste format state
        val currentPasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)
        val allFormatStates = JsonFormatState.entries
            .map { JsonFormatStateWrapper(it) }
            .toTypedArray()
        val selectedPasteWrapper = allFormatStates.find { it.state == currentPasteFormatState }
        pasteFormatStateComboBox?.selectedItem = selectedPasteWrapper
        
        // Find the matching wrapper for the current diff display mode
        val currentDiffDisplayMode = try {
            JsonDiffDisplayMode.valueOf(settings.diffDisplayMode)
        } catch (e: IllegalArgumentException) {
            JsonDiffDisplayMode.EDITOR_TAB
        }
        val diffDisplayModes = JsonDiffDisplayMode.entries
            .map { JsonDiffDisplayModeWrapper(it) }
            .toTypedArray()
        val selectedDiffModeWrapper = diffDisplayModes.find { it.mode == currentDiffDisplayMode }
        diffDisplayModeComboBox?.selectedItem = selectedDiffModeWrapper
        diffSortKeysCheckBox?.isSelected = settings.diffSortKeys
        
        // Find the matching wrapper for the current json query type
        val currentJsonQueryType = JsonQueryType.fromString(settings.jsonQueryType)
        val jsonQueryTypes = JsonQueryType.entries
            .map { JsonQueryTypeWrapper(it) }
            .toTypedArray()
        val selectedJsonQueryTypeWrapper = jsonQueryTypes.find { it.type == currentJsonQueryType }
        jsonQueryTypeComboBox?.selectedItem = selectedJsonQueryTypeWrapper

        largeFileThresholdSpinner?.value = settings.largeFileThresholdMB
        showLargeFileWarningCheckBox?.isSelected = settings.showLargeFileWarning
    }

    override fun disposeUIResources() {
        mainPanel = null
        indentSizeSpinner = null
        sortKeysCheckBox = null
        jsonFormatStateComboBox = null
        iconPackComboBox = null
        pasteFormatStateComboBox = null
        diffDisplayModeComboBox = null
        diffSortKeysCheckBox = null
        jsonQueryTypeComboBox = null
        largeFileThresholdSpinner = null
        showLargeFileWarningCheckBox = null
    }
}
