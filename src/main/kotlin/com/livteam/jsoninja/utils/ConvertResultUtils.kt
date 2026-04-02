package com.livteam.jsoninja.utils

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelPresenter
import java.awt.KeyboardFocusManager
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent
import javax.swing.RootPaneContainer

object ConvertResultUtils {
    fun copyToClipboard(text: String, project: Project?) {
        runCatching {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
        }.onSuccess {
            showBalloon(project, LocalizationBundle.message("notification.convert.copied"), MessageType.INFO)
        }.onFailure {
            showBalloon(project, LocalizationBundle.message("notification.convert.copy.failed"), MessageType.ERROR)
        }
    }

    fun insertToNewTab(text: String, panel: JsoninjaPanelPresenter) {
        insertToNewTab(text, panel, null)
    }

    fun insertToNewTab(text: String, panel: JsoninjaPanelPresenter, fileExtension: String?) {
        panel.addNewTab(content = text, fileExtension = fileExtension)
        showBalloon(panel.getProject(), LocalizationBundle.message("notification.convert.inserted"), MessageType.INFO)
    }

    fun insertToEditor(
        text: String,
        project: Project,
        editor: Editor,
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            val selectionModel = editor.selectionModel
            if (selectionModel.hasSelection()) {
                editor.document.replaceString(selectionModel.selectionStart, selectionModel.selectionEnd, text)
            } else {
                editor.document.setText(text)
            }
        }
        showBalloon(project, LocalizationBundle.message("notification.convert.inserted.editor"), MessageType.INFO)
    }

    private fun showBalloon(project: Project?, message: String, messageType: MessageType) {
        val rootPane = resolveRootPane(project) ?: return
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(message, messageType, null)
            .setFadeoutTime(1800)
            .createBalloon()
            .show(RelativePoint.getNorthEastOf(rootPane), Balloon.Position.below)
    }

    private fun resolveRootPane(project: Project?): JComponent? {
        val projectFrame = project?.let { WindowManager.getInstance().getFrame(it) }
        val activeWindow = projectFrame ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        return (activeWindow as? RootPaneContainer)?.rootPane
    }
}
