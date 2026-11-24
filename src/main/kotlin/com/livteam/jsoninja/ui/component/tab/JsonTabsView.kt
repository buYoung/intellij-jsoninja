package com.livteam.jsoninja.ui.component.tab

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class JsonTabsView(
    private val project: Project,
    parentDisposable: Disposable,
) : JPanel(BorderLayout()) {
    private val tabs: JBTabs = JBTabsFactory.createEditorTabs(project, parentDisposable)
    private var presenter: JsonTabsPresenter? = null
    private val selectionListeners = mutableListOf<() -> Unit>()
    private val addTabPaneActionGroup = DefaultActionGroup(createAddTabAction())

    companion object {
        const val ADD_NEW_TAB_COMPONENT_NAME = "addNewTab"
    }

    init {
        tabs.presentation
            .setSingleRow(true)
            .setTabDraggingEnabled(true)
            .setTabLabelActionsAutoHide(false)
            .setInnerInsets(JBUI.emptyInsets())

        tabs.addListener(object : TabsListener {
            override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
                selectionListeners.forEach { it.invoke() }
            }
        }, parentDisposable)

        tabs.addTabMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    val tabInfo = tabs.findInfo(e) ?: return
                    e.consume()
                    presenter?.onTabCloseClicked(tabInfo.component)
                }
            }
        })

        tabs.component.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.clickCount != 2) return
                if (tabs.findInfo(e) != null) return
                if (!isPointInTabHeaderStrip(e.point)) return
                e.consume()
                presenter?.onPlusTabSelected()
            }
        })

        tabs.setPopupGroup(createTabPopupGroup(), ActionPlaces.EDITOR_TAB_POPUP, false)
        add(tabs.component, BorderLayout.CENTER)
    }

    val tabCount: Int
        get() = tabs.tabCount

    val selectedComponent: Component?
        get() = tabs.selectedInfo?.component

    var selectedIndex: Int
        get() = tabs.selectedInfo?.let(tabs::getIndexOf) ?: -1
        set(value) {
            if (value in 0 until tabs.tabCount) {
                tabs.select(tabs.getTabAt(value), false)
            }
        }

    fun setPresenter(presenter: JsonTabsPresenter) {
        this.presenter = presenter
    }

    fun addChangeListener(listener: () -> Unit) {
        selectionListeners.add(listener)
    }

    fun getComponentAt(index: Int): Component? {
        return if (index in 0 until tabs.tabCount) tabs.getTabAt(index).component else null
    }

    fun indexOfComponent(component: Component): Int {
        return tabs.findInfo(component)?.let(tabs::getIndexOf) ?: -1
    }

    fun getAllTabComponents(): List<Component> {
        return tabs.tabs.map { it.component }
    }

    fun getTabTitle(component: Component): String? {
        return tabs.findInfo(component)?.text
    }

    fun setTabTitle(component: Component, title: String) {
        tabs.findInfo(component)?.setText(title)
    }

    fun isPlusTabComponent(component: Component?): Boolean = false

    fun setTabTooltip(index: Int, text: String) {
        if (index in 0 until tabs.tabCount) {
            applyTabTooltip(tabs.getTabAt(index), text)
        }
    }

    fun insertEditorTab(title: String, component: JComponent, index: Int, tooltip: String? = null) {
        val tabInfo = TabInfo(component).setText(title)
        if (!tooltip.isNullOrBlank()) {
            applyTabTooltip(tabInfo, tooltip)
        }

        val closeTabAction = object : DumbAwareAction(
            LocalizationBundle.messagePointer("tab.action.close"),
            LocalizationBundle.messagePointer("tab.action.close"),
            AllIcons.Actions.Close,
        ) {
            init {
                templatePresentation.hoveredIcon = AllIcons.Actions.CloseHovered
            }

            override fun actionPerformed(e: AnActionEvent) {
                presenter?.onTabCloseClicked(component)
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        tabInfo.setTabLabelActions(DefaultActionGroup(closeTabAction), ActionPlaces.EDITOR_TAB)
        tabInfo.setTabPaneActions(addTabPaneActionGroup)

        val safeIndex = index.coerceIn(0, tabs.tabCount)
        tabs.addTab(tabInfo, safeIndex)
    }

    private fun createAddTabAction(): DumbAwareAction {
        return object : DumbAwareAction(
            LocalizationBundle.messagePointer("addTab"),
            LocalizationBundle.messagePointer("addTabDescription"),
            AllIcons.General.Add,
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                presenter?.onPlusTabSelected()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
    }

    private fun isPointInTabHeaderStrip(point: Point): Boolean {
        if (point.y < 0) return false
        if (tabs.tabCount == 0) return true

        val selectedContent = tabs.selectedInfo?.component
        if (selectedContent != null && selectedContent.isShowing) {
            val contentTopY = SwingUtilities.convertPoint(selectedContent, Point(0, 0), tabs.component).y
            if (contentTopY > 0) {
                return point.y < contentTopY
            }
        }

        val firstTabLabel = tabs.getTabLabel(tabs.getTabAt(0)) ?: return false
        val labelParent = firstTabLabel.parent ?: return false
        val headerBounds = SwingUtilities.convertRectangle(labelParent, firstTabLabel.bounds, tabs.component)
        return headerBounds.height > 0 && point.y <= headerBounds.y + headerBounds.height
    }

    fun removeTab(index: Int) {
        if (index in 0 until tabs.tabCount) {
            tabs.removeTab(tabs.getTabAt(index))
        }
    }

    fun removeTab(component: Component) {
        tabs.findInfo(component)?.let { tabs.removeTab(it) }
    }

    private fun createTabPopupGroup(): DefaultActionGroup {
        return DefaultActionGroup().apply {
            add(object : DumbAwareAction(LocalizationBundle.messagePointer("tab.action.close")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val target = resolvePopupTargetInfo() ?: return
                    presenter?.onTabCloseClicked(target.component)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = resolvePopupTargetInfo() != null
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : DumbAwareAction(LocalizationBundle.messagePointer("tab.action.close.others")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val target = resolvePopupTargetInfo() ?: return
                    presenter?.closeOtherTabs(target.component)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = resolvePopupTargetInfo() != null && tabs.tabCount > 1
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : DumbAwareAction(LocalizationBundle.messagePointer("tab.action.close.all")) {
                override fun actionPerformed(e: AnActionEvent) {
                    presenter?.closeAllTabs()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = tabs.tabCount > 0
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            addSeparator()

            add(object : DumbAwareAction(LocalizationBundle.messagePointer("tab.action.rename")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val target = resolvePopupTargetInfo() ?: return
                    promptRenameTab(target)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = resolvePopupTargetInfo() != null
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : DumbAwareAction(LocalizationBundle.messagePointer("tab.action.duplicate")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val target = resolvePopupTargetInfo() ?: return
                    presenter?.duplicateTab(target.component)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = resolvePopupTargetInfo() != null
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })
        }
    }

    private fun resolvePopupTargetInfo(): TabInfo? {
        return tabs.targetInfo ?: tabs.selectedInfo
    }

    private fun promptRenameTab(targetInfo: TabInfo) {
        if (project.isDisposed) return
        val newTitle = Messages.showInputDialog(
            project,
            LocalizationBundle.message("tab.action.rename.message"),
            LocalizationBundle.message("tab.action.rename.title"),
            null,
            targetInfo.text,
            object : InputValidator {
                override fun checkInput(inputString: String?): Boolean = !inputString.isNullOrBlank()
                override fun canClose(inputString: String?): Boolean = !inputString.isNullOrBlank()
            },
        )?.trim()

        if (!newTitle.isNullOrBlank()) {
            presenter?.renameTab(targetInfo.component, newTitle)
        }
    }

    private fun applyTabTooltip(tabInfo: TabInfo, text: String) {
        val htmlChunk = HtmlChunk.text(text)
        val appliedHtml = invokeTabTooltipMethod(tabInfo, "setTooltipText", HtmlChunk::class.java, htmlChunk) ||
            invokeTabTooltipMethod(tabInfo, "setTooltipHtmlText", HtmlChunk::class.java, htmlChunk)
        if (!appliedHtml) {
            invokeTabTooltipMethod(tabInfo, "setTooltipText", String::class.java, text)
        }
    }

    private fun invokeTabTooltipMethod(
        tabInfo: TabInfo,
        methodName: String,
        parameterType: Class<*>,
        argument: Any,
    ): Boolean {
        return runCatching {
            TabInfo::class.java.getMethod(methodName, parameterType).invoke(tabInfo, argument)
        }.isSuccess
    }
}
