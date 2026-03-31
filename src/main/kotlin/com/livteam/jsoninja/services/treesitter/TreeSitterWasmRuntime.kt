package com.livteam.jsoninja.services.treesitter

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.ImportFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.WasmFunctionHandle
import com.dylibso.chicory.wasm.ChicoryException
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.Disposable
import com.livteam.jsoninja.services.BundledResourceService
import com.livteam.jsoninja.services.JsonObjectMapperService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TreeSitterWasmRuntime(
    private val bundledResourceService: BundledResourceService = BundledResourceService(),
    objectMapperService: JsonObjectMapperService = JsonObjectMapperService(),
) : Disposable {
    private val objectMapper: ObjectMapper = objectMapperService.objectMapper
    private val runtimeLock = ReentrantLock()
    private val isDisposed = AtomicBoolean(false)

    @Volatile
    private var runtimeBindings: RuntimeBindings? = null

    fun initialize() {
        ensureRuntimeBindings()
    }

    fun executeQuery(
        language: SupportedLanguage,
        sourceCode: String,
        query: String,
    ): TreeSitterQueryResult {
        require(sourceCode.isNotBlank()) { "tree-sitter source code is empty." }
        require(query.isNotBlank()) { "tree-sitter query is empty." }
        checkNotDisposed()

        return runtimeLock.withLock {
            val bindings = ensureRuntimeBindings()
            val parserHandle = createParser(bindings, language)

            try {
                val (sourcePointer, sourceLength) = bindings.memoryBridge.writeString(sourceCode)
                try {
                    val treeHandle = callI32Function(
                        exportFunction = bindings.parseFunction,
                        operationName = "parse",
                        parserHandle.value.toLong(),
                        sourcePointer.toLong(),
                        sourceLength.toLong(),
                    )

                    try {
                        val (queryPointer, queryLength) = bindings.memoryBridge.writeString(query)
                        try {
                            val packedResult = callI64Function(
                                exportFunction = bindings.queryExecuteFunction,
                                operationName = "query_execute",
                                treeHandle.toLong(),
                                queryPointer.toLong(),
                                queryLength.toLong(),
                                sourcePointer.toLong(),
                                sourceLength.toLong(),
                            )
                            val (resultPointer, resultLength) = decodeResultPointer(bindings, packedResult, "query_execute")

                            return@withLock try {
                                val resultJson = bindings.memoryBridge.readString(resultPointer, resultLength)
                                objectMapper.readValue(resultJson, TreeSitterQueryResult::class.java)
                            } catch (exception: Exception) {
                                throw TreeSitterException("Failed to decode tree-sitter query result JSON.", cause = exception)
                            } finally {
                                bindings.memoryBridge.free(resultPointer, resultLength)
                            }
                        } finally {
                            bindings.memoryBridge.free(queryPointer, queryLength)
                        }
                    } finally {
                        destroyTree(bindings, treeHandle)
                    }
                } finally {
                    bindings.memoryBridge.free(sourcePointer, sourceLength)
                }
            } finally {
                destroyParser(bindings, parserHandle.value)
            }
        }
    }

    override fun dispose() {
        if (!isDisposed.compareAndSet(false, true)) {
            return
        }

        runtimeLock.withLock {
            runtimeBindings = null
        }
    }

    private fun ensureRuntimeBindings(): RuntimeBindings {
        checkNotDisposed()

        runtimeBindings?.let { return it }

        return runtimeLock.withLock {
            runtimeBindings ?: createRuntimeBindings().also { runtimeBindings = it }
        }
    }

    private fun createRuntimeBindings(): RuntimeBindings {
        val moduleBytes = try {
            bundledResourceService.loadBytes(WASM_RESOURCE_PATH)
        } catch (exception: Exception) {
            throw TreeSitterException("Failed to load tree-sitter WASM resource from $WASM_RESOURCE_PATH.", cause = exception)
        }

        val instance = try {
            val module = Parser.parse(moduleBytes)
            Instance.builder(module)
                .withImportValues(createWasiImportValues())
                .build()
        } catch (exception: Exception) {
            throw TreeSitterException("Failed to initialize Chicory tree-sitter runtime.", cause = exception)
        }

        return RuntimeBindings(
            instance = instance,
            memoryBridge = WasmMemoryBridge(instance),
            parserCreateFunction = instance.export("parser_create"),
            parserDestroyFunction = instance.export("parser_destroy"),
            parseFunction = instance.export("parse"),
            queryExecuteFunction = instance.export("query_execute"),
            treeDestroyFunction = instance.export("tree_destroy"),
            getLastErrorFunction = instance.export("get_last_error"),
        )
    }

    private fun createParser(
        bindings: RuntimeBindings,
        language: SupportedLanguage,
    ): TreeSitterParserHandle {
        val parserHandle = callI32Function(
            exportFunction = bindings.parserCreateFunction,
            operationName = "parser_create",
            language.languageId.toLong(),
        )
        return TreeSitterParserHandle(parserHandle)
    }

    private fun destroyParser(
        bindings: RuntimeBindings,
        parserHandle: Int,
    ) {
        runCatching {
            bindings.parserDestroyFunction.apply(parserHandle.toLong())
        }.onFailure { exception ->
            throw wrapRuntimeFailure("parser_destroy", bindings, exception)
        }
    }

    private fun destroyTree(
        bindings: RuntimeBindings,
        treeHandle: Int,
    ) {
        runCatching {
            bindings.treeDestroyFunction.apply(treeHandle.toLong())
        }.onFailure { exception ->
            throw wrapRuntimeFailure("tree_destroy", bindings, exception)
        }
    }

    private fun callI32Function(
        exportFunction: ExportFunction,
        operationName: String,
        vararg arguments: Long,
    ): Int {
        val result = try {
            exportFunction.apply(*arguments).firstOrNull()?.toInt() ?: 0
        } catch (exception: Exception) {
            throw TreeSitterException("WASM call `$operationName` failed.", cause = exception)
        }

        if (result == -1) {
            throw TreeSitterException("WASM call `$operationName` failed.", cause = null)
        }

        return result
    }

    private fun callI64Function(
        exportFunction: ExportFunction,
        operationName: String,
        vararg arguments: Long,
    ): Long {
        return try {
            exportFunction.apply(*arguments).firstOrNull() ?: 0L
        } catch (exception: Exception) {
            throw TreeSitterException("WASM call `$operationName` failed.", cause = exception)
        }
    }

    private fun decodeResultPointer(
        bindings: RuntimeBindings,
        packedResult: Long,
        operationName: String,
    ): Pair<Int, Int> {
        val (pointer, lengthOrErrorCode) = bindings.memoryBridge.decodePtrLen(packedResult)
        if (pointer == 0 && lengthOrErrorCode != 0) {
            throw TreeSitterException(
                message = readLastErrorMessage(bindings).ifBlank {
                    "WASM call `$operationName` failed with error code $lengthOrErrorCode."
                },
                errorCode = lengthOrErrorCode,
            )
        }

        if (pointer <= 0 || lengthOrErrorCode <= 0) {
            throw TreeSitterException("WASM call `$operationName` returned an empty result buffer.")
        }

        return pointer to lengthOrErrorCode
    }

    private fun readLastErrorMessage(bindings: RuntimeBindings): String {
        val packedError = try {
            bindings.getLastErrorFunction.apply().firstOrNull() ?: 0L
        } catch (exception: ChicoryException) {
            return "Failed to read last tree-sitter WASM error message: ${exception.message}"
        }

        if (packedError == 0L) {
            return ""
        }

        val (pointer, length) = bindings.memoryBridge.decodePtrLen(packedError)
        return try {
            bindings.memoryBridge.readString(pointer, length)
        } finally {
            bindings.memoryBridge.free(pointer, length)
        }
    }

    private fun wrapRuntimeFailure(
        operationName: String,
        bindings: RuntimeBindings,
        exception: Throwable,
    ): TreeSitterException {
        val errorMessage = readLastErrorMessage(bindings)
        return TreeSitterException(
            message = if (errorMessage.isBlank()) {
                "WASM call `$operationName` failed."
            } else {
                errorMessage
            },
            cause = exception,
        )
    }

    private fun checkNotDisposed() {
        if (isDisposed.get()) {
            throw TreeSitterException("TreeSitterWasmRuntime is already disposed.")
        }
    }

    private fun createWasiImportValues(): ImportValues {
        return ImportValues.builder()
            .withFunctions(
                listOf(
                wasiFunction(
                    name = "random_get",
                    functionType = functionType(listOf(ValType.I32, ValType.I32), returnsI32 = true),
                ) { instance, arguments ->
                    val bufferPointer = arguments[0].toInt()
                    val bufferLength = arguments[1].toInt()
                    instance.memory()?.fill(0, bufferPointer, bufferPointer + bufferLength)
                    longArrayOf(0)
                },
                wasiFunction(
                    name = "environ_get",
                    functionType = functionType(listOf(ValType.I32, ValType.I32), returnsI32 = true),
                ) { _, _ ->
                    longArrayOf(0)
                },
                wasiFunction(
                    name = "environ_sizes_get",
                    functionType = functionType(listOf(ValType.I32, ValType.I32), returnsI32 = true),
                ) { instance, arguments ->
                    val countPointer = arguments[0].toInt()
                    val bufferSizePointer = arguments[1].toInt()
                    instance.memory()?.writeI32(countPointer, 0)
                    instance.memory()?.writeI32(bufferSizePointer, 0)
                    longArrayOf(0)
                },
                wasiFunction(
                    name = "clock_time_get",
                    functionType = functionType(listOf(ValType.I32, ValType.I64, ValType.I32), returnsI32 = true),
                ) { instance, arguments ->
                    val resultPointer = arguments[2].toInt()
                    instance.memory()?.writeLong(resultPointer, 0L)
                    longArrayOf(0)
                },
                wasiFunction(
                    name = "fd_close",
                    functionType = functionType(listOf(ValType.I32), returnsI32 = true),
                ) { _, _ ->
                    longArrayOf(0)
                },
                wasiFunction(
                    name = "fd_seek",
                    functionType = functionType(
                        listOf(ValType.I32, ValType.I64, ValType.I32, ValType.I32),
                        returnsI32 = true,
                    ),
                ) { instance, arguments ->
                    val resultPointer = arguments[3].toInt()
                    instance.memory()?.writeLong(resultPointer, 0L)
                    longArrayOf(0)
                },
                wasiFunction(
                    name = "fd_write",
                    functionType = functionType(
                        listOf(ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                        returnsI32 = true,
                    ),
                ) { instance, arguments ->
                    val writtenPointer = arguments[3].toInt()
                    instance.memory()?.writeI32(writtenPointer, 0)
                    longArrayOf(0)
                },
                wasiFunction(
                    name = "proc_exit",
                    functionType = FunctionType.accepting(ValType.I32),
                ) { _, arguments ->
                    throw TreeSitterException("tree-sitter WASM module unexpectedly called proc_exit(${arguments[0]}).")
                },
                )
            )
            .build()
    }

    private fun wasiFunction(
        name: String,
        functionType: FunctionType,
        handle: WasmFunctionHandle,
    ): ImportFunction {
        return ImportFunction(WASI_MODULE_NAME, name, functionType, handle)
    }

    private fun functionType(
        parameters: List<ValType>,
        returnsI32: Boolean = false,
    ): FunctionType {
        return FunctionType.of(parameters, if (returnsI32) listOf(ValType.I32) else emptyList())
    }

    private data class RuntimeBindings(
        val instance: Instance,
        val memoryBridge: WasmMemoryBridge,
        val parserCreateFunction: ExportFunction,
        val parserDestroyFunction: ExportFunction,
        val parseFunction: ExportFunction,
        val queryExecuteFunction: ExportFunction,
        val treeDestroyFunction: ExportFunction,
        val getLastErrorFunction: ExportFunction,
    )

    companion object {
        const val WASI_MODULE_NAME = "wasi_snapshot_preview1"
        const val WASM_RESOURCE_PATH = "wasm/tree-sitter/tree-sitter.wasm"
    }
}
