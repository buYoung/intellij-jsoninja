package com.livteam.jsoninja.services.treesitter

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import com.dylibso.chicory.wasm.Parser
import java.util.concurrent.atomic.AtomicReference

object TreeSitterWasmRuntime {
    private const val WASM_RESOURCE_PATH = "wasm/tree-sitter/tree-sitter.wasm"

    data class RuntimeHandle(
        val instance: Instance,
        val memory: Memory,
        val alloc: ExportFunction,
        val dealloc: ExportFunction,
        val analyzeSource: ExportFunction,
        val getLastError: ExportFunction,
    )

    private val runtimeHandleReference = AtomicReference<RuntimeHandle?>()

    fun getOrCreate(): RuntimeHandle {
        runtimeHandleReference.get()?.let { return it }
        return synchronized(this) {
            runtimeHandleReference.get() ?: createRuntime().also(runtimeHandleReference::set)
        }
    }

    fun clear() {
        runtimeHandleReference.set(null)
    }

    private fun createRuntime(): RuntimeHandle {
        val moduleStream = checkNotNull(javaClass.classLoader.getResourceAsStream(WASM_RESOURCE_PATH)) {
            "Bundled tree-sitter WASM module not found at $WASM_RESOURCE_PATH"
        }
        val wasmModule = moduleStream.use(Parser::parse)
        val instance = Instance.builder(wasmModule)
            .withImportValues(ImportValues.empty())
            .build()
        val memory = checkNotNull(instance.memory()) {
            "tree-sitter WASM module does not expose linear memory"
        }
        return RuntimeHandle(
            instance = instance,
            memory = memory,
            alloc = instance.export("alloc"),
            dealloc = instance.export("dealloc"),
            analyzeSource = instance.export("analyze_source"),
            getLastError = instance.export("get_last_error"),
        )
    }
}
