package com.livteam.jsoninja.services

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service(Service.Level.PROJECT)
class WasmRuntimeProbeService {
    private val bundledResourceService = service<BundledResourceService>()

    fun runProbe(): WasmProbeResult {
        val resourcePath = "wasm/probe/add.wasm"
        val exportName = "add"
        val leftValue = 1
        val rightValue = 41
        val expectedResultValue = 42
        val moduleBytes = bundledResourceService.loadBytes(resourcePath)

        val module = Parser.parse(moduleBytes)
        val instance = Instance.builder(module).build()
        val resultValue = instance.export(exportName).apply(leftValue.toLong(), rightValue.toLong())[0].toInt()

        if (resultValue != expectedResultValue) {
            throw IllegalStateException(
                "Unexpected Wasm probe result. Expected $expectedResultValue but received $resultValue."
            )
        }

        return WasmProbeResult(
            exportName = exportName,
            leftValue = leftValue,
            rightValue = rightValue,
            resultValue = resultValue,
            moduleByteCount = moduleBytes.size,
        )
    }
}
