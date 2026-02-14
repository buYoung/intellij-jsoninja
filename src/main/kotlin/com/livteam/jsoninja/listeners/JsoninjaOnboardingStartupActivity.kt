package com.livteam.jsoninja.listeners

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.livteam.jsoninja.services.OnboardingService

class JsoninjaOnboardingStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        invokeLater {
            if (project.isDisposed) return@invokeLater
            project.service<OnboardingService>().maybeShowWelcomeDialogOnStartup()
        }
    }
}
