package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.WasmRuntimeProbeService

class RunWasmProbeAction : AnAction(
    LocalizationBundle.message("action.run.wasm.probe.text"),
    LocalizationBundle.message("action.run.wasm.probe.description"),
    AllIcons.Actions.Execute
) {
    private val log = logger<RunWasmProbeAction>()

    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        JsonHelperActionUtils.getPanel(actionEvent) ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val probeResult = project.service<WasmRuntimeProbeService>().runProbe()

                invokeLater(ModalityState.any()) {
                    Messages.showInfoMessage(
                        project,
                        LocalizationBundle.message(
                            "dialog.wasm.probe.success.message",
                            probeResult.exportName,
                            probeResult.leftValue,
                            probeResult.rightValue,
                            probeResult.resultValue,
                            probeResult.moduleByteCount,
                        ),
                        LocalizationBundle.message("dialog.wasm.probe.success.title")
                    )
                }
            } catch (exception: Exception) {
                log.error("Wasm probe failed.", exception)
                invokeLater(ModalityState.any()) {
                    Messages.showErrorDialog(
                        project,
                        exception.message ?: LocalizationBundle.message("dialog.wasm.probe.error.generic"),
                        LocalizationBundle.message("dialog.wasm.probe.error.title")
                    )
                }
            } catch (error: Throwable) {
                log.error("Fatal Wasm probe failure.", error)
                invokeLater(ModalityState.any()) {
                    Messages.showErrorDialog(
                        project,
                        error.message ?: LocalizationBundle.message("dialog.wasm.probe.error.generic"),
                        LocalizationBundle.message("dialog.wasm.probe.error.title")
                    )
                }
            }
        }
    }

    override fun update(actionEvent: AnActionEvent) {
        actionEvent.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(actionEvent) != null
    }
}
