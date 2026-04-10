package com.livteam.jsoninja.listeners

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.livteam.jsoninja.services.OnboardingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JsoninjaOnboardingStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        withContext(Dispatchers.EDT) {
            if (project.isDisposed) return@withContext
            project.service<OnboardingService>().maybeShowWelcomeDialogOnStartup()
        }
    }
}
