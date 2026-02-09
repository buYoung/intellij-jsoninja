package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.livteam.jsoninja.LocalizationBundle
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.WindowConstants

class OnboardingTutorialDialog(
    private val project: Project,
    private val rootComponent: JComponent,
    private val onClosed: () -> Unit
) : JDialog(WindowManager.getInstance().getFrame(project) as? Window), Disposable {
    private var closed = false
    private val presenter = OnboardingTutorialDialogPresenter(
        project = project,
        rootComponent = rootComponent,
        tooltipParent = this,
        onCancelRequested = { dispose() }
    )

    init {
        title = LocalizationBundle.message("onboarding.tutorial.title")
        modalityType = Dialog.ModalityType.MODELESS
        isAlwaysOnTop = true
        isAutoRequestFocus = false
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        minimumSize = Dimension(420, 260)
        isResizable = false

        contentPane = JPanel(BorderLayout()).apply {
            add(presenter.createCenterPanel(), BorderLayout.CENTER)
            add(presenter.createSouthPanel(), BorderLayout.SOUTH)
        }
        addWindowListener(object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent?) {
                presenter.focusNextButtonIfAvailable()
            }
        })
        presenter.refreshStep(showTooltip = false)

        pack()
        setLocationRelativeTo(owner)
    }

    fun open() {
        if (!isVisible) {
            isVisible = true
        } else {
            toFront()
        }
        presenter.refreshStep(showTooltip = true)
        presenter.focusNextButtonIfAvailable()
    }

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
