package com.livteam.jsoninja.services

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.settings.JsoninjaSettingsConfigurable
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelView
import com.livteam.jsoninja.ui.onboarding.OnboardingTutorialDialog
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class OnboardingService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) : Disposable {

    private var tutorialDialog: OnboardingTutorialDialog? = null

    fun maybeShowWelcomeDialogOnStartup() {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        if (project.isDisposed || isOnboardingSeen()) return
        if (!WELCOME_DIALOG_GUARD.compareAndSet(false, true)) return

        try {
            markOnboardingSeen()
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(
                    LocalizationBundle.message("onboarding.welcome.title"),
                    LocalizationBundle.message("onboarding.welcome.message"),
                    NotificationType.INFORMATION
                )
                .addAction(
                    NotificationAction.createSimpleExpiring(
                        LocalizationBundle.message("settings.onboarding.start.tutorial")
                    ) {
                        startTutorial()
                    }
                )
                .addAction(
                    NotificationAction.createSimpleExpiring(
                        LocalizationBundle.message("onboarding.welcome.open.settings")
                    ) {
                        openSettings()
                    }
                )
                .notify(project)
        } finally {
            WELCOME_DIALOG_GUARD.set(false)
        }
    }

    fun startTutorial() {
        if (project.isDisposed) return

        coroutineScope.launch(Dispatchers.EDT) {
            if (project.isDisposed) return@launch

            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return@launch
            toolWindow.show {
                if (project.isDisposed) return@show
                openTutorialDialog(toolWindow)
            }
        }
    }

    fun openSettings() {
        if (project.isDisposed) return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, JsoninjaSettingsConfigurable::class.java)
    }

    private fun openTutorialDialog(toolWindow: ToolWindow) {
        val currentDialog = tutorialDialog
        if (currentDialog != null && !currentDialog.isDisposed && currentDialog.isShowing) {
            currentDialog.bringToFront()
            return
        }

        val panelView = resolvePanelView(toolWindow) ?: return
        tutorialDialog = OnboardingTutorialDialog(project, panelView) {
            tutorialDialog = null
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
        val onboardingState = OnboardingStateService.getInstance()
        if (onboardingState.isWelcomeDialogSeen) return true

        if (PropertiesComponent.getInstance(project).getBoolean(ONBOARDING_SEEN_KEY, false)) {
            onboardingState.markWelcomeDialogSeen()
            return true
        }

        return false
    }

    private fun markOnboardingSeen() {
        OnboardingStateService.getInstance().markWelcomeDialogSeen()
        PropertiesComponent.getInstance(project).setValue(ONBOARDING_SEEN_KEY, true)
    }

    override fun dispose() {
        val dialog = tutorialDialog ?: return
        tutorialDialog = null
        val closeDialog = {
            if (!dialog.isDisposed) {
                dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
            }
        }
        val application = ApplicationManager.getApplication()
        if (application == null || application.isDispatchThread) {
            closeDialog()
        } else {
            application.invokeLater(closeDialog, ModalityState.any())
        }
    }

    companion object {
        private const val ONBOARDING_SEEN_KEY = "com.livteam.jsoninja.onboarding.seen"
        private const val TOOL_WINDOW_ID = "JSONinja"
        private const val NOTIFICATION_GROUP_ID = "JSONinja"
        private val WELCOME_DIALOG_GUARD = AtomicBoolean(false)
    }
}
