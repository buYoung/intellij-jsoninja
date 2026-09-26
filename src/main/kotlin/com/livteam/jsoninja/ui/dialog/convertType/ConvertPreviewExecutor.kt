package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConvertPreviewExecutor(
    private val coroutineScope: CoroutineScope,
) {
    private val requestSequence = AtomicInteger()
    private var previewJob: Job? = null

    fun submit(
        delayMs: Int,
        onLoading: () -> Unit,
        computePreview: (() -> Unit) -> String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val requestId = requestSequence.incrementAndGet()
        previewJob?.cancel()
        previewJob = coroutineScope.launch {
            delay(delayMs.toLong())
            if (requestId != requestSequence.get()) {
                return@launch
            }

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                onLoading()
            }

            val previewResult = try {
                Result.success(
                    withContext(Dispatchers.Default) {
                        val context = currentCoroutineContext()
                        computePreview { context.ensureActive() }
                    }
                )
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                if (requestId != requestSequence.get()) {
                    return@withContext
                }
                previewResult.fold(onSuccess, onError)
            }
        }
    }

    fun cancel() {
        requestSequence.incrementAndGet()
        previewJob?.cancel()
        previewJob = null
    }

    fun dispose() {
        cancel()
    }
}
