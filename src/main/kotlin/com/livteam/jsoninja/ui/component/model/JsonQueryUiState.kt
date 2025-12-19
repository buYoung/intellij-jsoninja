package com.livteam.jsoninja.ui.component.model

/**
 * JsonQuery 관련 데이터를 관리하는 UI 상태
 */
class JsonQueryUiState {
    var originalJson: String = ""

    var lastQuery: String = ""

    /**
     * 원본 JSON 데이터가 비어있는지 확인합니다
     */
    fun hasOriginalJson(): Boolean {
        return originalJson.isNotEmpty()
    }
}
