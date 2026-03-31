package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialog

class ConvertTypeToJsonAction : AnAction(
    LocalizationBundle.message("action.type.to.json.text"),
    LocalizationBundle.message("action.type.to.json.description"),
    null,
) {
    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        val panel = JsonHelperActionUtils.getPanel(actionEvent) ?: return

        TypeToJsonDialog(project, panel.presenter).show()
    }

    override fun update(actionEvent: AnActionEvent) {
        actionEvent.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(actionEvent) != null
        actionEvent.presentation.icon = JsoninjaIcons.getGenerateIcon(actionEvent.project)
    }
}
