package com.livteam.jsoninja.services

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class JsoninjaCoroutineService(
    val coroutineScope: CoroutineScope,
)
