package com.livteam.jsonhelper2.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.livteam.jsonhelper2.LocalizationBundle

@Service(Service.Level.PROJECT)
class JsonHelperProjectService(project: Project) {

    init {
        thisLogger().info(LocalizationBundle.message("projectService", project.name))
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    fun getRandomNumber() = (1..300).random()
}
