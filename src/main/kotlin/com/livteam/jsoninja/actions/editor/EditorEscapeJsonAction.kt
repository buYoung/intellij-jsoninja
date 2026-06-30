package com.livteam.jsoninja.actions.editor

import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.services.JsonFormatterService

class EditorEscapeJsonAction : BaseEditorJsonAction() {

    override val requiresJsonValidation: Boolean = false

    override fun transformJson(service: JsonFormatterService, input: String): String {
        return service.escapeJson(input)
    }

    override fun getCommandName(): String = "JSONinja Escape"

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.icon = JsoninjaIcons.getEscapeIcon(e.project)
    }
}
