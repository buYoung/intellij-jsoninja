package com.livteam.jsoninja.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.livteam.jsoninja.ui.component.editor.JsoninjaFoldingActionGuardService

class JsoninjaFoldingActionGuardStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().service<JsoninjaFoldingActionGuardService>()
    }
}
