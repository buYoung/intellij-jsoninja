package com.livteam.jsoninja.ui.dialog.convertType

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.util.Alarm
import java.util.concurrent.atomic.AtomicInteger

class ConvertPreviewExecutor {
    private val previewAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
    private val requestSequence = AtomicInteger()

    fun submit(
        delayMs: Int,
        onLoading: () -> Unit,
        computePreview: () -> String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val requestId = requestSequence.incrementAndGet()
        previewAlarm.cancelAllRequests()
        previewAlarm.addRequest(
            {
                if (requestId != requestSequence.get()) {
                    return@addRequest
                }

                onLoading()
                ApplicationManager.getApplication().executeOnPooledThread {
                    val previewResult = runCatching(computePreview)
                    invokeLater(ModalityState.any()) {
                        if (requestId != requestSequence.get()) {
                            return@invokeLater
                        }
                        previewResult.fold(onSuccess, onError)
                    }
                }
            },
            delayMs,
        )
    }

    fun cancel() {
        requestSequence.incrementAndGet()
        previewAlarm.cancelAllRequests()
    }

    fun dispose() {
        cancel()
        previewAlarm.dispose()
    }
}
