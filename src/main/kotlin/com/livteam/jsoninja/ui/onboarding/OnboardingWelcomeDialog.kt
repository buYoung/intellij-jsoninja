package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class OnboardingWelcomeDialog(
    project: Project,
    private val onOpenSettings: () -> Unit
) : DialogWrapper(project) {

    init {
        title = LocalizationBundle.message("onboarding.welcome.title")
        setModal(false)
        setResizable(false)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val messageLabel = JBLabel(
            "<html>${LocalizationBundle.message("onboarding.welcome.message")}</html>"
        ).apply {
            border = JBUI.Borders.empty(8, 0)
        }

        return JPanel().apply {
            border = JBUI.Borders.empty(8, 8)
            add(messageLabel)
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            OpenSettingsAction(),
            cancelAction
        )
    }

    private inner class OpenSettingsAction : DialogWrapperAction(
        LocalizationBundle.message("onboarding.welcome.open.settings")
    ) {
        override fun doAction(e: ActionEvent?) {
            close(OK_EXIT_CODE)
            onOpenSettings()
        }
    }
}
