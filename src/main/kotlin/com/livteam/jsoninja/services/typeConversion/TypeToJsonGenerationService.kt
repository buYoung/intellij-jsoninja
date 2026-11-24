package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.TypeToJsonGenerationResult
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class TypeToJsonGenerationService(
    private val project: Project,
) {
    private val analyzerService by lazy { project.service<TypeDeclarationAnalyzerService>() }
    private val objectMapper by lazy { service<JsonObjectMapperService>().objectMapper }
    private val documentBuilder by lazy { TypeToJsonDocumentBuilder(objectMapper) }
    private val prettyPrinterCache = ConcurrentHashMap<Pair<Int, Boolean>, DefaultPrettyPrinter>()

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
        checkCancellation()
        val text = formatNodeDirectly(jsonNode, options.formatState)
        checkCancellation()
        return TypeToJsonGenerationResult(text, analysisResult.diagnostics.toList())
    }

    private fun formatNodeDirectly(node: JsonNode, formatState: JsonFormatState): String {
        val settings = JsoninjaSettingsState.getInstance(project)
        val usesSorting = if (formatState == JsonFormatState.UGLIFY) {
            false
        } else {
            formatState.usesSorting() || settings.sortKeys
        }
        val targetNode = if (usesSorting) sortObjectNodesRecursively(node) else node
        return if (formatState.usesPrettyPrinting()) {
            val prettyPrinter = prettyPrinterCache.computeIfAbsent(
                Pair(settings.indentSize, formatState.usesCompactArrays())
            ) { (indentSize, useCompactArrays) ->
                DirectJsonPrettyPrinter(indentSize, useCompactArrays)
            }
            objectMapper.writer(prettyPrinter).writeValueAsString(targetNode)
        } else {
            objectMapper.writeValueAsString(targetNode)
        }
    }

    private fun sortObjectNodesRecursively(node: JsonNode): JsonNode {
        return when (node) {
            is ObjectNode -> {
                val sortedNode = objectMapper.nodeFactory.objectNode()
                val entries = ArrayList<Map.Entry<String, JsonNode>>(node.size())
                val fieldsIterator = node.fields()
                while (fieldsIterator.hasNext()) {
                    entries.add(fieldsIterator.next())
                }
                entries.sortBy { it.key }
                for (entry in entries) {
                    sortedNode.set<JsonNode>(entry.key, sortObjectNodesRecursively(entry.value))
                }
                sortedNode
            }
            is ArrayNode -> {
                val sortedArray = objectMapper.nodeFactory.arrayNode(node.size())
                for (element in node) {
                    sortedArray.add(sortObjectNodesRecursively(element))
                }
                sortedArray
            }
            else -> node
        }
    }

    private class DirectJsonPrettyPrinter(
        private val indentSize: Int,
        private val useCompactArrays: Boolean,
    ) : DefaultPrettyPrinter(
        Separators.createDefaultInstance()
            .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
    ) {
        init {
            val indenter = DefaultIndenter(" ".repeat(indentSize), "\n")
            indentObjectsWith(indenter)
            if (!useCompactArrays) {
                indentArraysWith(indenter)
            }
        }

        override fun writeArrayValueSeparator(g: JsonGenerator) {
            if (useCompactArrays) {
                g.writeRaw(',')
                g.writeRaw(' ')
            } else {
                super.writeArrayValueSeparator(g)
            }
        }

        override fun writeStartArray(g: JsonGenerator) {
            super.writeStartArray(g)
            if (useCompactArrays) {
                g.writeRaw(' ')
            }
        }

        override fun writeEndArray(g: JsonGenerator, nrOfValues: Int) {
            if (useCompactArrays && nrOfValues > 0) {
                g.writeRaw(' ')
            }
            super.writeEndArray(g, nrOfValues)
        }

        override fun createInstance(): DefaultPrettyPrinter {
            return DirectJsonPrettyPrinter(indentSize, useCompactArrays)
        }
    }
}
