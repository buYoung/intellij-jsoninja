package com.livteam.jsoninja.services.typeConversion

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.livteam.jsoninja.model.typeConversion.TypeConversionLanguage
import com.livteam.jsoninja.services.BundledResourceService
import com.livteam.jsoninja.services.JsonObjectMapperService

@Service(Service.Level.PROJECT)
class TreeSitterAssetRegistryService {
    private val resourceService = service<BundledResourceService>()
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val assetsByLanguage: Map<TypeConversionLanguage, TreeSitterLanguageAsset> by lazy(::loadAssets)

    fun getAsset(language: TypeConversionLanguage): TreeSitterLanguageAsset {
        return assetsByLanguage[language]
            ?: throw IllegalArgumentException("Unsupported tree-sitter language: $language")
    }

    fun loadParserModuleBytes(language: TypeConversionLanguage): ByteArray? {
        val parserResourcePath = getAsset(language).parserResourcePath ?: return null
        return resourceService.loadBytes(parserResourcePath)
    }

    fun loadQueryText(language: TypeConversionLanguage): String {
        return getAsset(language).queryText
    }

    fun getAssets(): List<TreeSitterLanguageAsset> {
        return assetsByLanguage.values.toList()
    }

    private fun loadAssets(): Map<TypeConversionLanguage, TreeSitterLanguageAsset> {
        val manifestText = resourceService.loadText(ASSET_MANIFEST_PATH)
        val manifest = objectMapper.readValue(manifestText, TreeSitterAssetManifest::class.java)

        return manifest.languages.associate { languageManifest ->
            val language = TypeConversionLanguage.valueOf(languageManifest.language)
            val queryText = resourceService.loadText(languageManifest.queryResourcePath)
            language to TreeSitterLanguageAsset(
                language = language,
                parserResourcePath = languageManifest.parserResourcePath,
                queryResourcePath = languageManifest.queryResourcePath,
                queryText = queryText,
            )
        }
    }

    companion object {
        const val ASSET_MANIFEST_PATH = "tree-sitter/asset-manifest.json"
    }
}

data class TreeSitterLanguageAsset(
    val language: TypeConversionLanguage,
    val parserResourcePath: String?,
    val queryResourcePath: String,
    val queryText: String,
) {
    val hasParserModule: Boolean
        get() = !parserResourcePath.isNullOrBlank()
}

private data class TreeSitterAssetManifest(
    val languages: List<TreeSitterLanguageManifest> = emptyList(),
)

private data class TreeSitterLanguageManifest(
    val language: String = "",
    val parserResourcePath: String? = null,
    val queryResourcePath: String = "",
)
