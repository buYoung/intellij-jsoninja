package com.livteam.jsoninja.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.livteam.jsoninja.services.JsonObjectMapperService
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
     * 2. 앞쪽 최대 10개의 비어 있지 않은 줄이 모두 strict JSON으로 파싱될 것(JsonObjectMapperService.jsonLObjectMapper 기준).
     *    전체 텍스트가 '{...}' 로 감싸져 있어도 라인별로 파싱 가능하면 JSONL로 취급한다.
     *
     * @param text 검사할 내용
     * @param jsonService JSONL 판별 시 사용할 ObjectMapper 제공
     * @return JSONL로 보이면 true
     */
    fun isJsonL(text: String, jsonService: JsonObjectMapperService): Boolean {
        if (text.isBlank()) return false

        // 1. lineSequence()를 사용하여 전체 텍스트를 메모리에 올리지 않고 스트림 처리
        val linesSequence = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 2. 판단을 위해 최대 11줄까지만 가져옵니다.
        // (검증용 10줄 + 데이터가 2줄 이상인지 확인하기 위한 예비 1줄)
        val sampleLines = linesSequence.take(11).toList()

        // 3. 유효한 데이터 라인이 2줄 미만이면 JSONL이 아니라고 판단
        // (한 줄만 있다면 일반 JSON 파일일 가능성이 높음)
        if (sampleLines.size < 2) return false

        // 4. Service에서 정의한 'jsonLObjectMapper' 사용 (Strict Mode)
        val mapper = jsonService.jsonLObjectMapper

        // 5. 샘플 라인(최대 10줄)이 모두 유효한 JSON인지 파싱 시도
        return sampleLines.take(10).all { line ->
            try {
                // readTree로 실제 파싱을 시도하여 문법 오류 체크
                mapper.readTree(line)
                true
            } catch (e: Exception) {
                // 파싱 실패 시(문법 오류 등) JSONL 형식이 아님
                false
            }
        }
    }
}
