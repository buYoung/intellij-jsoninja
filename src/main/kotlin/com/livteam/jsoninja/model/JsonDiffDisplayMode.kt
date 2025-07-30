package com.livteam.jsoninja.model

/**
 * JSON Diff 표시 방식
 */
enum class JsonDiffDisplayMode {
    /**
     * 에디터 탭으로 표시 (IntelliJ 기본 방식)
     */
    EDITOR_TAB,
    
    /**
     * 독립적인 창으로 표시
     */
    WINDOW
}