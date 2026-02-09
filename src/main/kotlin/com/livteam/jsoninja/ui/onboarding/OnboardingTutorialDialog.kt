package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.livteam.jsoninja.LocalizationBundle
import javax.swing.Action
import javax.swing.JComponent

class OnboardingTutorialDialog(
    project: Project,
    private val rootComponent: JComponent,
    private val onClosed: () -> Unit
) : DialogWrapper(project, true), Disposable {
    private var closed = false
    private val presenter = OnboardingTutorialDialogPresenter(
        project = project,
        rootComponent = rootComponent,
        tooltipParent = this,
        onCancelRequested = { close(CANCEL_EXIT_CODE) }
    )

    init {
        title = LocalizationBundle.message("onboarding.tutorial.title")
        isModal = false
        isResizable = false
        init()
        presenter.refreshStep(showTooltip = false)
    }

    fun open() {
        show()
        presenter.refreshStep(showTooltip = true)
    }

    override fun isShowing(): Boolean = window.isShowing

    override fun createCenterPanel(): JComponent = presenter.createCenterPanel()

    override fun createSouthPanel(): JComponent = presenter.createSouthPanel()

    override fun createActions(): Array<Action> = emptyArray()

    override fun dispose() {
        if (closed) {
            super.dispose()
            return
        }
        closed = true
        presenter.dispose()
        onClosed()
        super.dispose()
    }
}
