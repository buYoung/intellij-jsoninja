package com.livteam.jsoninja.services

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class JsoninjaCoroutineScopeService(
    private val coroutineScope: CoroutineScope,
) {
    fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return coroutineScope.launch(block = block)
    }

    fun createChildScope(): CoroutineScope {
        val parentJob = coroutineScope.coroutineContext[Job]
        return CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(parentJob))
    }
}
