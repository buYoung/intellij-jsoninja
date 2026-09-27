package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class GenerateJsonDialogView(
    private val randomTabComponent: JComponent,
    private val schemaTabComponent: JComponent,
    private val onGenerationModeChanged: () -> Unit
) {
    private lateinit var tabbedPane: JBTabbedPane
    private val summaryLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    val component: JComponent by lazy { createComponent() }

    fun getGenerationMode(): JsonGenerationMode {
        return if (tabbedPane.selectedIndex == 1) {
            JsonGenerationMode.SCHEMA
        } else {
            JsonGenerationMode.RANDOM
        }
    }

    fun setSummary(summary: String) {
        summaryLabel.text = summary
    }

    private fun createComponent(): JComponent {
        tabbedPane = object : JBTabbedPane() {
            override fun getPreferredSize(): Dimension {
                val size = super.getPreferredSize()
                return Dimension(maxOf(size.width, JBUI.scale(600)), size.height)
            }

            override fun getMinimumSize(): Dimension {
                val size = super.getMinimumSize()
                return Dimension(maxOf(size.width, JBUI.scale(560)), size.height)
            }
        }
        tabbedPane.tabComponentInsets = null
        tabbedPane.addTab(LocalizationBundle.message("dialog.generate.json.tab.random"), createTabContainer(randomTabComponent))
        tabbedPane.addTab(LocalizationBundle.message("dialog.generate.json.tab.schema"), createTabContainer(schemaTabComponent))
        tabbedPane.addChangeListener {
            onGenerationModeChanged()
        }

        val footer = panel {
            separator()
            row { cell(summaryLabel) }
            row { comment(LocalizationBundle.message("dialog.generate.json.output.destination")) }
        }.apply {
            border = JBUI.Borders.empty(0, 12, 0, 12)
        }

        return JPanel(BorderLayout()).apply {
            add(tabbedPane, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }
    }

    private fun createTabContainer(content: JComponent): JComponent {
        return object : JPanel(BorderLayout()) {
            init {
                add(content, BorderLayout.CENTER)
            }

            // 숨겨진 탭이 현재 탭의 기본 크기와 최소 크기를 늘리지 않도록 한다.
            override fun getPreferredSize(): Dimension = if (isVisible) super.getPreferredSize() else Dimension()

            override fun getMinimumSize(): Dimension = if (isVisible) super.getMinimumSize() else Dimension()
        }
    }
}
