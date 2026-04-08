package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.JsonObjectMapperService

class ConvertTypeDialogPresenter(
    project: Project,
    seedText: String,
    forcedTabIndex: Int?,
) {
    private val seedResolver = ConvertTypeInputSeedResolver(service<JsonObjectMapperService>().objectMapper)
    private val seedResolution = when (forcedTabIndex) {
        0 -> ConvertTypeSeedResolution(0, seedText, "")
        1 -> ConvertTypeSeedResolution(1, "", seedText)
        else -> seedResolver.resolve(seedText)
    }
    private var isSynchronizingLanguage = false
    private val jsonToTypePresenter = JsonToTypeDialogPresenter(project, seedResolution.jsonInputText) { syncLanguage(it, sourceTabIndex = 0) }
    private val typeToJsonPresenter = TypeToJsonDialogPresenter(project, seedResolution.typeInputText) { syncLanguage(it, sourceTabIndex = 1) }
    private val view = ConvertTypeDialogView(
        jsonToTypeComponent = jsonToTypePresenter.component,
        typeToJsonComponent = typeToJsonPresenter.component,
    )

    init {
        view.setSelectedTabIndex(forcedTabIndex ?: seedResolution.selectedTabIndex)
    }

    val component
        get() = view.component

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
}
