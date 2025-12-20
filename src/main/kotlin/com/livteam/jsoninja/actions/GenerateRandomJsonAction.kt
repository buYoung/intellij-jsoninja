package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.services.RandomJsonDataCreator // 이 서비스가 존재하거나 수정될 것이라고 가정합니다.
import com.livteam.jsoninja.ui.dialog.generateJson.GenerateJsonDialog

class GenerateRandomJsonAction : AnAction(
    LocalizationBundle.message("action.generate.random.json.text"),
    LocalizationBundle.message("action.generate.random.json.text"),
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = JsonHelperActionUtils.getPanel(e) ?: return // 유틸리티를 사용해 활성 에디터 가져오기

        val dialog = GenerateJsonDialog(project)
        if (dialog.showAndGet()) {
            // 다이얼로그가 OK 버튼으로 닫혔을 경우
            val config = dialog.getConfig() // 다이얼로그에서 설정값 가져오기

            // TODO: config를 사용하도록 RandomJsonDataCreator 수정 필요
            // 현재는 임시로 기존 기본 생성기를 사용합니다.
            val creator = RandomJsonDataCreator() // 최종적으로는 여기에 config를 전달해야 합니다.

            ApplicationManager.getApplication().executeOnPooledThread {
                // JSON5일 경우 주석/trailing comma 생성을 위해 prettyPrint가 true여야 함
                // 일반 JSON일 경우 false로 생성해도 panel.setRandomJsonData에서 포맷팅 수행함
                val prettyPrint = config.isJson5
                val randomJson = creator.generateConfiguredJsonString(config, prettyPrint = prettyPrint)

                panel.presenter.setRandomJsonData(randomJson, skipFormatting = config.isJson5)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        // 활성 JSON 에디터가 있을 때만 액션을 활성화합니다.
        e.presentation.isEnabledAndVisible = JsonHelperActionUtils.getPanel(e) != null
        e.presentation.icon = JsoninjaIcons.getGenerateIcon(e.project)
    }
}
