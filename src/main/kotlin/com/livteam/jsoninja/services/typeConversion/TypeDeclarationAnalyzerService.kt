package com.livteam.jsoninja.services.typeConversion

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisResult
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.treesitter.TreeSitterQueryResult
import com.livteam.jsoninja.services.treesitter.TreeSitterWasmRuntime
import com.livteam.jsoninja.services.treesitter.WasmMemoryBridge

@Service(Service.Level.PROJECT)
class TypeDeclarationAnalyzerService(
    private val project: Project,
) {
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val assetRegistryService = service<TreeSitterAssetRegistryService>()

    fun analyzeSource(
        sourceCode: String,
        language: SupportedLanguage,
    ): TypeAnalysisResult {
        require(sourceCode.isNotBlank()) { "Type declaration source code must not be blank." }
        assetRegistryService.loadQuery(language)

        val runtimeHandle = TreeSitterWasmRuntime.getOrCreate()
        val memoryBridge = WasmMemoryBridge(runtimeHandle)
        val sourceBuffer = memoryBridge.writeUtf8String(sourceCode)

        try {
            val packedResult = runtimeHandle.analyzeSource
                .apply(language.wasmLanguageId.toLong(), sourceBuffer.pointer.toLong(), sourceBuffer.length.toLong())
                .firstOrNull() ?: 0L

            if (memoryBridge.isErrorCode(packedResult)) {
                val errorMessage = memoryBridge.readLastErrorMessage().ifBlank {
                    "tree-sitter WASM analysis failed."
                }
                throw IllegalStateException(errorMessage)
            }

            if (packedResult == 0L) {
                return TypeAnalysisResult(emptyList())
            }

            val resultBuffer = memoryBridge.unpackPointerLength(packedResult)
            return try {
                TreeSitterQueryResult.parse(
                    jsonText = memoryBridge.readUtf8String(resultBuffer),
                    objectMapper = objectMapper,
                ).toTypeAnalysisResult()
            } finally {
                memoryBridge.releaseBuffer(resultBuffer)
            }
        } finally {
            memoryBridge.releaseBuffer(sourceBuffer)
        }
    }
}
