package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelPresenter
import javax.swing.Action
import javax.swing.JComponent

class TypeToJsonDialog(
    project: Project,
    panelPresenter: JsoninjaPanelPresenter,
) : DialogWrapper(project) {
    private val presenter = TypeToJsonDialogPresenter(project, panelPresenter)

    private val insertAction = object : DialogWrapperAction(LocalizationBundle.message("common.convert.insert")) {
        override fun doAction(actionEvent: java.awt.event.ActionEvent?) {
            presenter.insertPreviewJson()
        }
    }

    init {
        title = LocalizationBundle.message("dialog.type.to.json.title")
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
        if (presenter.insertPreviewJson()) {
            super.doOKAction()
        }
    }

    override fun dispose() {
        presenter.dispose()
        super.dispose()
    }
}
