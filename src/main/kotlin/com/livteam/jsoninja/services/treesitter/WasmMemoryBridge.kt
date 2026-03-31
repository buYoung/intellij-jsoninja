package com.livteam.jsoninja.services.treesitter

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import java.nio.charset.StandardCharsets

class WasmMemoryBridge(
    private val memory: Memory,
    private val allocFunction: ExportFunction,
    private val deallocFunction: ExportFunction,
) {
    constructor(instance: Instance) : this(
        memory = instance.memory() ?: throw TreeSitterException("tree-sitter WASM module does not expose linear memory."),
        allocFunction = instance.export("alloc"),
        deallocFunction = instance.export("dealloc"),
    )

    fun writeString(value: String): Pair<Int, Int> {
        if (value.isEmpty()) {
            return 0 to 0
        }

        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val pointer = allocFunction.apply(bytes.size.toLong()).firstOrNull()?.toInt() ?: 0
        if (pointer <= 0) {
            throw TreeSitterException("Failed to allocate WASM memory for UTF-8 input.")
        }

        memory.write(pointer, bytes)
        return pointer to bytes.size
    }

    fun readString(
        pointer: Int,
        length: Int,
    ): String {
        if (pointer <= 0 || length <= 0) {
            return ""
        }

        return memory.readString(pointer, length, StandardCharsets.UTF_8)
    }

    fun decodePtrLen(packed: Long): Pair<Int, Int> {
        return ((packed ushr 32).toInt()) to packed.toInt()
    }

    fun free(
        pointer: Int,
        length: Int,
    ) {
        if (pointer <= 0) {
            return
        }

        deallocFunction.apply(pointer.toLong(), length.toLong())
    }
}
