package com.livteam.jsoninja.services.typeConversion

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.JsonToTypeConversionResult
import com.livteam.jsoninja.services.JsonObjectMapperService

@Service(Service.Level.PROJECT)
class JsonToTypeConversionService(
    private val project: Project,
) {
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val renderer = JsonToTypeRenderer()

    fun convert(
        jsonText: String,
        language: SupportedLanguage,
        options: JsonToTypeConversionOptions,
    ): String {
        return convertDetailed(jsonText, language, options).sourceCode
    }

    fun convertDetailed(
        jsonText: String,
        language: SupportedLanguage,
        options: JsonToTypeConversionOptions,
    ): JsonToTypeConversionResult {
        require(jsonText.isNotBlank()) { "JSON text must not be blank." }
        require(JsonToTypeNamingSupport.isValidTypeIdentifier(options.rootTypeName)) {
            "Root type name must be a valid identifier."
        }

        val jsonNode = objectMapper.readTree(jsonText)
        val inferenceContext = JsonToTypeInferenceContext(language, options)
        val inferenceResult = inferenceContext.infer(jsonNode)
        val renderedSourceCode = renderer.render(
            declarations = inferenceResult.declarations,
            language = language,
            options = options,
            warningMessages = inferenceResult.warnings.map { it.message },
        )
        return inferenceResult.copy(sourceCode = renderedSourceCode)
    }
}
