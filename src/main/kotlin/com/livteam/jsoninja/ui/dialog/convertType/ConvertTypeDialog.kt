package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.OptionAction
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelPresenter
import com.livteam.jsoninja.utils.ConvertResultUtils
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.util.Locale
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

class ConvertTypeDialog(
    private val project: Project,
    seedText: String,
    forcedTabIndex: Int?,
    private val panelPresenter: JsoninjaPanelPresenter?,
    private val targetEditor: Editor?,
    private val sourceFileExtension: String? = null,
) : DialogWrapper(project) {
    private enum class ConvertDestination {
        INSERT_TO_EDITOR,
        OPEN_IN_NEW_TAB,
        COPY_TO_CLIPBOARD,
    }

    private val presenter = ConvertTypeDialogPresenter(
        project = project,
        seedText = seedText,
        forcedTabIndex = forcedTabIndex,
        sourceFileExtension = sourceFileExtension,
    )
    private var primaryDestination: ConvertDestination = ConvertDestination.COPY_TO_CLIPBOARD
    private var secondaryDestinations: List<ConvertDestination> = emptyList()

    init {
        title = LocalizationBundle.message("dialog.type.conversion.title")
        init()
        presenter.setOnPreviewStateChanged {
            refreshDestinationActions()
        }
    }

    override fun createDefaultActions() {
        super.createDefaultActions()
        updateDestinations()
        myOKAction = ConvertSplitOkAction()
    }

    override fun createCenterPanel(): JComponent {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(presenter.component, BorderLayout.CENTER)
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

    override fun getDimensionServiceKey(): String = "com.livteam.jsoninja.dialog.ConvertTypeDialog"

    override fun getPreferredFocusedComponent(): JComponent = presenter.getPreferredFocusedComponent()

    override fun doValidate(): ValidationInfo? {
        return presenter.validateCurrentTab() ?: if (!presenter.hasCurrentPreview()) {
            ValidationInfo(LocalizationBundle.message("common.convert.generating"), presenter.component)
        } else {
            null
        }
    }

    override fun doOKAction() {
        performDestination(primaryDestination)
    }

    override fun dispose() {
        presenter.dispose()
        super.dispose()
    }

    private inner class ConvertSplitOkAction : OkAction(), OptionAction {
        init {
            putValue(Action.NAME, getDestinationLabel(primaryDestination))
        }

        override fun getOptions(): Array<Action> = buildSecondaryOptionActions(presenter.hasCurrentPreview())
    }

    private fun refreshDestinationActions() {
        val hasPreview = presenter.hasCurrentPreview()
        updateDestinations()
        myOKAction.putValue(Action.NAME, getDestinationLabel(primaryDestination))
        isOKActionEnabled = hasPreview
        (getButton(myOKAction) as? JBOptionButton)?.options = buildSecondaryOptionActions(hasPreview)
    }

    private fun updateDestinations() {
        val ordered = resolveDestinationOrder()
        primaryDestination = ordered.first()
        secondaryDestinations = ordered.drop(1)
    }

    private fun resolveDestinationOrder(): List<ConvertDestination> {
        val canInsertToEditor = targetEditor != null && targetEditor.document.isWritable
        val canOpenInNewTab = panelPresenter != null || ConvertResultUtils.hasToolWindow(project)
        val available = buildList {
            if (canInsertToEditor) {
                add(ConvertDestination.INSERT_TO_EDITOR)
            }
            if (canOpenInNewTab) {
                add(ConvertDestination.OPEN_IN_NEW_TAB)
            }
            add(ConvertDestination.COPY_TO_CLIPBOARD)
        }

        val outputExtension = presenter.getCurrentOutputFileExtension()
        val isCompatibleEditor = canInsertToEditor && isCompatibleFileExtension(sourceFileExtension, outputExtension)
        val preferredPrimary = when {
            isCompatibleEditor -> ConvertDestination.INSERT_TO_EDITOR
            panelPresenter != null -> ConvertDestination.OPEN_IN_NEW_TAB
            else -> ConvertDestination.COPY_TO_CLIPBOARD
        }

        return listOf(preferredPrimary) + available.filter { it != preferredPrimary }
    }

    private fun buildSecondaryOptionActions(isEnabled: Boolean): Array<Action> {
        return secondaryDestinations.map { destination ->
            object : AbstractAction(getDestinationLabel(destination)) {
                init {
                    this.isEnabled = isEnabled
                }

                override fun actionPerformed(e: ActionEvent?) {
                    performDestination(destination)
                }
            }
        }.toTypedArray()
    }

    private fun performDestination(destination: ConvertDestination) {
        val validationInfo = doValidate()
        if (validationInfo != null) {
            setErrorInfoAll(listOf(validationInfo))
            return
        }
        val isConsumed = presenter.consumeCurrentPreview { previewText, fileExtension ->
            when (destination) {
                ConvertDestination.INSERT_TO_EDITOR ->
                    targetEditor?.let { ConvertResultUtils.insertToEditor(previewText, project, it) }
                ConvertDestination.OPEN_IN_NEW_TAB -> {
                    if (panelPresenter != null) {
                        ConvertResultUtils.insertToNewTab(previewText, panelPresenter, fileExtension)
                    } else {
                        ConvertResultUtils.insertToNewTabInToolWindow(previewText, project, fileExtension)
                    }
                }
                ConvertDestination.COPY_TO_CLIPBOARD ->
                    ConvertResultUtils.copyToClipboard(previewText, project)
            }
        }
        if (isConsumed) {
            close(OK_EXIT_CODE)
        }
    }

    private fun getDestinationLabel(destination: ConvertDestination): String {
        val key = when (destination) {
            ConvertDestination.INSERT_TO_EDITOR -> "common.convert.insert"
            ConvertDestination.OPEN_IN_NEW_TAB -> "common.convert.insert.new.tab"
            ConvertDestination.COPY_TO_CLIPBOARD -> "common.convert.copy"
        }
        return LocalizationBundle.message(key)
    }

    private fun isCompatibleFileExtension(targetExt: String?, outputExt: String): Boolean {
        val normalizedTarget = targetExt?.lowercase(Locale.ROOT) ?: return false
        val normalizedOutput = outputExt.lowercase(Locale.ROOT)
        if (normalizedTarget == normalizedOutput) return true
        return when (normalizedOutput) {
            "json", "json5" -> normalizedTarget == "json" || normalizedTarget == "json5"
            "kt" -> normalizedTarget == "kts"
            "ts" -> normalizedTarget in setOf("tsx", "mts", "cts")
            "js" -> normalizedTarget in setOf("jsx", "mjs", "cjs")
            "c" -> normalizedTarget == "h"
            "cpp" -> normalizedTarget in setOf("cc", "cxx", "hpp", "hh", "hxx")
            "py" -> normalizedTarget == "pyi"
            "scala" -> normalizedTarget == "sc"
            else -> false
        }
    }
}
