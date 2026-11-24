package com.livteam.jsoninja.services.schema

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonHttpConnection
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.ui.dialog.generateJson.schema.SchemaStoreCatalogItem
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Service(Service.Level.APP)
class SchemaStoreCatalogService : Disposable {
    private data class CachedCatalog(
        val items: List<SchemaStoreCatalogItem>,
        val loadedAtMillis: Long,
    )

    private val loadMutex = Mutex()
    @Volatile
    private var cachedCatalog: CachedCatalog? = null

    fun getCachedCatalogOrNull(): List<SchemaStoreCatalogItem>? {
        val cached = cachedCatalog ?: return null
        val ageMillis = System.currentTimeMillis() - cached.loadedAtMillis
        return if (ageMillis in 0..CATALOG_TTL_MS && cached.items.isNotEmpty()) cached.items else null
    }

    suspend fun getCatalog(): List<SchemaStoreCatalogItem> {
        getCachedCatalogOrNull()?.let { return it }

        return loadMutex.withLock {
            getCachedCatalogOrNull()?.let { return@withLock it }

            val catalogText = withContext(Dispatchers.IO) {
                fetchCatalogText()
            }
            val parsedItems = withContext(Dispatchers.Default) {
                parseCatalog(catalogText)
            }
            if (parsedItems.isEmpty()) {
                throw IOException("SchemaStore catalog has no valid entries.")
            }

            cachedCatalog = CachedCatalog(
                items = parsedItems,
                loadedAtMillis = System.currentTimeMillis(),
            )
            parsedItems
        }
    }

    override fun dispose() {
        cachedCatalog = null
    }

    private fun fetchCatalogText(): String {
        return JsonHttpConnection.withConnection(SCHEMA_STORE_CATALOG_URL) { connection ->
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/schema+json, application/json;q=0.9, */*;q=0.8")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.errorStream?.use {
                    throw IOException(LocalizationBundle.message("dialog.generate.json.schema.url.http.status", responseCode))
                }
                throw IOException(LocalizationBundle.message("dialog.generate.json.schema.url.http.status", responseCode))
            }
            connection.inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        }
    }

    private fun parseCatalog(catalogText: String): List<SchemaStoreCatalogItem> {
        val objectMapper = service<JsonObjectMapperService>().objectMapper
        val rootNode = objectMapper.readTree(catalogText)
        val schemasNode = rootNode?.path("schemas") ?: return emptyList()
        if (!schemasNode.isArray) {
            return emptyList()
        }

        val itemsByUrl = linkedMapOf<String, SchemaStoreCatalogItem>()
        for (schemaNode in schemasNode) {
            val schemaName = schemaNode.path("name").asText("").trim()
            val schemaUrl = schemaNode.path("url").asText("").trim()
            if (schemaName.isBlank() || schemaUrl.isBlank()) {
                continue
            }
            if (!itemsByUrl.containsKey(schemaUrl)) {
                itemsByUrl[schemaUrl] = SchemaStoreCatalogItem(
                    name = schemaName,
                    url = schemaUrl,
                )
            }
        }
        return itemsByUrl.values.toList()
    }

    companion object {
        private const val SCHEMA_STORE_CATALOG_URL = "https://www.schemastore.org/api/json/catalog.json"
        private const val CATALOG_TTL_MS = 30L * 60L * 1000L
    }
}
