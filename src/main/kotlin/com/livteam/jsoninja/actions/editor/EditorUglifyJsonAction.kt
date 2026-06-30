package com.livteam.jsoninja.actions.editor

import com.intellij.openapi.actionSystem.AnActionEvent
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService

class EditorUglifyJsonAction : BaseEditorJsonAction() {

    override val unescapeBeforeTransform: Boolean = true

    override fun transformJson(service: JsonFormatterService, input: String): String {
        return service.formatJson(input, JsonFormatState.UGLIFY)
    }

    override fun getCommandName(): String = "JSONinja Minify"

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.icon = JsoninjaIcons.getUglifyIcon(e.project)
    }
}
