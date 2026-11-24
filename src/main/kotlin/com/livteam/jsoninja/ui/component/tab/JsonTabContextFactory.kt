package com.livteam.jsoninja.ui.component.tab

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonHelperService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.ui.component.editor.JsonEditorView
import com.livteam.jsoninja.ui.component.jsonQuery.JsonQueryPresenter
import com.livteam.jsoninja.ui.component.model.JsonQueryUiState
import com.livteam.jsoninja.ui.component.model.TabUiState
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JsonTabContextFactory(
    private val project: Project,
    private val parentDisposable: Disposable,
    private val formatterService: JsonFormatterService,
    private val helperService: JsonHelperService,
    private val onOpenInNewTabRequested: ((String) -> Unit)? = null,
) {
    companion object {
        private const val MAX_BANNER_QUERY_LENGTH = 60
    }

    fun create(
        title: String,
        content: String,
        fileExtension: String?,
        onTabContentChangedListener: ((String) -> Unit)?
    ): TabUiState {
        val model = JsonQueryUiState()
        val editor = createEditor(fileExtension, onTabContentChangedListener)

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

        val modeToolbar = createModeAndTreeToolbar(editor, tabContentPanel)
        editor.setOnDisplayModeChangedListener {
            modeToolbar.updateActionsAsync()
        }
        registerToggleDisplayModeShortcut(editor, tabContentPanel, tabDisposable)

        val headerBar = JPanel(BorderLayout(4, 0)).apply {
            add(jmesComponent, BorderLayout.CENTER)
            add(modeToolbar.component, BorderLayout.EAST)
        }

        val filterBanner = createFilterBanner(jsonQueryPresenter, editor)
        val topContainer = JPanel(BorderLayout(0, 0)).apply {
            add(headerBar, BorderLayout.NORTH)
            add(filterBanner, BorderLayout.SOUTH)
        }

        tabContentPanel.add(topContainer, BorderLayout.NORTH)
        tabContentPanel.add(editor, BorderLayout.CENTER)

        setupJmesPathPresenter(jsonQueryPresenter, editor, filterBanner, topContainer, tabDisposable)

        return TabUiState(
            panel = tabContentPanel,
            editor = editor,
            queryPresenter = jsonQueryPresenter,
            disposable = tabDisposable
        )
    }

    private fun createModeAndTreeToolbar(
        editor: JsonEditorView,
        targetComponent: JComponent,
    ): ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            add(object : DumbAwareAction(
                LocalizationBundle.messagePointer("tree.action.expand.all"),
                LocalizationBundle.messagePointer("tree.action.expand.all"),
                AllIcons.Actions.Expandall,
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    editor.expandAllTreeNodes()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabledAndVisible = editor.isTreeMode()
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : DumbAwareAction(
                LocalizationBundle.messagePointer("tree.action.collapse.all"),
                LocalizationBundle.messagePointer("tree.action.collapse.all"),
                AllIcons.Actions.Collapseall,
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    editor.collapseAllTreeNodes()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabledAndVisible = editor.isTreeMode()
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            addSeparator()

            add(object : DumbAwareToggleAction(
                LocalizationBundle.messagePointer("editor.mode.text"),
                LocalizationBundle.messagePointer("editor.mode.toggle.tooltip"),
                AllIcons.FileTypes.Text,
            ) {
                init {
                    templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
                }

                override fun isSelected(e: AnActionEvent): Boolean = editor.isTextMode()

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    if (state && !editor.isTextMode()) {
                        editor.switchToTextMode()
                    }
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : DumbAwareToggleAction(
                LocalizationBundle.messagePointer("editor.mode.tree"),
                LocalizationBundle.messagePointer("editor.mode.toggle.tooltip"),
                AllIcons.Actions.ShowAsTree,
            ) {
                init {
                    templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
                }

                override fun isSelected(e: AnActionEvent): Boolean = editor.isTreeMode()

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    if (state && !editor.isTreeMode()) {
                        editor.switchToTreeMode()
                    }
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })
        }

        return ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true)
            .apply {
                this.targetComponent = targetComponent
                setReservePlaceAutoPopupIcon(false)
                component.border = JBUI.Borders.emptyTop(3)
            }
    }

    private fun registerToggleDisplayModeShortcut(
        editor: JsonEditorView,
        tabContentPanel: JComponent,
        tabDisposable: Disposable,
    ) {
        val shortcut = CustomShortcutSet.fromString(
            if (SystemInfo.isMac) "control shift T" else "alt shift T",
        )
        val toggleAction = object : DumbAwareAction(
            LocalizationBundle.messagePointer("editor.mode.toggle.tooltip"),
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                editor.toggleDisplayMode()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        toggleAction.registerCustomShortcutSet(shortcut, tabContentPanel, tabDisposable)
    }

    private fun createFilterBanner(
        jsonQueryPresenter: JsonQueryPresenter,
        editor: JsonEditorView,
    ): EditorNotificationPanel {
        return EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
            isVisible = false
            createActionLabel(LocalizationBundle.message("query.filtered.restore")) {
                jsonQueryPresenter.restoreOriginalJson()
            }
            if (onOpenInNewTabRequested != null) {
                createActionLabel(LocalizationBundle.message("query.filtered.open.tab")) {
                    onOpenInNewTabRequested.invoke(editor.getText())
                }
            }
            createActionLabel(LocalizationBundle.message("query.filtered.apply")) {
                jsonQueryPresenter.applyFilteredResult()
                editor.focusContent()
            }
        }
    }

    private fun createEditor(
        fileExtension: String? = null,
        onTabContentChangedListener: ((String) -> Unit)?
    ): JsonEditorView {
        return JsonEditorView(project, fileExtension).apply {
            if (onTabContentChangedListener != null) {
                setOnContentChangeCallback(onTabContentChangedListener)
            }
        }
    }

    private fun setupJmesPathPresenter(
        jsonQueryPresenter: JsonQueryPresenter,
        editor: JsonEditorView,
        filterBanner: EditorNotificationPanel,
        topContainer: JPanel,
        tabDisposable: Disposable,
    ) {
        val queryResultFormatScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
        var queryResultFormatJob: Job? = null
        var isApplyingQueryResult = false
        val isTabDisposed = AtomicBoolean(false)

        fun updateFilterBanner(activeQuery: String?) {
            if (activeQuery.isNullOrBlank()) {
                if (filterBanner.isVisible) {
                    filterBanner.isVisible = false
                    topContainer.revalidate()
                    topContainer.repaint()
                }
            } else {
                val displayQuery = if (activeQuery.length > MAX_BANNER_QUERY_LENGTH) {
                    activeQuery.take(MAX_BANNER_QUERY_LENGTH) + "…"
                } else {
                    activeQuery
                }
                filterBanner.text = LocalizationBundle.message("query.filtered.banner", displayQuery)
                filterBanner.isVisible = true
                topContainer.revalidate()
                topContainer.repaint()
            }
        }

        editor.editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (!isApplyingQueryResult && !jsonQueryPresenter.isFiltered()) {
                    // Invalidate cached query source lazily instead of copying document.text on every keystroke.
                    jsonQueryPresenter.setOriginalJson("")
                    queryResultFormatJob?.cancel()
                }
            }
        }, tabDisposable)
        Disposer.register(tabDisposable) {
            isTabDisposed.set(true)
            queryResultFormatJob?.cancel()
            queryResultFormatScope.cancel()
        }

        jsonQueryPresenter.setOnBeforeSearchCallback {
            queryResultFormatJob?.cancel()
            if (!jsonQueryPresenter.hasOriginalJson()) {
                val editorText = editor.getText()
                if (editorText.isNotBlank()) {
                    jsonQueryPresenter.setOriginalJson(editorText)
                } else {
                    return@setOnBeforeSearchCallback
                }
            }
        }

        jsonQueryPresenter.setOnSearchCallback { _, resultJson ->
            queryResultFormatJob?.cancel()
            val requestId = jsonQueryPresenter.getCurrentRequestId()
            val activeQuery = jsonQueryPresenter.getActiveQuery()
            val document = editor.editor.document
            val modificationStamp = document.modificationStamp
            val jsonFormatState = helperService.getJsonFormatState()
            queryResultFormatJob = queryResultFormatScope.launch {
                try {
                    val formattedJson = formatterService.formatJsonOnDefault(resultJson, jsonFormatState)
                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed || isTabDisposed.get()) return@withContext
                        if (!jsonQueryPresenter.isCurrentRequest(requestId) || editor.editor.document !== document ||
                            document.modificationStamp != modificationStamp
                        ) return@withContext
                        isApplyingQueryResult = true
                        try {
                            editor.setText(formattedJson)
                        } finally {
                            isApplyingQueryResult = false
                        }
                        updateFilterBanner(activeQuery)
                    }
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                }
            }
        }

        jsonQueryPresenter.setOnRestoreOriginalCallback { originalJson ->
            queryResultFormatJob?.cancel()
            if (project.isDisposed || isTabDisposed.get()) return@setOnRestoreOriginalCallback
            isApplyingQueryResult = true
            try {
                editor.setText(originalJson)
            } finally {
                isApplyingQueryResult = false
            }
            updateFilterBanner(null)
        }

        jsonQueryPresenter.setOnFilterAppliedCallback {
            queryResultFormatJob?.cancel()
            updateFilterBanner(null)
        }

        jsonQueryPresenter.setOnFocusEditorRequested {
            editor.focusContent()
        }
    }
}
