package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.ui.dialog.LargeFileWarningDialog
import com.livteam.jsoninja.ui.dialog.convertType.JsonToTypeDialog
import java.nio.charset.StandardCharsets

class ConvertJsonToTypeAction : AnAction(
    LocalizationBundle.message("action.json.to.type.text"),
    LocalizationBundle.message("action.json.to.type.description"),
    null,
) {
    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        val panel = JsonHelperActionUtils.getPanel(actionEvent) ?: return
        val sourceJsonText = panel.presenter.getCurrentEditor()?.getText()?.trim().orEmpty()
        if (sourceJsonText.isBlank()) {
            return
        }

        val shouldProceed = LargeFileWarningDialog.showWarningIfNeeded(
            project = project,
            fileSizeBytes = sourceJsonText.toByteArray(StandardCharsets.UTF_8).size.toLong(),
            fileName = "Current JSON",
            messageKey = "warning.large.file.convert.message",
            thresholdBytesOverride = 1024L * 1024L,
        )
        if (!shouldProceed) {
            return
        }

        JsonToTypeDialog(project, panel.presenter, sourceJsonText).show()
    }

    override fun update(actionEvent: AnActionEvent) {
        val panel = JsonHelperActionUtils.getPanel(actionEvent)
        val hasJsonText = panel?.presenter?.getCurrentEditor()?.getText()?.isNotBlank() == true
        actionEvent.presentation.isEnabledAndVisible = hasJsonText
        actionEvent.presentation.icon = JsoninjaIcons.getGenerateIcon(actionEvent.project)
    }
}
