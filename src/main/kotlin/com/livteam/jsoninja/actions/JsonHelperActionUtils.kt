package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.util.IconLoader
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelView

/**
 * JSON Helper 액션에 필요한 유틸리티 함수들을 제공하는 객체입니다.
 */
object JsonHelperActionUtils {

    /**
     * 이벤트에서 JsonHelperPanel을 가져옵니다.
     *
     * @param e 액션 이벤트
     * @return JsonHelperPanel 인스턴스 또는 null
     */
    fun getPanel(e: AnActionEvent): JsoninjaPanelView? {
        // PlatformDataKeys.EDITOR는 Editor 타입을 반환하기 때문에 JsonHelperPanel로 변환하는 것은 적합하지 않음
        // 직접 정의한 JsonHelperPanel을 찾기 위해 컨텍스트 컴포넌트를 확인
        val component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)

        // 컨텍스트 컴포넌트가 JsonHelperPanel인지 확인
        return if (component is JsoninjaPanelView) component else null
    }

    /**
     * 아이콘 리소스 경로로부터 아이콘을 로드합니다.
     *
     * @param path 아이콘 경로
     * @return 로드된 아이콘
     */
    fun getIcon(path: String): javax.swing.Icon {
        return IconLoader.getIcon(path, JsonHelperActionUtils::class.java)
    }
}
