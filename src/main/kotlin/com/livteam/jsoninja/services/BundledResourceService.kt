package com.livteam.jsoninja.services

import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class BundledResourceService {

    fun hasResource(resourcePath: String): Boolean {
        return javaClass.classLoader.getResource(resourcePath) != null
    }

    fun loadBytes(resourcePath: String): ByteArray {
        return javaClass.classLoader.getResourceAsStream(resourcePath)?.use { inputStream ->
            inputStream.readBytes()
        } ?: throw IllegalArgumentException("Bundled resource not found: $resourcePath")
    }

    fun loadText(resourcePath: String): String {
        return loadBytes(resourcePath).toString(Charsets.UTF_8)
    }
}
