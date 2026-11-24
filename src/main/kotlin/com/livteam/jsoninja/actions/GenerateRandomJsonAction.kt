package com.livteam.jsoninja.actions

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.RandomJsonDataCreator
import com.livteam.jsoninja.services.random.RandomJsonGenerationSessionService
import com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationService
import com.livteam.jsoninja.services.schema.JsonSchemaGenerationException
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelView
import com.livteam.jsoninja.ui.dialog.generateJson.GenerateJsonDialog
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class GenerateRandomJsonAction : DumbAwareAction(
    LocalizationBundle.messagePointer("action.generate.random.json.text"),
    LocalizationBundle.messagePointer("action.generate.random.json.text"),
    null as javax.swing.Icon?
) {
    private val LOG = logger<GenerateRandomJsonAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = JsonHelperActionUtils.getPanel(e) ?: return
        openGenerateDialog(project, panel)
    }

    private fun openGenerateDialog(project: Project, panel: JsoninjaPanelView) {
        val dialog = GenerateJsonDialog(project)
        if (!dialog.showAndGet()) return

        val selectedConfig = dialog.getConfig()
        val jsonFormatState = panel.presenter.getJsonFormatState()
        val target = panel.presenter.captureGenerationTarget() ?: return
        val config = if (selectedConfig.generationMode == JsonGenerationMode.RANDOM) {
            project.service<RandomJsonGenerationSessionService>().prepareGenerationConfig(selectedConfig)
        } else {
            selectedConfig
        }

        project.service<JsoninjaCoroutineScopeService>().launch {
            try {
                val generatedJson = withBackgroundProgress(
                    project = project,
                    title = LocalizationBundle.message("progress.generating.json"),
                    cancellable = true,
                ) {
                    withContext(Dispatchers.Default) {
                        val rawGeneratedJson = when (config.generationMode) {
                            JsonGenerationMode.RANDOM -> {
                                val creator = RandomJsonDataCreator()
                                val prettyPrint = config.isJson5
                                val generationContext = currentCoroutineContext()
                                creator.generateConfiguredJsonString(config, prettyPrint = prettyPrint) {
                                    generationContext.ensureActive()
                                }
                            }

                            JsonGenerationMode.SCHEMA -> {
                                val schemaDataGenerationService =
                                    project.getService(JsonSchemaDataGenerationService::class.java)
                                val generationContext = currentCoroutineContext()
                                schemaDataGenerationService.generateFromSchema(config) { generationContext.ensureActive() }
                            }
                        }

                        if (shouldSkipFormatting(config)) {
                            rawGeneratedJson
                        } else {
                            project.service<JsonFormatterService>().formatJson(
                                rawGeneratedJson,
                                jsonFormatState
                            )
                        }
                    }
                }

                withContext(Dispatchers.EDT) {
                    if (project.isDisposed || !panel.presenter.isGenerationTargetCurrent(target)) return@withContext
                    val isApplied = panel.presenter.setRandomJsonData(
                        generatedJson,
                        target,
                        skipFormatting = true
                    )
                    if (isApplied && config.generationMode == JsonGenerationMode.RANDOM) {
                        project.service<RandomJsonGenerationSessionService>().remember(config)
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (cancellationException: ProcessCanceledException) {
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
                    showErrorNotification(
                        project = project,
                        panel = panel,
                        errorMessage = errorMessage,
                        allowReopenSchema = config.generationMode == JsonGenerationMode.SCHEMA,
                    )
                }
            } catch (exception: Exception) {
                LOG.error("Unexpected error while generating JSON.", exception)
                withContext(Dispatchers.EDT) {
                    if (project.isDisposed) return@withContext
                    showErrorNotification(
                        project = project,
                        panel = panel,
                        errorMessage = exception.message ?: LocalizationBundle.message("dialog.generate.json.error.generic"),
                        allowReopenSchema = config.generationMode == JsonGenerationMode.SCHEMA,
                    )
                }
            }
        }
    }

    private fun showErrorNotification(
        project: Project,
        panel: JsoninjaPanelView,
        errorMessage: String,
        allowReopenSchema: Boolean,
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                LocalizationBundle.message("dialog.generate.json.error.title"),
                errorMessage,
                NotificationType.ERROR,
            )
        if (allowReopenSchema) {
            notification.addAction(
                NotificationAction.createSimpleExpiring(LocalizationBundle.message("dialog.generate.json.error.reopen")) {
                    if (!project.isDisposed) {
                        openGenerateDialog(project, panel)
                    }
                }
            )
        }
        notification.notify(project)
    }

    companion object {
        private const val NOTIFICATION_GROUP_ID = "JSONinja"
    }

    override fun update(e: AnActionEvent) {
        // 활성 JSON 에디터가 있을 때만 액션을 활성화합니다.
        e.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(e) != null
        e.presentation.icon = JsoninjaIcons.getGenerateIcon(e.project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun shouldSkipFormatting(config: JsonGenerationConfig): Boolean {
        if (config.isJson5) {
            return true
        }

        return config.generationMode == JsonGenerationMode.SCHEMA &&
            config.schemaPropertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
    }
}
