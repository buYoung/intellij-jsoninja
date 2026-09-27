package com.livteam.jsoninja.ui.dialog.loadJson

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingUtilities

class LoadJsonFromApiDialog(
    project: Project,
    onJsonLoaded: (String) -> Unit
) : DialogWrapper(project) {
    private val presenter = LoadJsonFromApiDialogPresenter(
        project = project,
        onJsonLoaded = onJsonLoaded,
        onDialogCloseRequested = { close(OK_EXIT_CODE) },
        onLoadingChanged = { isLoading ->
            isOKActionEnabled = !isLoading
            setOKButtonText(LocalizationBundle.message(
                if (isLoading) "dialog.load.json.api.loading" else "dialog.load.json.api.send"
            ))
        },
        onValidationChanged = ::showValidation,
        onLayoutChanged = ::scheduleLayoutUpdate
    )
    private var isLayoutUpdateScheduled = false

    init {
        title = LocalizationBundle.message("dialog.load.json.api.title")
        setOKButtonText(LocalizationBundle.message("dialog.load.json.api.send"))
        setCancelButtonText(LocalizationBundle.message("dialog.load.json.api.button.close"))
        setResizable(true)
        init()
        updateMinimumSize()
    }

    override fun createCenterPanel(): JComponent {
        return presenter.getComponent()
    }

    override fun getPreferredFocusedComponent(): JComponent = presenter.getPreferredFocusedComponent()

    override fun doOKAction() {
        presenter.handleSendRequested()
    }

    override fun dispose() {
        presenter.dispose()
        super.dispose()
    }

    private fun showValidation(validationInfo: ValidationInfo?) {
        setErrorInfoAll(listOfNotNull(validationInfo))
        validationInfo?.component?.let { component ->
            SwingUtilities.invokeLater {
                if (!isDisposed && component.isShowing) component.requestFocusInWindow()
            }
        }
    }

    private fun scheduleLayoutUpdate() {
        if (isLayoutUpdateScheduled) return
        isLayoutUpdateScheduled = true
        // 조건부 행의 배치가 반영된 뒤 현재 폭을 유지하며 높이만 조정한다.
        SwingUtilities.invokeLater {
            isLayoutUpdateScheduled = false
            if (isDisposed) return@invokeLater
            val dialogWindow = window?.takeIf { it.isShowing } ?: return@invokeLater
            updateMinimumSize()
            dialogWindow.size = Dimension(
                maxOf(dialogWindow.width, dialogWindow.minimumSize.width),
                maxOf(dialogWindow.preferredSize.height, dialogWindow.minimumSize.height)
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
