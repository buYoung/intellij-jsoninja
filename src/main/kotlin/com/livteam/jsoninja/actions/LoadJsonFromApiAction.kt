package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.dialog.loadJson.LoadJsonFromApiDialog

class LoadJsonFromApiAction : AnAction(
    LocalizationBundle.message("action.load.json.api.text"),
    LocalizationBundle.message("action.load.json.api.description"),
    AllIcons.Actions.Download
) {
    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project ?: return
        val panel = JsonHelperActionUtils.getPanel(actionEvent) ?: return

        val loadJsonFromApiDialog = LoadJsonFromApiDialog(project) { responseJson ->
            panel.presenter.addNewTab(responseJson, "json")
        }
        loadJsonFromApiDialog.show()
    }

    override fun update(actionEvent: AnActionEvent) {
        actionEvent.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(actionEvent) != null
    }
}
