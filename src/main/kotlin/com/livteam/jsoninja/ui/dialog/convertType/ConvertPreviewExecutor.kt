package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.application.EDT
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConvertPreviewExecutor {
    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var previewJob: Job? = null
    private val requestSequence = AtomicInteger()

    fun submit(
        delayMs: Int,
        onLoading: () -> Unit,
        computePreview: () -> String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val requestId = requestSequence.incrementAndGet()
        previewJob?.cancel()
        previewJob = previewScope.launch {
            delay(delayMs.toLong())
            if (requestId != requestSequence.get()) {
                return@launch
            }

            withContext(Dispatchers.EDT) {
                if (requestId != requestSequence.get()) {
                    return@withContext
                }
                onLoading()
            }

            val previewResult = try {
                Result.success(computePreview())
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
            withContext(Dispatchers.EDT) {
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
        previewScope.cancel()
    }
}
