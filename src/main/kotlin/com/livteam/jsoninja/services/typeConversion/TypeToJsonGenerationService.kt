package com.livteam.jsoninja.services.typeConversion

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.TypeToJsonGenerationResult
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode

@Service(Service.Level.PROJECT)
class TypeToJsonGenerationService(
    private val project: Project,
) {
    private val analyzerService by lazy { project.service<TypeDeclarationAnalyzerService>() }
    private val formatterService by lazy { project.service<JsonFormatterService>() }
    private val objectMapper by lazy { service<JsonObjectMapperService>().objectMapper }
    private val documentBuilder by lazy { TypeToJsonDocumentBuilder(objectMapper) }

    fun generate(
        sourceCode: String,
        language: SupportedLanguage,
        options: TypeToJsonGenerationOptions,
        rootTypeName: String? = null,
    ): String = generate(sourceCode, language, options, rootTypeName) {}

    fun generate(
        sourceCode: String,
        language: SupportedLanguage,
        options: TypeToJsonGenerationOptions,
        rootTypeName: String? = null,
        checkCancellation: () -> Unit,
    ): String = generateDetailed(sourceCode, language, options, rootTypeName, checkCancellation).jsonText

    fun generateDetailed(
        sourceCode: String,
        language: SupportedLanguage,
        options: TypeToJsonGenerationOptions,
        rootTypeName: String? = null,
        checkCancellation: () -> Unit = {},
    ): TypeToJsonGenerationResult {
        require(sourceCode.isNotBlank()) { "Type declaration source code must not be blank." }
        require(options.outputCount in 1..100) { "Output count must be between 1 and 100." }

        val analysisResult = analyzerService.analyzeSource(sourceCode, language, checkCancellation)
        if (analysisResult.declarations.isEmpty()) {
            val diagnosticMessage = analysisResult.diagnostics.firstOrNull()?.message
            throw IllegalStateException(diagnosticMessage ?: "No type declarations found.")
        }
        if (analysisResult.diagnostics.any { it.severity.name == "ERROR" }) {
            val diagnosticMessage = analysisResult.diagnostics.firstOrNull { it.severity.name == "ERROR" }?.message
            throw IllegalStateException(diagnosticMessage ?: "Failed to parse type declaration.")
        }

        checkCancellation()
        if (options.propertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED) {
            val text = documentBuilder.buildCommentedDocument(analysisResult.declarations, options, rootTypeName)
            checkCancellation()
            return TypeToJsonGenerationResult(text, analysisResult.diagnostics.toList())
        }
        val jsonNode = documentBuilder.buildDocument(
            declarations = analysisResult.declarations,
            options = options,
            rootTypeName = rootTypeName,
        )
        val rawJson = objectMapper.writeValueAsString(jsonNode)
        checkCancellation()
        val text = formatterService.formatJson(rawJson, options.formatState)
        checkCancellation()
        return TypeToJsonGenerationResult(text, analysisResult.diagnostics.toList())
    }
}
