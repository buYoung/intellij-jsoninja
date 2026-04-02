package com.livteam.jsoninja.services.typeConversion

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonObjectMapperService

@Service(Service.Level.PROJECT)
class TypeToJsonGenerationService(
    private val project: Project,
) {
    private val analyzerService = project.getService(TypeDeclarationAnalyzerService::class.java)
    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val objectMapper = project.getService(JsonObjectMapperService::class.java).objectMapper
    private val documentBuilder = TypeToJsonDocumentBuilder(objectMapper)

    fun generate(
        sourceCode: String,
        language: SupportedLanguage,
        options: TypeToJsonGenerationOptions,
        rootTypeName: String? = null,
    ): String {
        require(sourceCode.isNotBlank()) { "Type declaration source code must not be blank." }
        require(options.outputCount in 1..100) { "Output count must be between 1 and 100." }

        val analysisResult = analyzerService.analyzeSource(sourceCode, language)
        if (analysisResult.declarations.isEmpty()) {
            val diagnosticMessage = analysisResult.diagnostics.firstOrNull()?.message
            throw IllegalStateException(diagnosticMessage ?: "No type declarations found.")
        }
        if (analysisResult.diagnostics.any { it.severity.name == "ERROR" }) {
            val diagnosticMessage = analysisResult.diagnostics.firstOrNull { it.severity.name == "ERROR" }?.message
            throw IllegalStateException(diagnosticMessage ?: "Failed to parse type declaration.")
        }

        val jsonNode = documentBuilder.buildDocument(
            declarations = analysisResult.declarations,
            options = options,
            rootTypeName = rootTypeName,
        )
        val rawJson = objectMapper.writeValueAsString(jsonNode)
        return formatterService.formatJson(rawJson, options.formatState)
    }
}
