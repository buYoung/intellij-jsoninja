package com.livteam.jsoninja.services.treesitter

import java.nio.charset.StandardCharsets

class WasmMemoryBridge(
    private val runtimeHandle: TreeSitterWasmRuntime.RuntimeHandle,
) {
    data class BufferSlice(
        val pointer: Int,
        val length: Int,
    )

    fun writeUtf8String(value: String): BufferSlice {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        if (bytes.isEmpty()) {
            return BufferSlice(pointer = 0, length = 0)
        }

        val pointer = runtimeHandle.alloc.apply(bytes.size.toLong()).firstOrNull()?.toInt() ?: 0
        check(pointer > 0) { "Failed to allocate WASM memory for input text." }
        runtimeHandle.memory.write(pointer, bytes)
        return BufferSlice(pointer = pointer, length = bytes.size)
    }

    fun readUtf8String(bufferSlice: BufferSlice): String {
        if (bufferSlice.length <= 0) {
            return ""
        }
        return runtimeHandle.memory.readString(bufferSlice.pointer, bufferSlice.length, StandardCharsets.UTF_8)
    }

    fun releaseBuffer(bufferSlice: BufferSlice) {
        if (bufferSlice.pointer <= 0 || bufferSlice.length < 0) {
            return
        }
        runtimeHandle.dealloc.apply(bufferSlice.pointer.toLong(), bufferSlice.length.toLong())
    }

    fun unpackPointerLength(packedValue: Long): BufferSlice {
        val pointer = (packedValue ushr 32).toInt()
        val length = (packedValue and 0xffffffffL).toInt()
        return BufferSlice(pointer = pointer, length = length)
    }

    fun isErrorCode(packedValue: Long): Boolean {
        return (packedValue ushr 32) == 0L && packedValue.toInt() != 0
    }

    fun readLastErrorMessage(): String {
        val packedValue = runtimeHandle.getLastError.apply().firstOrNull() ?: 0L
        if (packedValue == 0L) {
            return ""
        }

        val bufferSlice = unpackPointerLength(packedValue)
        return try {
            readUtf8String(bufferSlice)
        } finally {
            releaseBuffer(bufferSlice)
        }
    }
}
