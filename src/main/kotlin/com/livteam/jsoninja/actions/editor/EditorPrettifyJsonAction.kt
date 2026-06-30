package com.livteam.jsoninja.actions.editor

import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService

class EditorPrettifyJsonAction : BaseEditorJsonAction() {

    override val unescapeBeforeTransform: Boolean = true

    override fun transformJson(service: JsonFormatterService, input: String): String {
        return service.formatJson(input, JsonFormatState.PRETTIFY)
    }

    override fun getCommandName(): String = "JSONinja Prettify"

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.icon = JsoninjaIcons.getPrettyIcon(e.project)
    }
}
