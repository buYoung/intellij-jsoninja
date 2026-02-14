package com.livteam.jsoninja.ui.component.jsonQuery

import com.intellij.ui.SearchTextField
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.ui.onboarding.OnboardingTutorialTargetIds
import java.awt.event.KeyAdapter
import javax.swing.JComponent

/**
 * JMESPath 검색을 위한 View 컴포넌트
 * UI 구성 요소와 사용자 입력 처리를 담당
 */
class JsonQueryView {
    private val jmesPathField = SearchTextField()

    init {
        jmesPathField.name = OnboardingTutorialTargetIds.QUERY_FIELD
        jmesPathField.textEditor.emptyText.text = LocalizationBundle.message("jmesPathPlaceholder")
    }

    val component: JComponent
        get() = jmesPathField

    var query: String
        get() = jmesPathField.text.trim()
        set(value) {
            jmesPathField.text = value
        }

    fun addKeyListener(listener: KeyAdapter) {
        jmesPathField.textEditor.addKeyListener(listener)
    }
}
