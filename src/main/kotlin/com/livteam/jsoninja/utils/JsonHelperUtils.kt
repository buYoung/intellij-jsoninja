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
     * 주어진 텍스트가 JSON Lines(JSONL) 형식인지 휴리스틱하게 검사합니다.
     *
     * 이 함수는 전체 파일을 파싱하지 않고 텍스트가 JSONL일 가능성이 높은지 신속하게 판단하기 위해
     * 다음과 같은 휴리스틱(heuristics)을 사용합니다. 이는 대용량 파일의 성능에 매우 중요합니다.
     *
     * 휴리스틱 규칙:
     * 1.  **여러 개의 비어있지 않은 줄**: 유효한 JSONL 파일은 적어도 두 개 이상의 비어있지 않은 줄을
     *     포함해야 합니다. 한 줄짜리 문서는 일반 JSON 파일일 가능성이 높습니다.
     * 2.  **샘플 라인의 엄격한 JSON 파싱**: 비어있지 않은 첫 10개 라인을 샘플링하여 각 라인이
     *     엄격한(strict) JSON 객체로 파싱되는지 확인합니다. 모든 샘플 라인이 성공적으로 파싱되면
     *     JSONL로 간주합니다. 이 검사는 주석이나 후행 쉼표 같은 JSON5 기능을 허용하지 않는
     *     엄격한 `ObjectMapper`를 사용합니다.
     * 3.  **메모리 효율성**: 전체 파일을 메모리에 로드하는 것을 피하기 위해 `lineSequence`를 사용하여
     *     텍스트를 한 줄씩 처리합니다.
     *
     * 이 접근 방식은 IDE 플러그인과 같이 응답성이 중요한 환경에 최적화된 실용적인 방법입니다.
     * 모든 라인을 검사하는 엄격한 유효성 검사는 대용량 파일에서 과도한 성능 저하를 일으킬 수 있습니다.
     *
     * @param text 검사할 텍스트 내용.
     * @param jsonService JSONL 파싱을 위한 엄격한 `ObjectMapper`를 제공하는 서비스.
     * @return 텍스트가 JSONL일 가능성이 높으면 `true`, 그렇지 않으면 `false`를 반환합니다.
     */
    fun isJsonL(text: String, jsonService: JsonObjectMapperService): Boolean {
        if (text.isBlank()) return false

        // 대용량 파일 처리를 위해 메모리 효율적인 시퀀스를 사용합니다.
        val linesSequence = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 두 가지 조건을 확인하기 위해 최대 11개의 라인을 가져옵니다:
        // 1. 비어있지 않은 라인이 2개 이상인가?
        // 2. 첫 10개의 라인이 유효한 JSON인가?
        val sampleLines = linesSequence.take(11).toList()

        // 비어있지 않은 라인이 2개 미만이면 JSONL로 간주하지 않습니다.
        if (sampleLines.size < 2) return false

        // 각 라인이 유효한 표준 JSON 객체인지 확인하기 위해 엄격한 ObjectMapper를 사용합니다.
        val mapper = jsonService.jsonLObjectMapper

        // 첫 10개의 비어있지 않은 라인이 모두 유효한 JSON 객체인지 확인합니다.
        return sampleLines.take(10).all { line ->
            try {
                // 라인 파싱을 시도합니다. 실패하면 유효한 JSONL 라인이 아닙니다.
                mapper.readTree(line)
                true
            } catch (e: Exception) {
                // 파싱 실패는 잘 구성된 JSONL 파일이 아님을 의미합니다.
                false
            }
        }
    }
}
