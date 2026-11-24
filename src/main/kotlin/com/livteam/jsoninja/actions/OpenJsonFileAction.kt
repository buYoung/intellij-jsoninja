package com.livteam.jsoninja.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAwareAction
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.ui.dialog.LargeFileWarningDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JSON 파일을 열어 새 탭에 로드하는 액션 클래스입니다.
 */
class OpenJsonFileAction : DumbAwareAction(
    LocalizationBundle.messagePointer("openJsonFile"),
    LocalizationBundle.messagePointer("openJsonFileDescription"),
    JsoninjaIcons.OpenJsonFileIconV3
) {
    companion object {
        private val LOG = logger<OpenJsonFileAction>()
        private const val NOTIFICATION_GROUP_ID = "JSONinja"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = JsonHelperActionUtils.getPanel(e) ?: return

        val descriptor = FileChooserDescriptor(
            true,  // files
            false, // directories
            false, // jars
            false, // jars directory
            false, // archives
            true   // multiple files
        ).withFileFilter {
            val name = it.name.lowercase()
            name.endsWith(".json") ||
                name.endsWith(".json5")
        }

        FileChooser.chooseFiles(descriptor, project, null) { virtualFiles ->
            if (project.isDisposed || virtualFiles.isEmpty()) return@chooseFiles

            val approvedFiles = virtualFiles.filter { virtualFile ->
                LargeFileWarningDialog.showWarningIfNeeded(
                    project = project,
                    fileSizeBytes = virtualFile.length,
                    fileName = virtualFile.name,
                    messageKey = "openJsonFile.large.warning.message",
                )
            }
            if (approvedFiles.isEmpty()) return@chooseFiles

            project.service<JsoninjaCoroutineScopeService>().launch {
                for (virtualFile in approvedFiles) {
                    if (project.isDisposed) return@launch
                    val loadResult = withContext(Dispatchers.IO) {
                        try {
                            val bytes = virtualFile.contentsToByteArray()
                            val content = String(bytes, virtualFile.charset)
                            Result.success(content)
                        } catch (cancellationException: CancellationException) {
                            throw cancellationException
                        } catch (cancellationException: ProcessCanceledException) {
                            throw cancellationException
                        } catch (exception: Exception) {
                            LOG.warn("Failed to read JSON file: ${virtualFile.presentableUrl}", exception)
                            Result.failure(exception)
                        }
                    }

                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed) return@withContext
                        loadResult.onSuccess { content ->
                            panel.presenter.addNewTab(
                                content = content,
                                fileExtension = virtualFile.extension,
                                title = virtualFile.name,
                                tooltip = virtualFile.presentableUrl,
                            )
                        }.onFailure { exception ->
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                                ?.createNotification(
                                    LocalizationBundle.message("openJsonFile"),
                                    LocalizationBundle.message(
                                        "openJsonFile.error.read.failed",
                                        virtualFile.name,
                                        exception.message ?: exception.javaClass.simpleName,
                                    ),
                                    NotificationType.ERROR,
                                )
                                ?.notify(project)
                        }
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(e) != null
        e.presentation.icon = JsoninjaIcons.getOpenJsonFileIcon(e.project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
