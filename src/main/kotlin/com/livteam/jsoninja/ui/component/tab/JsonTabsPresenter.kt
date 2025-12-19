package com.livteam.jsoninja.ui.component.tab

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonHelperService
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.ui.component.jsonQuery.JsonQueryPresenter
import com.livteam.jsoninja.ui.component.model.JsonQueryModel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel

class JsonTabsPresenter(
    private val project: Project,
    private val parentDisposable: Disposable,
    private val view: JsonTabsView
) {
    private var tabCounter = 1
    private var onTabSelectedListener: ((JsonEditorView?) -> Unit)? = null
    private var onTabContentChangedListener: ((String) -> Unit)? = null

    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val helperService = project.getService(JsonHelperService::class.java)

    private val tabContexts = mutableMapOf<Component, TabContext>()

    companion object {
        private const val TAB_TITLE_PREFIX = "JSON "
    }

    private data class TabContext(
        val panel: JPanel,
        val editor: JsonEditorView,
        val disposable: Disposable
    )

    init {
        view.setPresenter(this)
        view.addChangeListener {
            onTabSelectedListener?.invoke(getCurrentEditor())
        }
        Disposer.register(parentDisposable) {
            tabContexts.clear()
        }
    }

    fun setupInitialTabs() {
        invokeLater {
            addNewTabInternal(0)
            addPlusTab()
        }
    }

    private fun addPlusTab() {
        view.addPlusTab()
        val plusTabIndex = view.getPlusTabIndex()
        if (plusTabIndex != -1) {
            view.setTabTooltip(plusTabIndex, LocalizationBundle.message("addTab"))
        }
    }

    fun onPlusTabSelected() {
        invokeLater {
            addNewTabFromPlusTab()
        }
    }

    fun addNewTabFromPlusTab(content: String = "", fileExtension: String? = null) {
        val plusTabIndex = view.getPlusTabIndex()

        if (plusTabIndex != -1) {
            addNewTabInternal(plusTabIndex, content, fileExtension)
        } else {
            addNewTabInternal(view.tabCount, content, fileExtension)
        }
    }

    private fun addNewTabInternal(index: Int, content: String = "", fileExtension: String? = null): JsonEditorView {
        val title = "$TAB_TITLE_PREFIX${tabCounter++}"
        val tabContext = createTabContext(title, content, fileExtension)

        tabContexts[tabContext.panel] = tabContext

        view.insertEditorTab(title, tabContext.panel, index)
        tabContext.panel.border = JBUI.Borders.empty()

        view.selectedIndex = index

        return tabContext.editor
    }

    private fun createTabContext(title: String, content: String, fileExtension: String?): TabContext {
        val model = JsonQueryModel()
        val editor = createEditor(model, fileExtension)

        if (content.isNotEmpty()) {
            editor.setText(content)
        }

        val tabContentPanel = JPanel(BorderLayout(0, 0)).apply {
            name = title
        }

        val tabDisposable = Disposer.newDisposable("JsonHelperTab-$title")
        Disposer.register(parentDisposable, tabDisposable)
        Disposer.register(tabDisposable, editor)

        val jsonQueryPresenter = JsonQueryPresenter(project, model)
        Disposer.register(tabDisposable, jsonQueryPresenter)
        val jmesComponent = jsonQueryPresenter.getComponent().apply {
            border = JBUI.Borders.emptyTop(3)
        }

        tabContentPanel.add(jmesComponent, BorderLayout.NORTH)
        tabContentPanel.add(editor, BorderLayout.CENTER)

        setupJmesPathPresenter(jsonQueryPresenter, editor, initialJson = content)

        return TabContext(
            panel = tabContentPanel,
            editor = editor,
            disposable = tabDisposable
        )
    }

    private fun createEditor(model: JsonQueryModel, fileExtension: String? = null): JsonEditorView {
        return JsonEditorView(project, model, fileExtension).apply {
            setOnContentChangeCallback { newContent ->
                onTabContentChangedListener?.invoke(newContent)
            }
        }
    }

    private fun setupJmesPathPresenter(
        jsonQueryPresenter: JsonQueryPresenter,
        editor: JsonEditorView,
        initialJson: String? = null
    ) {
        initialJson?.takeIf { it.isNotBlank() }?.let {
            jsonQueryPresenter.setOriginalJson(it)
        }

        jsonQueryPresenter.setOnBeforeSearchCallback {
            if (!jsonQueryPresenter.hasOriginalJson()) {
                val editorText = editor.getText()
                if (editorText.isNotBlank()) {
                    jsonQueryPresenter.setOriginalJson(editorText)
                } else {
                    return@setOnBeforeSearchCallback
                }
            }
        }

        jsonQueryPresenter.setOnSearchCallback { originalJson, resultJson ->
            val jsonFormatState = helperService.getJsonFormatState()
            val formattedJson = formatterService.formatJson(resultJson, jsonFormatState)
            editor.setText(formattedJson)
        }
    }


    fun onTabCloseClicked(tabContentComponent: Component) {
        invokeLater {
            val closableTabIndex = view.indexOfComponent(tabContentComponent)
            if (closableTabIndex == -1) return@invokeLater
            closeTabAt(closableTabIndex)
        }
    }

    fun canCloseCurrentTab(): Boolean {
        val selectedComponent = view.selectedComponent
        if (selectedComponent == null || view.isPlusTabComponent(selectedComponent)) {
            return false
        }

        return getJsonTabCount() > 0
    }

    fun isPlusTabSelected(): Boolean {
        return view.isPlusTabComponent(view.selectedComponent)
    }

    fun closeCurrentTab(): Boolean {
        return closeTabAt(view.selectedIndex)
    }

    private fun closeTabAt(index: Int): Boolean {
        if (index < 0 || index >= view.tabCount) {
            return false
        }

        val component = view.getComponentAt(index)
        if (component == null || view.isPlusTabComponent(component)) {
            return false
        }

        val isLastJsonTab = getJsonTabCount() <= 1
        val nextSelectedIndex = if (index > 0) index - 1 else 0

        disposeTabComponent(component)
        view.removeTab(index)

        if (isLastJsonTab) {
            tabCounter = 1
            addNewTabInternal(0)
            ToolWindowManager.getInstance(project).getToolWindow("JSONinja")?.hide()
        } else if (view.tabCount > 0) {
            view.selectedIndex = if (nextSelectedIndex < view.tabCount) nextSelectedIndex else view.tabCount - 1
        }

        return true
    }

    private fun disposeTabComponent(component: Component?) {
        if (component == null) return
        tabContexts.remove(component)?.let { Disposer.dispose(it.disposable) }
    }

    fun getJsonTabCount(): Int {
        var count = 0
        for (i in 0 until view.tabCount) {
            val component = view.getComponentAt(i)
            if (component != null && !view.isPlusTabComponent(component)) {
                count++
            }
        }
        return count
    }

    fun getCurrentEditor(): JsonEditorView? {
        val currentSelectedComponent = view.selectedComponent
        if (currentSelectedComponent == null || view.isPlusTabComponent(currentSelectedComponent)) {
            return null
        }

        return tabContexts[currentSelectedComponent]?.editor
    }

    fun setOnTabSelectedListener(listener: (JsonEditorView?) -> Unit) {
        this.onTabSelectedListener = listener
    }

    fun setOnTabContentChangedListener(listener: (String) -> Unit) {
        this.onTabContentChangedListener = listener
    }

    fun getView(): JsonTabsView {
        return view
    }
}
