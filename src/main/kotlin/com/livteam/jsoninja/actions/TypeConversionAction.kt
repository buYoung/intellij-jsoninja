package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelPresenter
import com.livteam.jsoninja.ui.dialog.LargeFileWarningDialog
import com.livteam.jsoninja.ui.dialog.convertType.ConvertTypeDialog

open class TypeConversionAction(
    actionTextKey: String = "action.type.conversion.text",
    actionDescriptionKey: String = "action.type.conversion.description",
    private val forcedTabIndex: Int? = null,
) : AnAction(
    LocalizationBundle.message(actionTextKey),
    LocalizationBundle.message(actionDescriptionKey),
    null,
) {
    private data class ConvertActionContext(
        val project: Project,
        val seedText: String,
        val panelPresenter: JsoninjaPanelPresenter?,
        val targetEditor: Editor?,
    )

    override fun actionPerformed(event: AnActionEvent) {
        val actionContext = resolveActionContext(event) ?: return
        val shouldProceed = LargeFileWarningDialog.showWarningIfNeeded(
            project = actionContext.project,
            fileSizeBytes = actionContext.seedText.toByteArray().size.toLong(),
            messageKey = "warning.large.file.convert.message",
            thresholdBytesOverride = 1024L * 1024L,
        )
        if (!shouldProceed) {
            return
        }

        ConvertTypeDialog(
            project = actionContext.project,
            seedText = actionContext.seedText,
            forcedTabIndex = forcedTabIndex,
            panelPresenter = actionContext.panelPresenter,
            targetEditor = actionContext.targetEditor,
        ).show()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = resolveActionContext(event) != null
        event.presentation.icon = JsoninjaIcons.getGenerateIcon(event.project)
    }

    private fun resolveActionContext(event: AnActionEvent): ConvertActionContext? {
        val project = event.project ?: return null
        val editor = event.getData(CommonDataKeys.EDITOR)
        val panelPresenter = JsonHelperActionUtils.getPanel(event)?.presenter

        if (editor != null) {
            val selectionModel = editor.selectionModel
            val seedText = if (selectionModel.hasSelection()) {
                selectionModel.selectedText.orEmpty()
            } else {
                editor.document.text
            }
            return ConvertActionContext(
                project = project,
                seedText = seedText,
                panelPresenter = panelPresenter,
                targetEditor = editor,
            )
        }

        val panelEditor = panelPresenter?.getCurrentEditor()
        return panelEditor?.let {
            ConvertActionContext(
                project = project,
                seedText = it.getText(),
                panelPresenter = panelPresenter,
                targetEditor = null,
            )
        }
    }
}
