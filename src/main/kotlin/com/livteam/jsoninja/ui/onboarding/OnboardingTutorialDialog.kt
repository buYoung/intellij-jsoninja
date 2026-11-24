package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.livteam.jsoninja.LocalizationBundle
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.KeyStroke

class OnboardingTutorialDialog(
    project: Project,
    rootComponent: JComponent,
    private val onClosed: () -> Unit
) : DialogWrapper(project, true) {
    private var closed = false
    private var windowListenerAttached = false
    private val presenter = OnboardingTutorialDialogPresenter(
        project = project,
        rootComponent = rootComponent,
        tooltipParent = disposable,
        onCancelRequested = { close(CANCEL_EXIT_CODE) }
    )

    init {
        Disposer.register(disposable, presenter)
        title = LocalizationBundle.message("onboarding.tutorial.title")
        setModal(false)
        isResizable = false
        init()
        bindNavigationKeys()
        presenter.refreshStep(showTooltip = false)
        pack()
    }

    override fun createCenterPanel(): JComponent {
        return presenter.createCenterPanel()
    }

    override fun createSouthPanel(): JComponent {
        return presenter.createSouthPanel()
    }

    override fun createActions(): Array<Action> {
        return emptyArray()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return presenter.getPreferredFocusedComponent()
    }

    override fun getDimensionServiceKey(): String = "com.livteam.jsoninja.dialog.OnboardingTutorialDialog"

    fun open() {
        if (!isShowing) {
            show()
        } else {
            bringToFront()
        }
        attachWindowListenerIfNeeded()
        presenter.refreshStep(showTooltip = true)
        presenter.onDialogActivated()
    }

    fun bringToFront() {
        window?.toFront()
        window?.requestFocus()
    }

    override fun dispose() {
        if (!closed) {
            closed = true
            onClosed()
        }
        super.dispose()
    }

    private fun attachWindowListenerIfNeeded() {
        if (windowListenerAttached) return
        val dialogWindow = window ?: return
        windowListenerAttached = true
        dialogWindow.addWindowListener(object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent?) {
                presenter.onDialogActivated()
            }
        })
    }

    private fun bindNavigationKeys() {
        val pane = rootPane ?: return
        val inputMap = pane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val actionMap = pane.actionMap

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ENTER_ACTION_KEY)
        actionMap.put(ENTER_ACTION_KEY, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                presenter.onEnterPressed()
            }
        })

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), NEXT_STEP_ACTION_KEY)
        actionMap.put(NEXT_STEP_ACTION_KEY, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                presenter.onNextKeyPressed()
            }
        })

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), PREV_STEP_ACTION_KEY)
        actionMap.put(PREV_STEP_ACTION_KEY, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                presenter.onPrevKeyPressed()
            }
        })
    }

    companion object {
        private const val ENTER_ACTION_KEY = "jsoninja.onboarding.enter.to.next"
        private const val NEXT_STEP_ACTION_KEY = "jsoninja.onboarding.right.to.next"
        private const val PREV_STEP_ACTION_KEY = "jsoninja.onboarding.left.to.prev"
    }
}
