package com.livteam.jsoninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.component.JsonHelperPanel

/**
 * JSON 파일을 열어 새 탭에 로드하는 액션 클래스입니다.
 */
class OpenJsonFileAction : AnAction(
    LocalizationBundle.message("openJsonFile"),
    LocalizationBundle.message("openJsonFileDescription"),
    AllIcons.Actions.MenuOpen
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = JsonHelperActionUtils.getPanel(e) ?: return

        val fileChooser = FileChooser.chooseFile(
            FileChooserDescriptor(
                true,  // files
                false, // directories
                false, // jars
                false, // jars directory
                false, // archives
                false  // all files
            ).withFileFilter { it.name.endsWith(".json") },
            project,
            null
        )

        fileChooser?.let { virtualFile ->
            val content = String(virtualFile.contentsToByteArray())
            ApplicationManager.getApplication().runWriteAction {
                panel.addNewTab(content)
            }
        }
    }
}
