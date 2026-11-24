package com.livteam.jsoninja.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ComponentUtil
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
        e.getData(JsoninjaPanelView.JSONINJA_PANEL_DATA_KEY)?.let { return it }

        val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) ?: return null
        return ComponentUtil.getParentOfType(JsoninjaPanelView::class.java, component)
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
