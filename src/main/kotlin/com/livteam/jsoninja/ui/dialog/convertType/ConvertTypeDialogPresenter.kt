package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.wm.IdeFocusManager
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.JsonObjectMapperService
import java.util.Locale
import javax.swing.SwingUtilities

class ConvertTypeDialogPresenter(
    private val project: Project,
    seedText: String,
    forcedTabIndex: Int?,
    sourceFileExtension: String? = null,
) {
    private val seedResolution = resolveSeedOnEdt(seedText, forcedTabIndex, sourceFileExtension)
    private var isSynchronizingLanguage = false
    private var onPreviewStateChanged: (() -> Unit)? = null
    private val jsonToTypePresenter = JsonToTypeDialogPresenter(project, seedResolution.jsonInputText) { syncLanguage(it, sourceTabIndex = 0) }
    private val typeToJsonPresenter = TypeToJsonDialogPresenter(
        project = project,
        initialInputText = seedResolution.typeInputText,
        initialLanguageOverride = seedResolution.detectedSourceLanguage,
    ) { syncLanguage(it, sourceTabIndex = 1) }
    private val view = ConvertTypeDialogView(
        jsonToTypeComponent = jsonToTypePresenter.component,
        typeToJsonComponent = typeToJsonPresenter.component,
        project = project,
    )

    init {
        view.setSelectedTabIndex(forcedTabIndex ?: seedResolution.selectedTabIndex)
        jsonToTypePresenter.setOnPreviewStateChanged { onPreviewStateChanged?.invoke() }
        typeToJsonPresenter.setOnPreviewStateChanged { onPreviewStateChanged?.invoke() }
        view.setOnTabChanged {
            onPreviewStateChanged?.invoke()
            val target = getPreferredFocusedComponent()
            SwingUtilities.invokeLater {
                if (!project.isDisposed && target.isShowing) {
                    IdeFocusManager.getInstance(project).requestFocus(target, true)
                }
            }
        }
    }

    val component
        get() = view.component

    fun getPreferredFocusedComponent() = when (view.getSelectedTabIndex()) {
        0 -> jsonToTypePresenter.getPreferredFocusedComponent()
        else -> typeToJsonPresenter.getPreferredFocusedComponent()
    }

    fun validateCurrentTab(): ValidationInfo? {
        return when (view.getSelectedTabIndex()) {
            0 -> jsonToTypePresenter.validate()
            else -> typeToJsonPresenter.validate()
        }
    }

    fun getCurrentPreviewText(): String {
        return when (view.getSelectedTabIndex()) {
            0 -> jsonToTypePresenter.getCurrentPreviewText()
            else -> typeToJsonPresenter.getCurrentPreviewText()
        }
    }

    fun hasCurrentPreview(): Boolean = getCurrentPreviewText().isNotBlank()

    fun consumeCurrentPreview(consumer: (String, String) -> Unit): Boolean {
        val text = getCurrentPreviewText()
        if (text.isBlank()) return false
        consumer(text, getCurrentOutputFileExtension())
        return true
    }

    fun setOnPreviewStateChanged(callback: () -> Unit) {
        onPreviewStateChanged = callback
        callback()
    }

    fun getCurrentOutputFileExtension(): String {
        return when (view.getSelectedTabIndex()) {
            0 -> jsonToTypePresenter.getOutputFileExtension()
            else -> typeToJsonPresenter.getOutputFileExtension()
        }
    }

    fun copyCurrentPreview() {
        when (view.getSelectedTabIndex()) {
            0 -> jsonToTypePresenter.copyPreview()
            else -> typeToJsonPresenter.copyPreview()
        }
    }

    fun dispose() {
        jsonToTypePresenter.dispose()
        typeToJsonPresenter.dispose()
    }

    private fun syncLanguage(
        language: SupportedLanguage,
        sourceTabIndex: Int,
    ) {
        if (isSynchronizingLanguage) {
            return
        }

        isSynchronizingLanguage = true
        try {
            if (sourceTabIndex == 0) {
                typeToJsonPresenter.updateLanguage(language)
            } else {
                jsonToTypePresenter.updateLanguage(language)
            }
        } finally {
            isSynchronizingLanguage = false
        }
    }

    private fun resolveSeedOnEdt(
        seedText: String,
        forcedTabIndex: Int?,
        sourceFileExtension: String?,
    ): ConvertTypeSeedResolution {
        val detectedLanguage = ConvertTypeInputSeedResolver.detectLanguageByExtension(sourceFileExtension)
        return when (forcedTabIndex) {
            0 -> ConvertTypeSeedResolution(0, seedText, "")
            1 -> ConvertTypeSeedResolution(
                selectedTabIndex = 1,
                jsonInputText = "",
                typeInputText = seedText,
                detectedSourceLanguage = detectedLanguage,
            )
            else -> {
                if (seedText.isBlank()) {
                    ConvertTypeSeedResolution(
                        selectedTabIndex = 0,
                        jsonInputText = "",
                        typeInputText = "",
                        detectedSourceLanguage = detectedLanguage,
                    )
                } else if (seedText.length <= MAX_SYNC_PARSE_LENGTH) {
                    ConvertTypeInputSeedResolver(service<JsonObjectMapperService>().objectMapper)
                        .resolve(seedText, sourceFileExtension)
                } else if (looksLikeLargeJsonSeed(seedText, sourceFileExtension, detectedLanguage)) {
                    ConvertTypeSeedResolution(
                        selectedTabIndex = 0,
                        jsonInputText = seedText,
                        typeInputText = "",
                        detectedSourceLanguage = null,
                    )
                } else {
                    ConvertTypeSeedResolution(
                        selectedTabIndex = 1,
                        jsonInputText = "",
                        typeInputText = seedText,
                        detectedSourceLanguage = detectedLanguage,
                    )
                }
            }
        }
    }

    /**
     * Bounded O(1) prefix/suffix classification for large seeds (>16KB) so opening the dialog
     * never runs synchronous full-tree Jackson parsing on the EDT.
     */
    private fun looksLikeLargeJsonSeed(
        seedText: String,
        sourceFileExtension: String?,
        detectedLanguage: SupportedLanguage?,
    ): Boolean {
        val normalizedExtension = sourceFileExtension?.lowercase(Locale.ROOT)
        if (normalizedExtension == "json" || normalizedExtension == "json5") {
            return true
        }

        val firstChar = findFirstJsonSignificantChar(seedText) ?: return false
        if (detectedLanguage != null && firstChar != '{' && firstChar != '[') {
            return false
        }

        val lastChar = findLastNonWhitespaceChar(seedText) ?: return false
        return (firstChar == '{' && lastChar == '}') || (firstChar == '[' && lastChar == ']')
    }

    private fun findFirstJsonSignificantChar(text: String): Char? {
        val limit = minOf(text.length, MAX_HEURISTIC_SCAN_CHARS)
        var index = 0
        if (index < limit && text[index] == '\uFEFF') {
            index++
        }
        while (index < limit) {
            val ch = text[index]
            if (ch.isWhitespace()) {
                index++
                continue
            }
            if (ch == '/' && index + 1 < limit) {
                val next = text[index + 1]
                if (next == '/') {
                    val newlineIndex = text.indexOf('\n', index + 2)
                    if (newlineIndex < 0 || newlineIndex >= limit) return null
                    index = newlineIndex + 1
                    continue
                }
                if (next == '*') {
                    val closeIndex = text.indexOf("*/", index + 2)
                    if (closeIndex < 0 || closeIndex + 2 > limit) return null
                    index = closeIndex + 2
                    continue
                }
            }
            return ch
        }
        return null
    }

    private fun findLastNonWhitespaceChar(text: String): Char? {
        val minIndex = maxOf(0, text.length - MAX_HEURISTIC_SCAN_CHARS)
        var index = text.length - 1
        while (index >= minIndex) {
            val ch = text[index]
            if (!ch.isWhitespace()) {
                return ch
            }
            index--
        }
        return null
    }

    private companion object {
        const val MAX_SYNC_PARSE_LENGTH = 16_384
        const val MAX_HEURISTIC_SCAN_CHARS = 4_096
    }
}
