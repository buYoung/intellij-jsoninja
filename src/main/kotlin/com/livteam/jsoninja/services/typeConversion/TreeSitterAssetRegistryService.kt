package com.livteam.jsoninja.services.typeConversion

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.livteam.jsoninja.model.SupportedLanguage
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class TreeSitterAssetRegistryService : Disposable {
    private val queryTextCache = ConcurrentHashMap<SupportedLanguage, String>()
    private val queryUtf8BytesCache = ConcurrentHashMap<SupportedLanguage, ByteArray>()

    fun getQueryResourcePath(language: SupportedLanguage): String {
        return "tree-sitter/queries/${language.name.lowercase()}/type-declarations.scm"
    }

    fun loadQuery(language: SupportedLanguage): String {
        return queryTextCache.computeIfAbsent(language) { readQueryFromResource(it) }
    }

    fun loadQueryUtf8Bytes(language: SupportedLanguage): ByteArray {
        return queryUtf8BytesCache.computeIfAbsent(language) {
            loadQuery(it).toByteArray(StandardCharsets.UTF_8)
        }
    }

    private fun readQueryFromResource(language: SupportedLanguage): String {
        val resourcePath = getQueryResourcePath(language)
        val resourceStream = checkNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Bundled tree-sitter query not found at $resourcePath"
        }
        return resourceStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    override fun dispose() {
        queryTextCache.clear()
        queryUtf8BytesCache.clear()
    }
}
