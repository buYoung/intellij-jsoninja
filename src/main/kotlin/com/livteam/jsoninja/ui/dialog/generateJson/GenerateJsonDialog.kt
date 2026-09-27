package com.livteam.jsoninja.ui.dialog.generateJson

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingUtilities

class GenerateJsonDialog(
    project: Project
) : DialogWrapper(project) {

    private val presenter = GenerateJsonDialogPresenter(project, ::scheduleLayoutUpdate, ::scheduleAdvancedOptionsLayoutUpdate)
    private val tabSizes = mutableMapOf<JsonGenerationMode, Dimension>()
    private var currentGenerationMode = JsonGenerationMode.RANDOM
    private var isLayoutUpdateScheduled = false
    private var shouldRestoreTabSize = false
    private var shouldPackHeight = false

    init {
        title = LocalizationBundle.message("dialog.generate.json.title")
        setOKButtonText(LocalizationBundle.message("button.generate"))
        init()
        presenter.registerValidators(disposable)
        updateMinimumSize()
    }

    override fun createCenterPanel(): JComponent {
        return presenter.getComponent()
    }

    override fun doValidate(): ValidationInfo? {
        return presenter.validate() ?: super.doValidate()
    }

    override fun getPreferredFocusedComponent(): JComponent = presenter.getPreferredFocusedComponent()

    fun getConfig(): JsonGenerationConfig {
        return presenter.getConfig()
    }

    override fun dispose() {
        presenter.dispose()
        super.dispose()
    }

    private fun scheduleAdvancedOptionsLayoutUpdate() {
        shouldPackHeight = true
        scheduleLayoutUpdate()
    }

    private fun scheduleLayoutUpdate() {
        val generationMode = presenter.getGenerationMode()
        if (generationMode != currentGenerationMode) {
            // 다음 탭의 최소 크기로 창이 자동 확대되기 전에 이전 탭의 크기를 저장한다.
            window?.takeIf { it.isShowing }?.let { tabSizes[currentGenerationMode] = it.size }
            currentGenerationMode = generationMode
            shouldRestoreTabSize = true
        }
        if (isLayoutUpdateScheduled) return
        isLayoutUpdateScheduled = true
        // 조건부 행과 탭의 레이아웃 변경이 반영된 뒤 창 크기를 계산한다.
        SwingUtilities.invokeLater {
            isLayoutUpdateScheduled = false
            if (isDisposed) return@invokeLater
            val dialogWindow = window ?: return@invokeLater
            if (!dialogWindow.isShowing) return@invokeLater

            updateMinimumSize()
            val targetSize = if (shouldRestoreTabSize) {
                tabSizes[currentGenerationMode] ?: dialogWindow.preferredSize
            } else if (shouldPackHeight) {
                Dimension(dialogWindow.width, dialogWindow.preferredSize.height)
            } else {
                dialogWindow.size
            }
            shouldRestoreTabSize = false
            shouldPackHeight = false
            val requiredSize = dialogWindow.minimumSize
            dialogWindow.size = Dimension(
                maxOf(targetSize.width, requiredSize.width),
                maxOf(targetSize.height, requiredSize.height)
            )
        }
    }

    private fun updateMinimumSize() {
        val dialogWindow = window ?: return
        val contentSize = rootPane.minimumSize
        val insets = dialogWindow.insets
        dialogWindow.minimumSize = Dimension(
            contentSize.width + insets.left + insets.right,
            contentSize.height + insets.top + insets.bottom
        )
    }
}
