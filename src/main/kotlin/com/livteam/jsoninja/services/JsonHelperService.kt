package com.livteam.jsoninja.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.JsonFormatState

/**
 * JSON Helper 서비스
 * JSON 포맷 상태를 저장하고 관리합니다.
 */
@Service(Service.Level.PROJECT)
class JsonHelperService(private val project: Project) {
    // JSON 포맷 상태
    private var jsonFormatState = JsonFormatState.PRETTIFY

    /**
     * JSON 포맷 상태 설정
     * @param state 설정할 JSON 포맷 상태
     */
    fun setJsonFormatState(state: JsonFormatState) {
        jsonFormatState = state
    }

    /**
     * 현재 JSON 포맷 상태 반환
     * @return 현재 JSON 포맷 상태
     */
    fun getJsonFormatState(): JsonFormatState {
        return jsonFormatState
    }
}
