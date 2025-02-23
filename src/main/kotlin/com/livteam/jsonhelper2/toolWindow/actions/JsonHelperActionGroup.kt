package com.livteam.jsonhelper2.toolWindow.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.util.IconLoader
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
            "Add Tab",
            "새로운 탭 추가",
            AllIcons.General.Add
        ) { e ->
            // 두 가지 방식으로 JsonHelperPanel을 찾아봅니다
            val panel = e.getData(JsonHelperPanel.DATA_KEY)
                ?: e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JsonHelperPanel
            
            panel?.addNewTab()
        })

        add(createAction(
            "Open Json file",
            "JSON 파일 열기",
            AllIcons.Actions.MenuOpen
        ))

        addSeparator()

        // JSON 변환 관련 액션 추가
        val jsonActions = listOf(
            Triple(
                "Prettify",
                "JSON 문자열을 들여쓰기와 줄바꿈을 포함한 가독성 좋은 형태로 변환",
                "/icons/prettify.svg"
            ),
            Triple(
                "Uglify",
                "JSON 문자열을 한 줄로 변환",
                "/icons/uglify.svg"
            ),
            Triple(
                "Escape",
                "JSON 문자열을 이스케이프 처리",
                "/icons/escape.svg"
            ),
            Triple(
                "Unescape",
                "이스케이프된 JSON 문자열을 원래 형태로 변환",
                "/icons/unescape.svg"
            )
        )

        jsonActions.forEach { (name, description, iconPath) ->
            add(createAction(name, description, getIcon(iconPath)))
        }
    }
}
