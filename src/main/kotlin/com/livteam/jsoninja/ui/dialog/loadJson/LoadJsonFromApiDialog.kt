package com.livteam.jsoninja.ui.dialog.loadJson

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.livteam.jsoninja.LocalizationBundle
import javax.swing.Action
import javax.swing.JComponent

class LoadJsonFromApiDialog(
    project: Project,
    onJsonLoaded: (String) -> Unit
) : DialogWrapper(project) {
    private val presenter = LoadJsonFromApiDialogPresenter(
        project = project,
        onJsonLoaded = onJsonLoaded,
        onDialogCloseRequested = { close(CANCEL_EXIT_CODE) }
    )

    init {
        title = LocalizationBundle.message("dialog.load.json.api.title")
        setResizable(true)
        init()
    }

    override fun createCenterPanel(): JComponent {
        return presenter.getComponent()
    }

    override fun createActions(): Array<Action> {
        cancelAction.putValue(Action.NAME, LocalizationBundle.message("dialog.load.json.api.button.close"))
        return arrayOf(cancelAction)
    }

    override fun dispose() {
        presenter.dispose()
        super.dispose()
    }
}
