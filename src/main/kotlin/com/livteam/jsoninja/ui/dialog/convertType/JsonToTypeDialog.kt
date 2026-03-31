package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelPresenter
import javax.swing.Action
import javax.swing.JComponent

class JsonToTypeDialog(
    project: Project,
    panelPresenter: JsoninjaPanelPresenter,
    sourceJsonText: String,
) : DialogWrapper(project) {
    private val presenter = JsonToTypeDialogPresenter(project, panelPresenter, sourceJsonText)

    private val insertAction = object : DialogWrapperAction(LocalizationBundle.message("common.convert.insert")) {
        override fun doAction(actionEvent: java.awt.event.ActionEvent?) {
            presenter.insertPreviewTypeDeclaration()
        }
    }

    init {
        title = LocalizationBundle.message("dialog.json.to.type.title")
        setResizable(true)
        init()
    }

    override fun createCenterPanel(): JComponent {
        return presenter.getComponent()
    }

    override fun doValidate(): ValidationInfo? {
        return presenter.validate() ?: super.doValidate()
    }

    override fun createActions(): Array<Action> {
        return arrayOf(insertAction, okAction, cancelAction)
    }

    override fun doOKAction() {
        if (presenter.insertPreviewTypeDeclaration()) {
            super.doOKAction()
        }
    }

    override fun dispose() {
        presenter.dispose()
        super.dispose()
    }
}
