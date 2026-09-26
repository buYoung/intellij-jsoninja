package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.ui.dialog.loadJson.LoadJsonFromApiDialog

class LoadJsonFromApiAction : AnAction(
    LocalizationBundle.messagePointer("action.load.json.api.text"),
    LocalizationBundle.messagePointer("action.load.json.api.description"),
    JsoninjaIcons.LoadJsonFromApiIconV3
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
        actionEvent.presentation.icon = JsoninjaIcons.getLoadJsonFromApiIcon(actionEvent.project)
    }
}
