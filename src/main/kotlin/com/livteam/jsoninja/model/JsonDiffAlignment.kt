package com.livteam.jsoninja.model

/**
 * JSON Diff에서 객체 키 순서를 맞추는 기준
 */
enum class JsonDiffSortMode {
    /**
     * 양쪽 객체 키를 사전순으로 정렬
     */
    KEY_ASCENDING,

    /**
     * 왼쪽 문서는 그대로 두고 오른쪽 객체 키를 왼쪽 순서에 맞춤
     */
    LEFT_ORDER
}

/**
 * 정렬 한 번에 적용할 불변 옵션
 *
 * @param shouldIgnoreArrayOrder true면 오른쪽 배열 요소를 내용이 같은 왼쪽 요소 순서로 재배치
 */
data class JsonDiffAlignmentOptions(
    val sortMode: JsonDiffSortMode = JsonDiffSortMode.KEY_ASCENDING,
    val shouldIgnoreArrayOrder: Boolean = false
)

enum class JsonDiffAlignmentStatus {
    /**
     * 한쪽 이상 텍스트가 바뀜
     */
    APPLIED,

    /**
     * 정렬 결과가 입력과 같음
     */
    NO_CHANGE,

    /**
     * 필요한 입력이 유효하지 않아 원래 두 텍스트를 그대로 반환함
     */
    INVALID_INPUT
}

data class JsonDiffAlignmentResult(
    val leftText: String,
    val rightText: String,
    val status: JsonDiffAlignmentStatus
)
