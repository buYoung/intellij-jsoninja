package com.livteam.jsoninja.ui.diff

import com.intellij.diff.DiffManager
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.livteam.jsoninja.LocalizationBundle
import java.awt.Window
import javax.swing.Action
import javax.swing.JComponent

class JsonDiffWindowDialog(
    project: Project,
    diffRequest: DiffRequest,
    private val onClosed: () -> Unit
) : DialogWrapper(project) {

    private val panelDisposable = Disposer.newDisposable()
    private val diffRequestPanel = DiffManager.getInstance().createRequestPanel(project, panelDisposable, null as Window?)
    private var disposedAlready = false

    init {
        title = LocalizationBundle.message("dialog.json.diff.title")
        setModal(false)
        setResizable(true)
        diffRequestPanel.setRequest(diffRequest)
        init()
    }

    override fun createCenterPanel(): JComponent {
        return diffRequestPanel.component
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return diffRequestPanel.preferredFocusedComponent
    }

    override fun createActions(): Array<Action> {
        return emptyArray()
    }

    fun showOrFocus() {
        if (!isOpen()) {
            show()
            return
        }

        window?.toFront()
        window?.requestFocus()
    }

    fun isOpen(): Boolean {
        return window?.isShowing == true
    }

    override fun dispose() {
        if (!disposedAlready) {
            disposedAlready = true
            Disposer.dispose(panelDisposable)
            onClosed()
        }
        super.dispose()
    }
}
