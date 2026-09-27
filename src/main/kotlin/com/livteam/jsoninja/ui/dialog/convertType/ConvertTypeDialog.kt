package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelPresenter
import com.livteam.jsoninja.utils.ConvertResultUtils
import java.awt.Dimension
import java.awt.BorderLayout
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
    init {
        title = LocalizationBundle.message("dialog.type.conversion.title")
        setOKButtonText(LocalizationBundle.message(when {
            targetEditor != null -> "common.convert.insert"
            panelPresenter != null -> "common.convert.insert.new.tab"
            else -> "common.convert.copy.result"
        }))
        init()
        presenter.setOnPreviewStateChanged {
            isOKActionEnabled = presenter.hasCurrentPreview()
        }
    }

    override fun createCenterPanel(): JComponent {
        val destinationKey = when {
            targetEditor?.selectionModel?.hasSelection() == true -> "common.convert.destination.selection"
            targetEditor != null -> "common.convert.destination.document"
            panelPresenter != null -> "common.convert.destination.new.tab"
            else -> "common.convert.destination.clipboard"
        }
        val footer = panel {
            separator()
            row { comment(LocalizationBundle.message(destinationKey)) }
        }.apply {
            border = JBUI.Borders.empty(0, 12, 0, 12)
        }
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(presenter.component, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
            val contentMinimum = minimumSize
            minimumSize = Dimension(
                maxOf(JBUI.scale(900), contentMinimum.width),
                maxOf(JBUI.scale(460), contentMinimum.height),
            )
            preferredSize = Dimension(
                maxOf(JBUI.scale(1120), minimumSize.width),
                maxOf(JBUI.scale(680), minimumSize.height),
            )
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = presenter.getPreferredFocusedComponent()

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
