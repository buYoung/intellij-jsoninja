package com.livteam.jsoninja.ui.component.convertType

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBPanel
import com.livteam.jsoninja.model.SupportedLanguage
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JList

class LanguageSelectorComponent : JBPanel<LanguageSelectorComponent>(BorderLayout()) {
    private val languageComboBox = ComboBox(SupportedLanguage.entries.toTypedArray())
    private var onLanguageChanged: ((SupportedLanguage) -> Unit)? = null

    init {
        languageComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val language = value as? SupportedLanguage
                text = language?.getDisplayName().orEmpty()
                icon = language?.let(::loadLanguageIcon)
                return component
            }
        }
        languageComboBox.addActionListener {
            getSelectedLanguage()?.let { selectedLanguage ->
                onLanguageChanged?.invoke(selectedLanguage)
            }
        }
        add(languageComboBox, BorderLayout.CENTER)
    }

    fun setSelectedLanguage(language: SupportedLanguage) {
        languageComboBox.selectedItem = language
    }

    fun getSelectedLanguage(): SupportedLanguage? {
        return languageComboBox.selectedItem as? SupportedLanguage
    }

    fun setOnLanguageChanged(callback: (SupportedLanguage) -> Unit) {
        onLanguageChanged = callback
    }

    private fun loadLanguageIcon(language: SupportedLanguage): Icon {
        return IconLoader.getIcon("/icons/languages/${language.name.lowercase()}.svg", javaClass)
    }
}
