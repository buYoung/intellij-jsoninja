package com.livteam.jsoninja.ui.component.tab

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonHelperService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.ui.component.model.TabUiState
import java.awt.Component
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JsonTabsPresenter(
    private val project: Project,
    private val parentDisposable: Disposable,
    private val view: JsonTabsView
) {
    private var tabCounter = 1
    private var isInitializingTabs = false
    private var isBulkClosingTabs = false
    private var onTabSelectedListener: ((JsonEditorView?) -> Unit)? = null
    private var onTabContentChangedListener: ((String) -> Unit)? = null
    private var onLastJsonTabClosedListener: (() -> Unit)? = null

    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val helperService = project.getService(JsonHelperService::class.java)
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    private val tabContextFactory = JsonTabContextFactory(
        project = project,
        parentDisposable = parentDisposable,
        formatterService = formatterService,
        helperService = helperService,
        onOpenInNewTabRequested = { content ->
            addNewTabFromPlusTab(content = content, fileExtension = "json")
        },
    )

    private val tabContexts = mutableMapOf<Component, TabUiState>()
    private val tabFileExtensions = mutableMapOf<Component, String?>()

    companion object {
        private const val TAB_TITLE_PREFIX = "JSON "
    }

    init {
        view.setPresenter(this)
        view.addChangeListener {
            val currentEditor = getCurrentEditor()
            onTabSelectedListener?.invoke(currentEditor)
            if (!isInitializingTabs && !isBulkClosingTabs && currentEditor != null) {
                requestFocusOnEditor(currentEditor)
            }
        }
        Disposer.register(parentDisposable) {
            coroutineScope.cancel()
            tabContexts.clear()
            tabFileExtensions.clear()
        }
    }

    fun setupInitialTabs() {
        launchOnEdt {
            isInitializingTabs = true
            try {
                addNewTabInternal(index = 0, shouldRequestFocus = false)
            } finally {
                isInitializingTabs = false
            }
        }
    }

    fun onPlusTabSelected() {
        launchOnEdt {
            addNewTabFromPlusTab()
        }
    }

    fun addNewTabFromPlusTab(
        content: String = "",
        fileExtension: String? = null,
        title: String? = null,
        tooltip: String? = null,
    ) {
        addNewTabInternal(
            index = view.tabCount,
            content = content,
            fileExtension = fileExtension,
            customTitle = title,
            tooltip = tooltip,
            shouldRequestFocus = true,
        )
    }

    private fun addNewTabInternal(
        index: Int,
        content: String = "",
        fileExtension: String? = null,
        customTitle: String? = null,
        tooltip: String? = null,
        shouldRequestFocus: Boolean = true,
    ): JsonEditorView {
        val title = customTitle?.takeIf { it.isNotBlank() } ?: "$TAB_TITLE_PREFIX${tabCounter++}"
        val tabContext = tabContextFactory.create(
            title = title,
            content = content,
            fileExtension = fileExtension,
            onTabContentChangedListener = onTabContentChangedListener
        )

        tabContexts[tabContext.panel] = tabContext
        tabFileExtensions[tabContext.panel] = fileExtension

        view.insertEditorTab(title, tabContext.panel, index, tooltip)
        tabContext.panel.border = JBUI.Borders.empty()

        val insertedIndex = view.indexOfComponent(tabContext.panel)
        if (insertedIndex != -1) {
            view.selectedIndex = insertedIndex
        }
        if (shouldRequestFocus) {
            requestFocusOnEditor(tabContext.editor)
        }

        return tabContext.editor
    }

    fun onTabCloseClicked(tabContentComponent: Component) {
        launchOnEdt {
            val closableTabIndex = view.indexOfComponent(tabContentComponent)
            if (closableTabIndex == -1) return@launchOnEdt
            closeTabAt(closableTabIndex)
        }
    }

    fun closeOtherTabs(keepComponent: Component) {
        launchOnEdt {
            val componentsToClose = view.getAllTabComponents().filter { it !== keepComponent }
            if (componentsToClose.isEmpty()) return@launchOnEdt

            isBulkClosingTabs = true
            try {
                for (component in componentsToClose) {
                    disposeTabComponent(component)
                    view.removeTab(component)
                }
            } finally {
                isBulkClosingTabs = false
            }

            val keptIndex = view.indexOfComponent(keepComponent)
            if (keptIndex != -1) {
                view.selectedIndex = keptIndex
            }
            getCurrentEditor()?.let(::requestFocusOnEditor)
        }
    }

    fun closeAllTabs() {
        launchOnEdt {
            val allComponents = view.getAllTabComponents()
            if (allComponents.isEmpty()) return@launchOnEdt

            isBulkClosingTabs = true
            try {
                for (component in allComponents) {
                    disposeTabComponent(component)
                    view.removeTab(component)
                }
                tabCounter = 1
                addNewTabInternal(index = 0, shouldRequestFocus = false)
            } finally {
                isBulkClosingTabs = false
            }
            onLastJsonTabClosedListener?.invoke()
        }
    }

    fun renameTab(tabContentComponent: Component, newTitle: String) {
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isEmpty()) return
        if (view.indexOfComponent(tabContentComponent) == -1) return

        view.setTabTitle(tabContentComponent, trimmedTitle)
        tabContexts[tabContentComponent]?.panel?.name = trimmedTitle
    }

    fun duplicateTab(sourceComponent: Component) {
        launchOnEdt {
            val sourceContext = tabContexts[sourceComponent] ?: return@launchOnEdt
            val sourceIndex = view.indexOfComponent(sourceComponent)
            if (sourceIndex == -1) return@launchOnEdt

            val sourceTitle = view.getTabTitle(sourceComponent).orEmpty()
            val duplicateTitle = buildDuplicateTitle(sourceTitle)
            addNewTabInternal(
                index = sourceIndex + 1,
                content = sourceContext.editor.getText(),
                fileExtension = tabFileExtensions[sourceComponent],
                customTitle = duplicateTitle,
                shouldRequestFocus = true,
            )
        }
    }

    private fun buildDuplicateTitle(sourceTitle: String): String {
        if (sourceTitle.isBlank() || sourceTitle.startsWith(TAB_TITLE_PREFIX)) {
            return "$TAB_TITLE_PREFIX${tabCounter++}"
        }
        val existingTitles = view.getAllTabComponents().mapNotNull { view.getTabTitle(it) }.toSet()
        var copyNumber = 2
        var candidate = "$sourceTitle ($copyNumber)"
        while (candidate in existingTitles) {
            copyNumber += 1
            candidate = "$sourceTitle ($copyNumber)"
        }
        return candidate
    }

    private fun launchOnEdt(action: () -> Unit) {
        coroutineScope.launch {
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) return@withContext
                action()
            }
        }
    }

    fun canCloseCurrentTab(): Boolean {
        val selectedComponent = view.selectedComponent ?: return false
        return getJsonTabCount() > 0 && !view.isPlusTabComponent(selectedComponent)
    }

    fun isPlusTabSelected(): Boolean = false

    fun closeCurrentTab(): Boolean {
        return closeTabAt(view.selectedIndex)
    }

    private fun closeTabAt(index: Int): Boolean {
        if (index < 0 || index >= view.tabCount) {
            return false
        }

        val component = view.getComponentAt(index) ?: return false
        val isLastJsonTab = getJsonTabCount() <= 1
        val nextSelectedIndex = if (index > 0) index - 1 else 0

        disposeTabComponent(component)
        view.removeTab(index)

        if (isLastJsonTab) {
            tabCounter = 1
            addNewTabInternal(index = 0, shouldRequestFocus = false)
            onLastJsonTabClosedListener?.invoke()
        } else if (view.tabCount > 0) {
            val resolvedIndex = if (nextSelectedIndex < view.tabCount) nextSelectedIndex else view.tabCount - 1
            view.selectedIndex = resolvedIndex
            getCurrentEditor()?.let(::requestFocusOnEditor)
        }

        return true
    }

    private fun requestFocusOnEditor(editor: JsonEditorView) {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed && getCurrentEditor() === editor) {
                editor.focusContent()
            }
        }, ModalityState.stateForComponent(view))
    }

    private fun disposeTabComponent(component: Component?) {
        if (component == null) return
        tabFileExtensions.remove(component)
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

    fun hasEditor(editor: JsonEditorView): Boolean = tabContexts.values.any { it.editor === editor }

    fun setOnTabSelectedListener(listener: (JsonEditorView?) -> Unit) {
        this.onTabSelectedListener = listener
    }

    fun setOnTabContentChangedListener(listener: (String) -> Unit) {
        this.onTabContentChangedListener = listener
    }

    fun setOnLastJsonTabClosedListener(listener: () -> Unit) {
        this.onLastJsonTabClosedListener = listener
    }

    fun getView(): JsonTabsView {
        return view
    }
}
