package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.RandomJsonDataCreator
import com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationService
import com.livteam.jsoninja.services.schema.JsonSchemaGenerationException
import com.livteam.jsoninja.ui.dialog.generateJson.GenerateJsonDialog
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateRandomJsonAction : AnAction(
    LocalizationBundle.message("action.generate.random.json.text"),
    LocalizationBundle.message("action.generate.random.json.text"),
    null
) {
    private val LOG = logger<GenerateRandomJsonAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = JsonHelperActionUtils.getPanel(e) ?: return

        val dialog = GenerateJsonDialog(project)
        if (dialog.showAndGet()) {
            val config = dialog.getConfig()

            project.service<JsoninjaCoroutineScopeService>().launch {
                try {
                    val generatedJson = withContext(Dispatchers.Default) {
                        when (config.generationMode) {
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
                    }

                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed) return@withContext
                        panel.presenter.setRandomJsonData(
                            generatedJson,
                            skipFormatting = shouldSkipFormatting(config)
                        )
                    }
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (generationException: JsonSchemaGenerationException) {
                    LOG.error(
                        "Schema generation failed. pointer=${generationException.jsonPointer}, message=${generationException.message}",
                        generationException
                    )
                    val pointerSuffix = generationException.jsonPointer?.let { jsonPointer ->
                        LocalizationBundle.message("dialog.generate.json.error.pointer", jsonPointer)
                    } ?: ""
                    val errorMessage = (generationException.message
                        ?: LocalizationBundle.message("dialog.generate.json.error.generic")) + pointerSuffix

                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed) return@withContext
                        Messages.showErrorDialog(
                            project,
                            errorMessage,
                            LocalizationBundle.message("dialog.generate.json.error.title")
                        )
                    }
                } catch (exception: Exception) {
                    LOG.error("Unexpected error while generating JSON.", exception)
                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed) return@withContext
                        Messages.showErrorDialog(
                            project,
                            exception.message ?: LocalizationBundle.message("dialog.generate.json.error.generic"),
                            LocalizationBundle.message("dialog.generate.json.error.title")
                        )
                    }
                } catch (error: Throwable) {
                    LOG.error("Fatal error while generating JSON.", error)
                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed) return@withContext
                        Messages.showErrorDialog(
                            project,
                            error.message ?: LocalizationBundle.message("dialog.generate.json.error.generic"),
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

    private fun shouldSkipFormatting(config: JsonGenerationConfig): Boolean {
        if (config.isJson5) {
            return true
        }

        return config.generationMode == JsonGenerationMode.SCHEMA &&
            config.schemaPropertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
    }
}
