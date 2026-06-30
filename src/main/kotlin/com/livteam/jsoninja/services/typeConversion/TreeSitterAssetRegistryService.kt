package com.livteam.jsoninja.services.typeConversion

import com.intellij.openapi.components.Service
import com.livteam.jsoninja.model.SupportedLanguage

@Service(Service.Level.APP)
class TreeSitterAssetRegistryService {
    fun getQueryResourcePath(language: SupportedLanguage): String {
        return "tree-sitter/queries/${language.name.lowercase()}/type-declarations.scm"
    }

    fun loadQuery(language: SupportedLanguage): String {
        val resourcePath = getQueryResourcePath(language)
        val resourceStream = checkNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Bundled tree-sitter query not found at $resourcePath"
        }
        return resourceStream.bufferedReader().use { it.readText() }
    }
}
