package com.livteam.jsoninja.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.components.service
import com.intellij.ui.dsl.builder.panel
import com.intellij.openapi.ui.DialogPanel
import com.livteam.jsoninja.LocalizationBundle
import javax.swing.JComponent
import javax.swing.JCheckBox
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class JsoninjaSettingsConfigurable : SearchableConfigurable {
    private val settings = service<JsoninjaSettingsService>()

    private lateinit var panel: DialogPanel
    private lateinit var indentField: JSpinner
    private lateinit var sortKeysCheck: JCheckBox
    private lateinit var compactArraysCheck: JCheckBox

    override fun getId(): String = "com.livteam.jsoninja.settings"

    override fun getDisplayName(): String = LocalizationBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        panel = panel {
            row(LocalizationBundle.message("settings.indent.size")) {
                indentField = spinner(SpinnerNumberModel(settings.indentSize, 0, 8, 1)).component
            }
            row {
                sortKeysCheck = checkBox(LocalizationBundle.message("settings.sort.keys"))
                    .component
                sortKeysCheck.isSelected = settings.sortKeys
            }
            row {
                compactArraysCheck = checkBox(LocalizationBundle.message("settings.compact.arrays"))
                    .component
                compactArraysCheck.isSelected = settings.compactArrays
            }
        }
        return panel
    }

    override fun isModified(): Boolean {
        return indentField.value as Int != settings.indentSize ||
                sortKeysCheck.isSelected != settings.sortKeys ||
                compactArraysCheck.isSelected != settings.compactArrays
    }

    override fun apply() {
        settings.indentSize = indentField.value as Int
        settings.sortKeys = sortKeysCheck.isSelected
        settings.compactArrays = compactArraysCheck.isSelected
    }

    override fun reset() {
        indentField.value = settings.indentSize
        sortKeysCheck.isSelected = settings.sortKeys
        compactArraysCheck.isSelected = settings.compactArrays
    }
}
