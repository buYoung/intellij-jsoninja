package com.livteam.jsoninja.ui.component.jsonQuery

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.ui.onboarding.OnboardingTutorialTargetIds
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.KeyAdapter
import java.net.URI
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * JSON Query 검색을 위한 View 컴포넌트
 * UI 구성 요소와 사용자 입력 처리를 담당
 */
class JsonQueryView {
    private val jmesPathField = SearchTextField()
    private val infoLabel = JLabel(AllIcons.General.ContextHelp)
    private val panel = JPanel(BorderLayout(4, 0))
    private var currentHelpTooltip: HelpTooltip? = null

    init {
        jmesPathField.name = OnboardingTutorialTargetIds.QUERY_FIELD
        jmesPathField.textEditor.emptyText.text = LocalizationBundle.message("jmesPathPlaceholder")

        infoLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        infoLabel.border = JBUI.Borders.emptyRight(6)

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
        HelpTooltip.dispose(infoLabel)

        val (title, description, link) = getHelpContent(queryType)

        currentHelpTooltip = createHelpTooltip(title, description)
            .setBrowserLink(
                LocalizationBundle.message("queryHelp.learnMore"),
                URI(link).toURL()
            )

        currentHelpTooltip?.installOn(infoLabel)
    }

    private fun createHelpTooltip(title: String, description: String): HelpTooltip {
        val helpTooltip = HelpTooltip()
        applyHelpTooltipTitle(helpTooltip, title)
        applyHelpTooltipDescription(helpTooltip, description)
        return helpTooltip
    }

    private fun applyHelpTooltipTitle(helpTooltip: HelpTooltip, title: String) {
        val htmlTitleSupplier = Supplier { HtmlChunk.text(title) }
        if (invokeHelpTooltipMethod(helpTooltip, "setTitleSupplier", Supplier::class.java, htmlTitleSupplier)) {
            return
        }

        invokeHelpTooltipMethod(helpTooltip, "setTitle", Supplier::class.java, Supplier { title })
    }

    private fun applyHelpTooltipDescription(helpTooltip: HelpTooltip, description: String) {
        if (invokeHelpTooltipMethod(helpTooltip, "setDescription", HtmlChunk::class.java, HtmlChunk.text(description))) {
            return
        }

        invokeHelpTooltipMethod(helpTooltip, "setDescription", String::class.java, description)
    }

    private fun invokeHelpTooltipMethod(
        helpTooltip: HelpTooltip,
        methodName: String,
        parameterType: Class<*>,
        argument: Any,
    ): Boolean {
        return runCatching {
            HelpTooltip::class.java.getMethod(methodName, parameterType).invoke(helpTooltip, argument)
        }.isSuccess
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
            link
        )
    }
}
