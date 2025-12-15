package com.livteam.jsoninja.ui.component.model

/**
 * JsonQuery 관련 데이터를 관리하는 모델
 */
class JsonQueryModel {
    var originalJson: String = ""

    // TODO 탭별로 lastQuery를 관리할 수 있도록 변경 예정
    var lastQuery: String = ""

    /**
     * 원본 JSON 데이터가 비어있는지 확인합니다
     */
    fun hasOriginalJson(): Boolean {
        return originalJson.isNotEmpty()
    }
}

