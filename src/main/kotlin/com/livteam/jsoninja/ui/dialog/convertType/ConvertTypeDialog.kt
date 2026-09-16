package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelPresenter
import com.livteam.jsoninja.utils.ConvertResultUtils
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

class ConvertTypeDialog(
    private val project: Project,
    seedText: String,
    forcedTabIndex: Int?,
    private val panelPresenter: JsoninjaPanelPresenter?,
    private val targetEditor: Editor?,
) : DialogWrapper(project) {
    private val presenter = ConvertTypeDialogPresenter(
        project = project,
        seedText = seedText,
        forcedTabIndex = forcedTabIndex,
    )
    private val copyAction = object : DialogWrapperAction(LocalizationBundle.message("common.convert.copy")) {
        override fun doAction(event: java.awt.event.ActionEvent?) {
            presenter.copyCurrentPreview()
        }
    }

    init {
        title = LocalizationBundle.message("dialog.type.conversion.title")
        setOKButtonText(LocalizationBundle.message("common.convert.insert"))
        init()
        presenter.setOnPreviewStateChanged {
            val canConsumePreview = presenter.hasCurrentPreview()
            isOKActionEnabled = canConsumePreview
            copyAction.isEnabled = canConsumePreview
        }
    }

    override fun createCenterPanel(): JComponent {
        return presenter.component.apply {
            preferredSize = Dimension(980, 700)
            minimumSize = Dimension(860, 420)
        }
    }

    override fun createLeftSideActions(): Array<Action> {
        return arrayOf(copyAction)
    }

    override fun doValidate(): ValidationInfo? {
        return presenter.validateCurrentTab() ?: if (!presenter.hasCurrentPreview()) {
            ValidationInfo(LocalizationBundle.message("common.convert.generating"), presenter.component)
        } else {
            null
        }
    }

    override fun doOKAction() {
        val isConsumed = presenter.consumeCurrentPreview { previewText, fileExtension ->
            when {
                targetEditor != null -> ConvertResultUtils.insertToEditor(previewText, project, targetEditor)
                panelPresenter != null -> ConvertResultUtils.insertToNewTab(previewText, panelPresenter, fileExtension)
                else -> ConvertResultUtils.copyToClipboard(previewText, project)
            }
        }
        if (isConsumed) super.doOKAction()
    }

    override fun dispose() {
        presenter.dispose()
        super.dispose()
    }
}
