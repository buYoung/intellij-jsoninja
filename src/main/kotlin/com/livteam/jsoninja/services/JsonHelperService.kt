package com.livteam.jsoninja.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.settings.JsoninjaSettingsState

/**
 * JSON Helper 서비스
 * JSON 포맷 상태를 저장하고 관리합니다.
 */
@Service(Service.Level.PROJECT)
class JsonHelperService(private val project: Project) {

    private val settings: JsoninjaSettingsState = JsoninjaSettingsState.getInstance(project)

    /**
     * JSON 포맷 상태 설정
     * @param state 설정할 JSON 포맷 상태
     */
    fun setJsonFormatState(state: JsonFormatState) {
        settings.jsonFormatState = state.name // Store the enum's name as a String
    }

    /**
     * 현재 JSON 포맷 상태 반환
     * @return 현재 JSON 포맷 상태
     */
    fun getJsonFormatState(): JsonFormatState {
        return JsonFormatState.fromString(settings.jsonFormatState) // Convert String back to enum
    }
}
