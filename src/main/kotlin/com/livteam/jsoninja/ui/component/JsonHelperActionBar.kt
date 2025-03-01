package com.livteam.jsoninja.ui.component

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.IconLoader
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonFormatState
import javax.swing.Icon

/**
 * JSON Helper 플러그인의 액션 바를 정의합니다.
 * 이 컴포넌트는 툴바에 표시되는 버튼과 메뉴 항목을 관리합니다.
 */
class JsonHelperActionBar : DefaultActionGroup() {
    
    init {
        isPopup = true
        addActions()
    }

    private fun getIcon(path: String): Icon {
        return IconLoader.getIcon(path, JsonHelperActionBar::class.java)
    }

    private fun createAction(
        name: String,
        description: String,
        icon: Icon,
        actionHandler: (AnActionEvent) -> Unit = {}
    ): AnAction {
        return object : AnAction(name, description, icon) {
            override fun actionPerformed(e: AnActionEvent) {
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
            val panel = getPanel(e) ?: return@createAction
            panel.addNewTab()
        })

        add(createAction(
            LocalizationBundle.message("openJsonFile"),
            LocalizationBundle.message("openJsonFileDescription"),
            AllIcons.Actions.MenuOpen
        ) { e ->
            val project = e.project ?: return@createAction
            val panel = getPanel(e) ?: return@createAction

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
        add(createAction(
            LocalizationBundle.message("prettify"),
            LocalizationBundle.message("prettifyDescription"),
            getIcon("/icons/prettify.svg")
        ) { e ->
            val panel = getPanel(e) ?: return@createAction
            panel.formatJson(JsonFormatState.PRETTIFY)
        })
        
        add(createAction(
            LocalizationBundle.message("uglify"),
            LocalizationBundle.message("uglifyDescription"),
            getIcon("/icons/uglify.svg")
        ) { e ->
            val panel = getPanel(e) ?: return@createAction
            panel.formatJson(JsonFormatState.UGLIFY)
        })
        
        add(createAction(
            LocalizationBundle.message("escape"),
            LocalizationBundle.message("escapeDescription"),
            getIcon("/icons/escape.svg")
        ) { e ->
            val panel = getPanel(e) ?: return@createAction
            panel.escapeJson()
        })
        
        add(createAction(
            LocalizationBundle.message("unescape"),
            LocalizationBundle.message("unescapeDescription"),
            getIcon("/icons/unescape.svg")
        ) { e ->
            val panel = getPanel(e) ?: return@createAction
            panel.unescapeJson()
        })
    }
    
    /**
     * 이벤트에서 JsonHelperPanel을 가져옵니다.
     *
     * @param e 액션 이벤트
     * @return JsonHelperPanel 인스턴스
     */
    private fun getPanel(e: AnActionEvent): JsonHelperPanel? {
        return e.getData(JsonHelperPanel.DATA_KEY)
            ?: e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JsonHelperPanel
    }
}
