package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class GenerateJsonDialogView(
    private val randomTabComponent: JComponent,
    private val schemaTabComponent: JComponent,
    private val onLayoutChanged: () -> Unit
) {
    private lateinit var tabbedPane: JBTabbedPane

    val component: JComponent by lazy { createComponent() }

    fun getGenerationMode(): JsonGenerationMode {
        return if (tabbedPane.selectedIndex == 1) {
            JsonGenerationMode.SCHEMA
        } else {
            JsonGenerationMode.RANDOM
        }
    }

    private fun createComponent(): JComponent {
        tabbedPane = JBTabbedPane()
        tabbedPane.addTab(LocalizationBundle.message("dialog.generate.json.tab.random"), randomTabComponent)
        tabbedPane.addTab(LocalizationBundle.message("dialog.generate.json.tab.schema"), schemaTabComponent)
        tabbedPane.addChangeListener {
            onLayoutChanged()
        }

        return JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(640, 500)
            add(tabbedPane, BorderLayout.CENTER)
        }
    }
}
