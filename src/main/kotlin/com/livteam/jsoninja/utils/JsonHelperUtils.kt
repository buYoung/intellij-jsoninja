package com.livteam.jsoninja.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.ui.component.JsonHelperPanel

/**
 * JSONinja plugin에서 자주 쓰는 유틸 함수
 */
object JsonHelperUtils {
    
    /**
     * JSONinja tool window의 활성 탭에서 현재 JSON 내용을 가져온다.
     * @param project 현재 project
     * @return 활성 탭 JSON 내용, 없으면 null
     */
    fun getCurrentJsonFromToolWindow(project: Project): String? {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("JSONinja") ?: return null
        
        if (!toolWindow.isVisible) return null
        
        val content = toolWindow.contentManager.selectedContent ?: return null
        val component = content.component as? JsonHelperPanel ?: return null
        
        return component.getCurrentEditor()?.getText()
    }

    /**
     * JSON Lines(JSONL) 형식으로 보이는지 단순 체크한다.
     * 휴리스틱:
     * 1. 비어 있지 않은 줄이 여러 개.
     * 2. 각 줄이 '{' 또는 '['로 시작.
     * 3. 전체를 하나의 JSON으로 보면 일반적인 시작 문자(예: '[')로 감싸지지 않는 경우가 많음; JSONL은 보통 개별 객체가 줄 단위로 나뉜다.
     *
     * @param text 검사할 내용
     * @return JSONL로 보이면 true
     */
    fun isJsonL(text: String): Boolean {
        if (text.isBlank()) return false

        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return false // 한 줄이면 일반 JSON일 가능성이 큼

        // 전체가 일반 JSON array 또는 object 형태인지 확인
        val trimmed = text.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // JSONL은 파일 전체를 bracket으로 감싸지 않으므로 보통 JSON array로 판단
            // 다만 사용자가 객체 리스트를 붙여넣을 수 있으므로 경계값이지만, '['로 시작하면 array일 가능성이 높다.
            return false
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            // 여러 줄로 표현된 단일 JSON object일 가능성
            return false
        }

        // 앞 몇 줄만 확인: JSONL에서는 각 줄이 유효한 JSON value여야 하므로 시작 문자만 빠르게 본다.
        return lines.take(10).all { line ->
            val t = line.trim()
            t.startsWith("{") || t.startsWith("[")
        }
    }
}
