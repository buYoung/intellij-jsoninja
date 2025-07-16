package com.livteam.jsoninja.ui.panel

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Alarm
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.ui.dialog.component.*
import java.awt.BorderLayout
import javax.swing.*

/**
 * Panel for JSON diff functionality that can be used in both dialogs and tool windows
 */
class JsonDiffPanel(
    private val project: Project,
    private val diffService: JsonDiffService,
    private val currentJson: String? = null,
    parentDisposable: Disposable
) : JPanel(BorderLayout()), Disposable {

    companion object {
        private const val SPLIT_DIVIDER_LOCATION = 400
        private const val SPLIT_RESIZE_WEIGHT = 0.4
        private const val DEBOUNCE_DELAY = 500 // 500ms delay for debouncing
    }

    private lateinit var editorPanel: JsonDiffEditorPanel
    private lateinit var optionsPanel: JsonDiffOptionsPanel
    private lateinit var diffViewerPanel: JsonDiffViewerPanel
    private lateinit var actionHandler: DefaultEditorActionHandler
    
    // Debouncing alarm for performance optimization
    private val updateAlarm: Alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    
    // Current diff task for cancellation
    @Volatile
    private var currentDiffTask: Task.Backgroundable? = null

    init {
        Disposer.register(parentDisposable, this)
        initializeUI()
    }

    private fun initializeUI() {
        // Initialize components
        initializeComponents()

        // Create editors container
        val editorsContainer = JPanel(BorderLayout())
        editorsContainer.add(editorPanel, BorderLayout.CENTER)
        editorsContainer.add(optionsPanel, BorderLayout.SOUTH)

        // Create split pane
        val splitPane = createSplitPane(editorsContainer)
        add(splitPane, BorderLayout.CENTER)

        // Setup listeners
        setupListeners()

        // Initial diff if both have content
        if (editorPanel.getLeftContent().isNotBlank() && editorPanel.getRightContent().isNotBlank()) {
            updateDiff()
        }
    }

    private fun initializeComponents() {
        // Initialize editor panel
        editorPanel = JsonDiffEditorPanel(
            project,
            initialLeftContent = currentJson
        )

        // Initialize action handler with editorPanel reference
        actionHandler = DefaultEditorActionHandler(project, editorPanel)
        editorPanel.addActionHandler(actionHandler)

        // Initialize options panel
        optionsPanel = JsonDiffOptionsPanel()

        // Initialize diff viewer panel
        diffViewerPanel = JsonDiffViewerPanel(project, this)
    }

    private fun createSplitPane(editorsContainer: JPanel): JSplitPane {
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, true)
        splitPane.topComponent = editorsContainer
        splitPane.bottomComponent = diffViewerPanel
        splitPane.dividerLocation = SPLIT_DIVIDER_LOCATION
        splitPane.resizeWeight = SPLIT_RESIZE_WEIGHT
        return splitPane
    }

    private fun setupListeners() {
        // Editor change listeners with debouncing
        editorPanel.setLeftEditorChangeCallback { content ->
            scheduleDiffUpdate()
        }

        editorPanel.setRightEditorChangeCallback { content ->
            scheduleDiffUpdate()
        }

        // Options change listener - immediate update for options
        optionsPanel.addOptionsChangeListener {
            updateDiff()
        }
    }
    
    /**
     * Schedule a debounced diff update to improve performance
     * This prevents excessive diff calculations during rapid typing
     */
    private fun scheduleDiffUpdate() {
        // Cancel any pending update
        updateAlarm.cancelAllRequests()
        
        // Schedule new update after delay
        updateAlarm.addRequest({
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    updateDiff()
                }
            }
        }, DEBOUNCE_DELAY)
    }

    private fun updateDiff() {
        val leftJson = editorPanel.getLeftContent()
        val rightJson = editorPanel.getRightContent()
        updateDiffWithContent(leftJson, rightJson)
    }
    
    private fun updateDiffWithContent(leftJson: String, rightJson: String) {
        val leftContent = leftJson.trim()
        val rightContent = rightJson.trim()

        if (leftContent.isEmpty() || rightContent.isEmpty()) {
            diffViewerPanel.clear()
            return
        }

        // Cancel any ongoing diff task before starting a new one
        currentDiffTask?.let { task ->
            ProgressManager.getInstance().getProgressIndicator()?.cancel()
        }
        
        // Create new diff task
        val diffTask = object : Task.Backgroundable(
            project,
            LocalizationBundle.message("progress.calculating.diff"),
            true // canBeCancelled
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                
                try {
                    // Check cancellation before processing
                    if (indicator.isCanceled) return
                    
                    // Create diff request in background
                    val request = diffService.createDiffRequest(
                        leftContent,
                        rightContent,
                        semantic = optionsPanel.isSemanticComparisonEnabled()
                    )
                    
                    // Check cancellation before updating UI
                    if (indicator.isCanceled) return
                    
                    // Update UI on EDT
                    ApplicationManager.getApplication().invokeLater {
                        if (!indicator.isCanceled && currentDiffTask === this) {
                            diffViewerPanel.showDiff(request)
                        }
                    }
                } catch (e: Exception) {
                    // Check cancellation before showing error
                    if (indicator.isCanceled) return
                    
                    // Show error on EDT
                    ApplicationManager.getApplication().invokeLater {
                        if (!indicator.isCanceled && currentDiffTask === this) {
                            diffViewerPanel.showError("Error: ${e.message}")
                        }
                    }
                }
            }
            
            override fun onFinished() {
                // Clear the reference when task is finished
                if (currentDiffTask === this) {
                    currentDiffTask = null
                }
            }
        }
        
        // Store the current task reference
        currentDiffTask = diffTask
        
        // Run the task
        ProgressManager.getInstance().run(diffTask)
    }
    
    /**
     * 현재 탭 또는 전체 창을 닫습니다.
     * - 탭이 여러 개 있으면: 현재 탭만 닫기
     * - 탭이 하나만 있으면: 전체 Tool Window 숨기기
     * Action 시스템에서 호출하기 위한 공개 메서드입니다.
     */
    fun closeCurrentTabOrWindow() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JSONinja-Diff")
        toolWindow?.let { tw ->
            val contentManager = tw.contentManager
            val currentContent = contentManager.contents.find { it.component === this@JsonDiffPanel }
            
            if (contentManager.contentCount > 1) {
                // Multiple tabs exist - close only the current tab
                currentContent?.let { contentManager.removeContent(it, true) }
            } else {
                // Only one tab remains - close the entire window
                tw.hide()
            }
        }
    }

    override fun dispose() {
        // Cancel any pending updates
        updateAlarm.cancelAllRequests()
        updateAlarm.dispose()
        
        // Cancel any ongoing diff task
        currentDiffTask?.let { task ->
            ProgressManager.getInstance().getProgressIndicator()?.cancel()
        }
        currentDiffTask = null
    }
}