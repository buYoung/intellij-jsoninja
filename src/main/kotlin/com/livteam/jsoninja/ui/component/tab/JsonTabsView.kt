package com.livteam.jsoninja.ui.component.tab

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBTabbedPane
import com.livteam.jsoninja.LocalizationBundle
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class JsonTabsView : JBTabbedPane() {
    private var presenter: JsonTabsPresenter? = null
    private var plusTabMouseListener: MouseAdapter? = null

    companion object {
        const val ADD_NEW_TAB_COMPONENT_NAME = "addNewTab"
    }

    fun setPresenter(presenter: JsonTabsPresenter) {
        this.presenter = presenter

        // 탭 변경 리스너는 JBTabbedPane의 기본 기능 사용, Presenter에서 addChangeListener 호출

        plusTabMouseListener?.let { removeMouseListener(it) }
        plusTabMouseListener = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val selectedComponent = this@JsonTabsView.selectedComponent
                if (selectedComponent?.name == ADD_NEW_TAB_COMPONENT_NAME) {
                    this@JsonTabsView.presenter?.onPlusTabSelected()
                }
            }
        }
        addMouseListener(plusTabMouseListener)
    }

    fun addPlusTab() {
        val plusPanel = JPanel().apply {
            name = ADD_NEW_TAB_COMPONENT_NAME
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = LocalizationBundle.message("addTab")
        }
        addTab("", AllIcons.General.Add, plusPanel)
    }

    fun setTabTooltip(index: Int, text: String) {
        if (index >= 0 && index < tabCount) {
            setToolTipTextAt(index, text)
        }
    }

    fun insertEditorTab(title: String, component: JComponent, index: Int) {
        insertTab(title, null, component, null, index)
        setTabComponentAt(index, createTabComponent(title, component))
    }

    fun removeTab(index: Int) {
        if (index >= 0 && index < tabCount) {
            removeTabAt(index)
        }
    }

    private fun createTabComponent(title: String, contentComponent: JComponent): JPanel {
        val panel = JPanel(BorderLayout(5, 0)).apply {
            isOpaque = false
        }

        val titleLabel = JLabel(title)
        panel.add(titleLabel, BorderLayout.CENTER)

        val closeLabel = JLabel(AllIcons.Actions.Close).apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                private val originalIcon = icon
                private val hoverIcon = AllIcons.Actions.CloseHovered

                override fun mouseEntered(e: MouseEvent) {
                    icon = hoverIcon
                }

                override fun mouseExited(e: MouseEvent) {
                    icon = originalIcon
                }

                override fun mouseClicked(e: MouseEvent) {
                    presenter?.onTabCloseClicked(contentComponent)
                }
            })
        }
        panel.add(closeLabel, BorderLayout.EAST)

        return panel
    }
}
