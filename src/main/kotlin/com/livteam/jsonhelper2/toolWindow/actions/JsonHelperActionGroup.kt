package com.livteam.jsonhelper2.toolWindow.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.IconLoader
import com.livteam.jsonhelper2.LocalizationBundle
import com.livteam.jsonhelper2.toolWindow.component.JsonHelperPanel
import javax.swing.Icon

class JsonHelperActionGroup : DefaultActionGroup() {
    init {
        isPopup = true
        addActions()
    }

    private fun getIcon(path: String): Icon {
        return IconLoader.getIcon(path, JsonHelperActionGroup::class.java)
    }

    private fun createAction(
        name: String,
        description: String,
        icon: Icon,
        actionHandler: (AnActionEvent) -> Unit = {}
    ): AnAction {
        return object : AnAction(name, description, icon) {
            override fun actionPerformed(e: AnActionEvent) {
                println("$name 액션이 클릭되었습니다.")
                actionHandler(e)
            }
        }
    }

    private fun addActions() {
        // 기본 액션 추가
        add(createAction(
            LocalizationBundle.message("addTab"),
            LocalizationBundle.message("addTabDescription"),
            AllIcons.General.Add
        ) { e ->
            // 두 가지 방식으로 JsonHelperPanel을 찾아봅니다
            val panel = e.getData(JsonHelperPanel.DATA_KEY)
                ?: e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JsonHelperPanel
            
            panel?.addNewTab()
        })

        add(createAction(
            LocalizationBundle.message("openJsonFile"),
            LocalizationBundle.message("openJsonFileDescription"),
            AllIcons.Actions.MenuOpen
        ) { e ->
            val project = e.project ?: return@createAction
            val panel = e.getData(JsonHelperPanel.DATA_KEY)
                ?: e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JsonHelperPanel
                ?: return@createAction

            val fileChooser = com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                com.intellij.openapi.fileChooser.FileChooserDescriptor(
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
        })

        addSeparator()

        // JSON 변환 관련 액션 추가
        val jsonActions = listOf(
            Triple(
                LocalizationBundle.message("prettify"),
                LocalizationBundle.message("prettifyDescription"),
                "/icons/prettify.svg"
            ),
            Triple(
                LocalizationBundle.message("uglify"),
                LocalizationBundle.message("uglifyDescription"),
                "/icons/uglify.svg"
            ),
            Triple(
                LocalizationBundle.message("escape"),
                LocalizationBundle.message("escapeDescription"),
                "/icons/escape.svg"
            ),
            Triple(
                LocalizationBundle.message("unescape"),
                LocalizationBundle.message("unescapeDescription"),
                "/icons/unescape.svg"
            )
        )

        jsonActions.forEach { (name, description, iconPath) ->
            add(createAction(name, description, getIcon(iconPath)))
        }
    }
}
