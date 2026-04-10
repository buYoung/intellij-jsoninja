package com.livteam.jsoninja.ui.component.jsonQuery

import com.intellij.icons.AllIcons
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.SearchTextField
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.ui.onboarding.OnboardingTutorialTargetIds
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.net.URI
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * JSON Query 검색을 위한 View 컴포넌트
 * UI 구성 요소와 사용자 입력 처리를 담당
 */
class JsonQueryView {
    private val jmesPathField = SearchTextField()
    private val panel = JPanel(BorderLayout(4, 0))
    private var infoLabel = createInfoLabel(JsonQueryType.JMESPATH)

    init {
        jmesPathField.name = OnboardingTutorialTargetIds.QUERY_FIELD
        jmesPathField.textEditor.emptyText.text = LocalizationBundle.message("jmesPathPlaceholder")

        panel.add(jmesPathField, BorderLayout.CENTER)
        panel.add(infoLabel, BorderLayout.EAST)
    }

    val component: JComponent
        get() = panel

    var query: String
        get() = jmesPathField.text.trim()
        set(value) {
            jmesPathField.text = value
        }

    fun addKeyListener(listener: KeyAdapter) {
        jmesPathField.textEditor.addKeyListener(listener)
    }

    fun updatePlaceholder(queryType: JsonQueryType) {
        val key = when (queryType) {
            JsonQueryType.JAYWAY_JSONPATH -> "queryPlaceholder.jsonpath"
            JsonQueryType.JMESPATH -> "queryPlaceholder.jmespath"
            JsonQueryType.JACKSON_JQ -> "queryPlaceholder.jq"
        }
        jmesPathField.textEditor.emptyText.text = LocalizationBundle.message(key)
        updateHelpTooltip(queryType)
    }

    private fun updateHelpTooltip(queryType: JsonQueryType) {
        panel.remove(infoLabel)
        infoLabel = createInfoLabel(queryType)
        panel.add(infoLabel, BorderLayout.EAST)
        panel.revalidate()
        panel.repaint()
    }

    private fun getHelpContent(queryType: JsonQueryType): Triple<String, String, String> {
        val titleKey = when (queryType) {
            JsonQueryType.JAYWAY_JSONPATH -> "queryHelp.jsonpath.title"
            JsonQueryType.JMESPATH -> "queryHelp.jmespath.title"
            JsonQueryType.JACKSON_JQ -> "queryHelp.jq.title"
        }
        val descKey = when (queryType) {
            JsonQueryType.JAYWAY_JSONPATH -> "queryHelp.jsonpath.description"
            JsonQueryType.JMESPATH -> "queryHelp.jmespath.description"
            JsonQueryType.JACKSON_JQ -> "queryHelp.jq.description"
        }
        val link = when (queryType) {
            JsonQueryType.JAYWAY_JSONPATH -> "https://github.com/json-path/JsonPath#readme"
            JsonQueryType.JMESPATH -> "https://jmespath.org/tutorial.html"
            JsonQueryType.JACKSON_JQ -> "https://jqlang.github.io/jq/manual/"
        }
        return Triple(
            LocalizationBundle.message(titleKey),
            LocalizationBundle.message(descKey),
            link,
        )
    }

    private fun createInfoLabel(queryType: JsonQueryType): ContextHelpLabel {
        val (title, description, link) = getHelpContent(queryType)
        return ContextHelpLabel.createWithBrowserLink(
            title,
            description,
            LocalizationBundle.message("queryHelp.learnMore"),
            URI.create(link).toURL(),
        ).also {
            it.icon = AllIcons.General.ContextHelp
        }
    }
}
