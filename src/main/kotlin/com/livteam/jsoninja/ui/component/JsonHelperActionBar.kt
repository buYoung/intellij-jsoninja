package com.livteam.jsoninja.ui.component

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.livteam.jsoninja.actions.*

/**
 * JSON Helper 플러그인의 액션 바를 정의합니다.
 * 이 컴포넌트는 툴바에 표시되는 버튼과 메뉴 항목을 관리합니다.
 */
class JsonHelperActionBar : DefaultActionGroup() {

    init {
        isPopup = true
        addActions()
    }

    private fun addActions() {
        // 기본 액션 추가
        add(AddTabAction())
        add(OpenJsonFileAction())

        addSeparator()

        // JSON 변환 관련 액션 추가
        add(PrettifyJsonAction(JsonHelperActionUtils.getIcon("/icons/prettify.svg")))
        add(UglifyJsonAction(JsonHelperActionUtils.getIcon("/icons/uglify.svg")))
        add(EscapeJsonAction(JsonHelperActionUtils.getIcon("/icons/escape.svg")))
        add(UnescapeJsonAction(JsonHelperActionUtils.getIcon("/icons/unescape.svg")))

        add(GenerateRandomJsonAction(JsonHelperActionUtils.getIcon("/icons/random_json.svg")))
    }
}
