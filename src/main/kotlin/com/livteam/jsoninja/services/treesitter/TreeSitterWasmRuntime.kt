package com.livteam.jsoninja.services.treesitter

import com.dylibso.chicory.compiler.Cache
import com.dylibso.chicory.compiler.InterpreterFallback
import com.dylibso.chicory.compiler.MachineFactoryCompiler
import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Machine
import com.dylibso.chicory.runtime.Memory
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.wasi.WasiOptions
import com.dylibso.chicory.wasi.WasiPreview1
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function

object TreeSitterWasmRuntime {
    private const val WASM_RESOURCE_PATH = "wasm/tree-sitter/tree-sitter.wasm"
    private const val PLUGIN_CACHE_DIR_NAME = "jsoninja"
    private const val WASM_CACHE_DIR_NAME = "wasm-cache"
    private const val CACHE_FORMAT_VERSION = "chicory-1.7.5-v1"
    private val LOG = logger<TreeSitterWasmRuntime>()

    data class RuntimeHandle(
        val instance: Instance,
        val memory: Memory,
        val alloc: ExportFunction,
        val dealloc: ExportFunction,
        val analyzeSource: ExportFunction,
        val getLastError: ExportFunction,
    ) {
        private val transactionLock = ReentrantLock()

        fun <T> withTransaction(checkCancellation: () -> Unit, action: () -> T): T {
            checkCancellation()
            while (!transactionLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                checkCancellation()
            }
            try {
                checkCancellation()
                // Once started, synchronous WASM work must finish buffer cleanup before releasing ownership.
                return action()
            } finally {
                transactionLock.unlock()
            }
        }
    }

    private val runtimeHandleReference = AtomicReference<RuntimeHandle?>()
    private val activeWasiReference = AtomicReference<WasiPreview1?>()
    private val bytecodeCache = WasmBytecodeCache()

    fun getOrCreate(): RuntimeHandle {
        ensureUnloadCleanupRegistered()
        runtimeHandleReference.get()?.let { return it }
        return synchronized(this) {
            runtimeHandleReference.get() ?: createRuntime().also(runtimeHandleReference::set)
        }
    }

    fun clear() {
        runtimeHandleReference.set(null)
        closeQuietly(activeWasiReference.getAndSet(null))
    }

    internal fun clearAll() {
        clear()
        bytecodeCache.clearInMemory()
    }

    private fun ensureUnloadCleanupRegistered() {
        val application = ApplicationManager.getApplication() ?: return
        if (!application.isDisposed) {
            application.getService(TreeSitterWasmRuntimeCleanupService::class.java)
        }
    }

    private fun createRuntime(): RuntimeHandle {
        val moduleStream = checkNotNull(javaClass.classLoader.getResourceAsStream(WASM_RESOURCE_PATH)) {
            "Bundled tree-sitter WASM module not found at $WASM_RESOURCE_PATH"
        }
        val wasmModule = moduleStream.use(Parser::parse)
        val machineFactory = compileMachineFactory(wasmModule)
        val wasiOptions = WasiOptions.builder()
            .withStdin(ByteArrayInputStream(ByteArray(0)))
            .withStdout(OutputStream.nullOutputStream())
            .withStderr(OutputStream.nullOutputStream())
            .build()
        val wasi = WasiPreview1.builder()
            .withOptions(wasiOptions)
            .build()
        val store = Store().addFunction(*wasi.toHostFunctions())
        val instance = if (machineFactory != null) {
            store.instantiate("tree-sitter-wasm") { importValues ->
                Instance.builder(wasmModule)
                    .withImportValues(importValues)
                    .withMachineFactory(machineFactory)
                    .build()
            }
        } else {
            store.instantiate("tree-sitter-wasm", wasmModule)
        }
        closeQuietly(activeWasiReference.getAndSet(wasi))
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

    private fun compileMachineFactory(wasmModule: WasmModule): Function<Instance, Machine>? {
        val digest = wasmModule.digest()
        var loadedFromCache = false
        val trackingCache = object : Cache {
            override fun get(key: String): ByteArray? {
                val cachedBytes = bytecodeCache.get(key)
                if (cachedBytes != null) {
                    loadedFromCache = true
                }
                return cachedBytes
            }

            override fun putIfAbsent(key: String, data: ByteArray) {
                bytecodeCache.putIfAbsent(key, data)
            }
        }

        try {
            return MachineFactoryCompiler.builder(wasmModule)
                .withInterpreterFallback(InterpreterFallback.SILENT)
                .withCache(trackingCache)
                .compile()
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (processCanceledException: ProcessCanceledException) {
            throw processCanceledException
        } catch (error: Throwable) {
            if (error is VirtualMachineError) throw error
            if (loadedFromCache && digest != null) {
                LOG.warn("Failed to load cached tree-sitter WASM bytecode; recompiling fresh.", error)
                bytecodeCache.evict(digest)
                try {
                    return MachineFactoryCompiler.builder(wasmModule)
                        .withInterpreterFallback(InterpreterFallback.SILENT)
                        .withCache(bytecodeCache)
                        .compile()
                } catch (retryCancellation: CancellationException) {
                    throw retryCancellation
                } catch (retryPce: ProcessCanceledException) {
                    throw retryPce
                } catch (retryError: Throwable) {
                    if (retryError is VirtualMachineError) throw retryError
                    LOG.warn(
                        "Tree-sitter WASM bytecode compilation failed after cache eviction; falling back to interpreter.",
                        retryError,
                    )
                    return null
                }
            }
            LOG.warn("Tree-sitter WASM bytecode compilation failed; falling back to interpreter.", error)
            return null
        }
    }

    private fun closeQuietly(wasi: WasiPreview1?) {
        if (wasi == null) return
        try {
            wasi.close()
        } catch (_: Throwable) {
            // WASI instance only wraps in-memory null streams; ignore shutdown noise.
        }
    }

    private class WasmBytecodeCache : Cache {
        private val inMemoryCache = ConcurrentHashMap<String, ByteArray>()

        override fun get(key: String): ByteArray? {
            inMemoryCache[key]?.let { return it }
            val diskFile = resolveDiskCacheFile(key) ?: return null
            if (!Files.isRegularFile(diskFile)) {
                return null
            }
            return try {
                val bytes = Files.readAllBytes(diskFile)
                if (bytes.isEmpty()) {
                    null
                } else {
                    inMemoryCache.putIfAbsent(key, bytes)
                    bytes
                }
            } catch (error: IOException) {
                LOG.warn("Failed to read cached tree-sitter WASM bytecode from $diskFile.", error)
                null
            } catch (error: SecurityException) {
                LOG.warn("Permission denied reading cached tree-sitter WASM bytecode from $diskFile.", error)
                null
            }
        }

        override fun putIfAbsent(key: String, data: ByteArray) {
            if (data.isEmpty()) return
            inMemoryCache.putIfAbsent(key, data)
            val diskFile = resolveDiskCacheFile(key) ?: return
            if (Files.isRegularFile(diskFile)) {
                return
            }
            val cacheDir = diskFile.parent ?: return
            var tempFile: Path? = null
            try {
                Files.createDirectories(cacheDir)
                tempFile = Files.createTempFile(cacheDir, ".tmp-wasm-", ".jar")
                Files.write(tempFile, data)
                moveAtomicallyOrReplace(tempFile, diskFile)
                tempFile = null
            } catch (error: IOException) {
                LOG.warn("Failed to persist tree-sitter WASM bytecode cache to $diskFile.", error)
            } catch (error: SecurityException) {
                LOG.warn("Permission denied writing tree-sitter WASM bytecode cache to $diskFile.", error)
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile)
                    } catch (_: IOException) {
                        // Best-effort cleanup for interrupted temp writes.
                    }
                }
            }
        }

        fun evict(key: String) {
            inMemoryCache.remove(key)
            val diskFile = resolveDiskCacheFile(key) ?: return
            try {
                Files.deleteIfExists(diskFile)
            } catch (_: IOException) {
                // Best-effort cleanup of corrupt cache artifact.
            } catch (_: SecurityException) {
                // Ignore if disk cache path is read-only.
            }
        }

        fun clearInMemory() {
            inMemoryCache.clear()
        }

        private fun resolveDiskCacheFile(key: String): Path? {
            val application = ApplicationManager.getApplication() ?: return null
            if (application.isUnitTestMode || application.isDisposed) {
                return null
            }
            val systemPath = try {
                PathManager.getSystemPath()
            } catch (_: Throwable) {
                return null
            }
            if (systemPath.isNullOrBlank()) {
                return null
            }
            val fileName = "$CACHE_FORMAT_VERSION-jdk${Runtime.version().feature()}-${sha256Hex(key)}.jar"
            return Path.of(systemPath, PLUGIN_CACHE_DIR_NAME, WASM_CACHE_DIR_NAME, fileName)
        }

        private fun moveAtomicallyOrReplace(source: Path, target: Path) {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: UnsupportedOperationException) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        private fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
            val hexChars = CharArray(digest.size * 2)
            val alphabet = "0123456789abcdef"
            for (index in digest.indices) {
                val unsignedByte = digest[index].toInt() and 0xFF
                hexChars[index * 2] = alphabet[unsignedByte ushr 4]
                hexChars[index * 2 + 1] = alphabet[unsignedByte and 0x0F]
            }
            return String(hexChars)
        }
    }
}

@Service(Service.Level.APP)
internal class TreeSitterWasmRuntimeCleanupService : Disposable {
    override fun dispose() {
        TreeSitterWasmRuntime.clearAll()
    }
}
