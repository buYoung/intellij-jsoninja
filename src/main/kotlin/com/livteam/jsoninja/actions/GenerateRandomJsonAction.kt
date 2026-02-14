package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.Messages
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.services.RandomJsonDataCreator
import com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationService
import com.livteam.jsoninja.services.schema.JsonSchemaGenerationException
import com.livteam.jsoninja.ui.dialog.generateJson.GenerateJsonDialog
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode

class GenerateRandomJsonAction : AnAction(
    LocalizationBundle.message("action.generate.random.json.text"),
    LocalizationBundle.message("action.generate.random.json.text"),
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = JsonHelperActionUtils.getPanel(e) ?: return

        val dialog = GenerateJsonDialog(project) { panel.presenter.getCurrentEditor()?.getText() }
        if (dialog.showAndGet()) {
            val config = dialog.getConfig()

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val generatedJson = when (config.generationMode) {
                        JsonGenerationMode.RANDOM -> {
                            val creator = RandomJsonDataCreator()
                            val prettyPrint = config.isJson5
                            creator.generateConfiguredJsonString(config, prettyPrint = prettyPrint)
                        }

                        JsonGenerationMode.SCHEMA -> {
                            val schemaDataGenerationService =
                                project.getService(JsonSchemaDataGenerationService::class.java)
                            schemaDataGenerationService.generateFromSchema(config)
                        }
                    }

                    invokeLater(ModalityState.any()) {
                        panel.presenter.setRandomJsonData(generatedJson, skipFormatting = config.isJson5)
                    }
                } catch (generationException: JsonSchemaGenerationException) {
                    val pointerSuffix = generationException.jsonPointer?.let { jsonPointer ->
                        LocalizationBundle.message("dialog.generate.json.error.pointer", jsonPointer)
                    } ?: ""
                    val errorMessage = (generationException.message
                        ?: LocalizationBundle.message("dialog.generate.json.error.generic")) + pointerSuffix

                    invokeLater(ModalityState.any()) {
                        Messages.showErrorDialog(
                            project,
                            errorMessage,
                            LocalizationBundle.message("dialog.generate.json.error.title")
                        )
                    }
                } catch (exception: Exception) {
                    invokeLater(ModalityState.any()) {
                        Messages.showErrorDialog(
                            project,
                            exception.message ?: LocalizationBundle.message("dialog.generate.json.error.generic"),
                            LocalizationBundle.message("dialog.generate.json.error.title")
                        )
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        // 활성 JSON 에디터가 있을 때만 액션을 활성화합니다.
        e.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(e) != null
        e.presentation.icon = JsoninjaIcons.getGenerateIcon(e.project)
    }
}
