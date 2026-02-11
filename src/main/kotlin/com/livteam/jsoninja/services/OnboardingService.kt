package com.livteam.jsoninja.services

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.settings.JsoninjaSettingsConfigurable
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelView
import com.livteam.jsoninja.ui.onboarding.OnboardingTutorialDialog
import com.livteam.jsoninja.ui.onboarding.OnboardingWelcomeDialog
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class OnboardingService(private val project: Project) {

    private var tutorialDialog: OnboardingTutorialDialog? = null

    fun maybeShowWelcomeDialogOnStartup() {
        if (project.isDisposed || isOnboardingSeen()) return
        if (!WELCOME_DIALOG_GUARD.compareAndSet(false, true)) return

        try {
            val dialog = OnboardingWelcomeDialog(project) {
                openSettings()
            }
            dialog.show()
            markOnboardingSeen()
        } finally {
            WELCOME_DIALOG_GUARD.set(false)
        }
    }

    fun startTutorial() {
        if (project.isDisposed) return

        invokeLater(ModalityState.any()) {
            if (project.isDisposed) return@invokeLater

            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return@invokeLater
            toolWindow.show {
                invokeLater(ModalityState.any()) {
                    if (project.isDisposed) return@invokeLater
                    openTutorialDialog(toolWindow)
                }
            }
        }
    }

    fun openSettings() {
        if (project.isDisposed) return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, JsoninjaSettingsConfigurable::class.java)
    }

    private fun openTutorialDialog(toolWindow: ToolWindow) {
        val currentDialog = tutorialDialog
        if (currentDialog != null && currentDialog.isShowing) {
            currentDialog.toFront()
            return
        }

        val panelView = resolvePanelView(toolWindow) ?: return
        tutorialDialog = OnboardingTutorialDialog(project, panelView) { dontShowAgain ->
            tutorialDialog = null
            if (dontShowAgain) {
                markOnboardingSeen()
            }
        }
        tutorialDialog?.open()
    }

    private fun resolvePanelView(toolWindow: ToolWindow): JComponent? {
        val selectedComponent = toolWindow.contentManager.selectedContent?.component
        val selectedPanel = findJsoninjaPanelView(selectedComponent)
        if (selectedPanel != null) return selectedPanel

        for (index in 0 until toolWindow.contentManager.contentCount) {
            val component = toolWindow.contentManager.getContent(index)?.component
            val panel = findJsoninjaPanelView(component)
            if (panel != null) return panel
        }
        return null
    }

    private fun findJsoninjaPanelView(component: Component?): JsoninjaPanelView? {
        if (component == null) return null
        if (component is JsoninjaPanelView) return component
        if (component !is JComponent) return null

        component.components.forEach { child ->
            val found = findJsoninjaPanelView(child)
            if (found != null) return found
        }
        return null
    }

    private fun isOnboardingSeen(): Boolean {
        return PropertiesComponent.getInstance().getBoolean(ONBOARDING_SEEN_KEY, false)
    }

    private fun markOnboardingSeen() {
        PropertiesComponent.getInstance().setValue(ONBOARDING_SEEN_KEY, true)
    }

    companion object {
        private const val ONBOARDING_SEEN_KEY = "com.livteam.jsoninja.onboarding.seen"
        private const val TOOL_WINDOW_ID = "JSONinja"
        private val WELCOME_DIALOG_GUARD = AtomicBoolean(false)
    }
}
