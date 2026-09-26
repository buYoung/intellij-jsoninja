package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBPanel
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import java.awt.BorderLayout
import javax.swing.JComponent

class ConvertTypeDialogView(
    jsonToTypeComponent: JComponent,
    typeToJsonComponent: JComponent,
    project: Project? = null,
) {
    private val tabbedPane = JBTabbedPane()
    private val rootPanel = JBPanel<JBPanel<*>>(BorderLayout())

    init {
        tabbedPane.addTab(
            LocalizationBundle.message("action.json.to.type.text"),
            JsoninjaIcons.getJsonToTypeIcon(project),
            jsonToTypeComponent,
        )
        tabbedPane.addTab(
            LocalizationBundle.message("action.type.to.json.text"),
            JsoninjaIcons.getTypeToJsonIcon(project),
            typeToJsonComponent,
        )
        rootPanel.add(tabbedPane, BorderLayout.CENTER)
    }

    val component: JComponent
        get() = rootPanel

    fun setSelectedTabIndex(index: Int) {
        tabbedPane.selectedIndex = index.coerceIn(0, tabbedPane.tabCount - 1)
    }

    fun getSelectedTabIndex(): Int = tabbedPane.selectedIndex

    fun setOnTabChanged(callback: () -> Unit) {
        tabbedPane.addChangeListener { callback() }
    }
}
