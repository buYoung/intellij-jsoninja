package com.livteam.jsoninja.ui.component.convertType

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import javax.swing.JComponent

class LanguageSelectorComponent(
    project: Project,
) : Disposable {
    private val settings = JsoninjaSettingsState.getInstance(project)
    private var isUpdatingSelection = false
    private var onLanguageChangedCallback: ((SupportedLanguage) -> Unit)? = null

    private lateinit var languageComboBox: ComboBox<SupportedLanguage>

    val component: JComponent by lazy { createComponent() }

    fun setOnLanguageChanged(callback: (SupportedLanguage) -> Unit) {
        onLanguageChangedCallback = callback
    }

    fun getSelectedLanguage(): SupportedLanguage {
        return languageComboBox.selectedItem as? SupportedLanguage
            ?: SupportedLanguage.fromNameOrDefault(settings.convertTypeLastLanguage)
    }

    fun setSelectedLanguage(language: SupportedLanguage) {
        if (!::languageComboBox.isInitialized) {
            component
        }

        if (languageComboBox.selectedItem == language) {
            return
        }

        isUpdatingSelection = true
        try {
            languageComboBox.selectedItem = language
        } finally {
            isUpdatingSelection = false
        }
        persistSelectedLanguage(language)
    }

    override fun dispose() {
        if (::languageComboBox.isInitialized) {
            languageComboBox.actionListeners.forEach(languageComboBox::removeActionListener)
        }
        onLanguageChangedCallback = null
    }

    private fun createComponent(): JComponent {
        languageComboBox = ComboBox(SupportedLanguage.entries.toTypedArray()).apply {
            prototypeDisplayValue = SupportedLanguage.TYPESCRIPT
            renderer = SimpleListCellRenderer.create { label, value, _ ->
                label.text = value?.displayName ?: ""
                label.icon = value?.icon
            }
            selectedItem = SupportedLanguage.fromNameOrDefault(settings.convertTypeLastLanguage)
            addActionListener {
                val selectedLanguage = selectedItem as? SupportedLanguage ?: return@addActionListener
                persistSelectedLanguage(selectedLanguage)
                if (!isUpdatingSelection) {
                    onLanguageChangedCallback?.invoke(selectedLanguage)
                }
            }
        }

        return languageComboBox
    }

    private fun persistSelectedLanguage(language: SupportedLanguage) {
        settings.convertTypeLastLanguage = language.name
    }
}
