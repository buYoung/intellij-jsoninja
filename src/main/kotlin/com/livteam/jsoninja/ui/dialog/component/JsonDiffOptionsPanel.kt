package com.livteam.jsoninja.ui.dialog.component

import com.intellij.ui.components.JBCheckBox
import com.livteam.jsoninja.LocalizationBundle
import java.awt.FlowLayout
import javax.swing.JPanel

/**
 * Panel for diff options configuration
 */
class JsonDiffOptionsPanel : JPanel(FlowLayout(FlowLayout.LEFT)) {

    private val semanticCheckbox = JBCheckBox(LocalizationBundle.message("dialog.json.diff.semantic"))
    private val listeners = mutableListOf<OptionsChangeListener>()

    init {
        setupComponents()
    }

    private fun setupComponents() {
        add(semanticCheckbox)
        
        semanticCheckbox.addActionListener {
            notifyListeners()
        }
    }

    fun isSemanticComparisonEnabled(): Boolean = semanticCheckbox.isSelected

    fun addOptionsChangeListener(listener: OptionsChangeListener) {
        listeners.add(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.onOptionsChanged() }
    }

    fun interface OptionsChangeListener {
        fun onOptionsChanged()
    }
}