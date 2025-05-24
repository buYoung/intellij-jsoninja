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
import javax.swing.*

class JsoninjaSettingsConfigurable(private val project: Project) : Configurable {

    private val settings: JsoninjaSettingsState = JsoninjaSettingsState.getInstance(project)

    private var indentSizeSpinner: JSpinner? = null
    private var sortKeysCheckBox: JBCheckBox? = null
    private var jsonFormatStateComboBox: ComboBox<JsonFormatStateWrapper>? = null
    private var pasteFormatStateComboBox: ComboBox<JsonFormatStateWrapper>? = null
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

    override fun getDisplayName(): String {
        return LocalizationBundle.message("settings.display.name")
    }

    override fun createComponent(): JComponent? {
        if (mainPanel == null) {
            indentSizeSpinner = JSpinner(SpinnerNumberModel(settings.indentSize, 0, 32, 1))
            sortKeysCheckBox = JBCheckBox(LocalizationBundle.message("settings.sortkeys.label"), settings.sortKeys)

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
            pasteFormatStateComboBox = ComboBox(allFormatStates)

            // Find the matching wrapper for the current state
            val currentFormatState = JsonFormatState.fromString(settings.jsonFormatState)
            val selectedWrapper = defaultFormatStates.find { it.state == currentFormatState }
            jsonFormatStateComboBox?.selectedItem = selectedWrapper

            // Find the matching wrapper for the paste format state
            val currentPasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)
            val selectedPasteWrapper = allFormatStates.find { it.state == currentPasteFormatState }
            pasteFormatStateComboBox?.selectedItem = selectedPasteWrapper

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
                row(LocalizationBundle.message("settings.paste.format.label")) {
                    cell(pasteFormatStateComboBox!!)
                }
            }
        }

        return mainPanel
    }

    override fun isModified(): Boolean {
        val currentFormatState = JsonFormatState.fromString(settings.jsonFormatState)
        val currentPasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)
        return indentSizeSpinner?.value != settings.indentSize ||
                sortKeysCheckBox?.isSelected != settings.sortKeys ||
                (jsonFormatStateComboBox?.selectedItem as? JsonFormatStateWrapper)?.state?.name != currentFormatState.name ||
                (pasteFormatStateComboBox?.selectedItem as? JsonFormatStateWrapper)?.state?.name != currentPasteFormatState.name
    }

    override fun apply() {
        settings.indentSize = indentSizeSpinner?.value as? Int ?: settings.indentSize
        settings.sortKeys = sortKeysCheckBox?.isSelected ?: settings.sortKeys
        val selectedFormatWrapper = jsonFormatStateComboBox?.selectedItem as? JsonFormatStateWrapper
        settings.jsonFormatState = selectedFormatWrapper?.state?.name ?: settings.jsonFormatState
        val selectedPasteFormatWrapper = pasteFormatStateComboBox?.selectedItem as? JsonFormatStateWrapper
        settings.pasteFormatState = selectedPasteFormatWrapper?.state?.name ?: settings.pasteFormatState
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

        // Find the matching wrapper for the paste format state
        val currentPasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)
        val allFormatStates = JsonFormatState.entries
            .map { JsonFormatStateWrapper(it) }
            .toTypedArray()
        val selectedPasteWrapper = allFormatStates.find { it.state == currentPasteFormatState }
        pasteFormatStateComboBox?.selectedItem = selectedPasteWrapper
    }

    override fun disposeUIResources() {
        mainPanel = null
        indentSizeSpinner = null
        sortKeysCheckBox = null
        jsonFormatStateComboBox = null
        pasteFormatStateComboBox = null
    }
}
