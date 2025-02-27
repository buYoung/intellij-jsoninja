package com.livteam.jsonhelper2.model

/**
 * JSON 포맷 상태를 나타내는 열거형
 * PRETTIFY: 들여쓰기와 줄바꿈이 적용된 가독성 좋은 JSON 형식
 * UGLIFY: 들여쓰기와 줄바꿈이 없는 압축된 JSON 형식
 * PRETTIFY_SORTED: 키를 알파벳 순서로 정렬하고 들여쓰기와 줄바꿈이 적용된 JSON 형식
 * PRETTIFY_COMPACT: 들여쓰기는 적용되지만 배열 요소는 한 줄에 표시되는 JSON 형식
 */
enum class JsonFormatState {
    PRETTIFY,
    UGLIFY,
    PRETTIFY_SORTED,
    PRETTIFY_COMPACT;
    
    companion object {
        /**
         * 기본 포맷 상태
         */
        val DEFAULT = PRETTIFY
        
        /**
         * 문자열로부터 JsonFormatState 값 가져오기
         * @param value 문자열 값
         * @return 일치하는 JsonFormatState, 없으면 PRETTIFY
         */
        fun fromString(value: String): JsonFormatState {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                PRETTIFY
            }
        }
    }
    
    /**
     * 이 포맷 상태가 들여쓰기를 사용하는지 여부
     * @return 들여쓰기를 사용하면 true, 아니면 false
     */
    fun usesPrettyPrinting(): Boolean {
        return this != UGLIFY
    }
    
    /**
     * 이 포맷 상태가 정렬을 사용하는지 여부
     * @return 정렬을 사용하면 true, 아니면 false
     */
    fun usesSorting(): Boolean {
        return this == PRETTIFY_SORTED
    }
    
    /**
     * 이 포맷 상태가 압축 배열을 사용하는지 여부
     * @return 압축 배열을 사용하면 true, 아니면 false
     */
    fun usesCompactArrays(): Boolean {
        return this == PRETTIFY_COMPACT
    }
}
