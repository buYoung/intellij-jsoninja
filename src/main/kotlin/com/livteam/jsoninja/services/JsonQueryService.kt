package com.livteam.jsoninja.services

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.InvalidJsonException
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import io.burt.jmespath.Expression
import io.burt.jmespath.jackson.JacksonRuntime
import java.lang.ref.SoftReference
import net.thisptr.jackson.jq.BuiltinFunctionLoader
import net.thisptr.jackson.jq.JsonQuery
import net.thisptr.jackson.jq.Scope
import net.thisptr.jackson.jq.Versions
import kotlinx.coroutines.CancellationException

/**
 * 다양한 쿼리 언어(Jayway JsonPath, JMESPath, jq)를 사용하여 JSON 데이터를 쿼리하는 서비스입니다.
 */
@Service(Service.Level.PROJECT)
class JsonQueryService(private val project: Project) : Disposable {
    private val LOG = logger<JsonQueryService>()
    private val objectMapperService by lazy { service<JsonObjectMapperService>() }
    private val standardObjectMapper: ObjectMapper
        get() = objectMapperService.standardObjectMapper
    private val objectMapper: ObjectMapper
        get() = objectMapperService.objectMapper

    // Jayway JsonPath 설정: 표준 JSON fast-path (Json5InputNormalizer 생략) + JSON5 fallback
    private val standardJsonPathConfiguration by lazy {
        Configuration.builder()
            .jsonProvider(JacksonJsonProvider(standardObjectMapper))
            .mappingProvider(JacksonMappingProvider(standardObjectMapper))
            .build()
    }

    private val jsonPathConfiguration by lazy {
        Configuration.builder()
            .jsonProvider(JacksonJsonProvider(objectMapper))
            .mappingProvider(JacksonMappingProvider(objectMapper))
            .build()
    }

    // JMESPath 런타임
    private val jmesPathRuntime by lazy { JacksonRuntime() }

    // Jackson-jq Scope (built-in functions loaded once)
    private val jqScope: Scope by lazy {
        Scope.newEmptyScope().also {
            BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, it)
        }
    }

    // Compiled expression LRU caches (thread-safe, bounded, cleared on dispose)
    private val jsonPathExpressionCache = BoundedLruCache<String, JsonPath>(MAX_EXPRESSION_CACHE_SIZE)
    private val jmesPathExpressionCache = BoundedLruCache<String, Expression<JsonNode>>(MAX_EXPRESSION_CACHE_SIZE)
    private val jqExpressionCache = BoundedLruCache<String, JsonQuery>(MAX_EXPRESSION_CACHE_SIZE)

    // Parsed document LRU cache (thread-safe, bounded, soft-referenced values, cleared on dispose)
    private val documentCacheLock = Any()
    private val documentCache = ArrayDeque<CachedDocumentEntry>(MAX_DOCUMENT_CACHE_SIZE)

    /**
     * 설정된 쿼리 언어를 사용하여 주어진 JSON 문자열에 쿼리를 적용합니다.
     * 표현식 검증/컴파일과 JSON 파싱을 캐시하여 단일 패스로 실행합니다.
     *
     * @param jsonString JSON 문자열
     * @param expression 쿼리 표현식
     * @return 쿼리 결과를 JSON 문자열로 반환, 오류 발생 시 null 반환
     */
    fun query(jsonString: String, expression: String): String? {
        if (jsonString.isBlank() || expression.isBlank()) {
            return null
        }

        val settings = JsoninjaSettingsState.getInstance(project)
        val type = JsonQueryType.fromString(settings.jsonQueryType)

        return try {
            when (type) {
                JsonQueryType.JAYWAY_JSONPATH -> queryJsonPath(jsonString, expression)
                JsonQueryType.JMESPATH -> queryJmesPath(jsonString, expression)
                JsonQueryType.JACKSON_JQ -> queryJq(jsonString, expression)
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (processCanceledException: ProcessCanceledException) {
            throw processCanceledException
        } catch (e: Exception) {
            LOG.warn("쿼리 실행 중 오류 발생 ($type): $expression", e)
            null
        }
    }

    private fun queryJsonPath(jsonString: String, expression: String): String? {
        // Compile/validate expression first so invalid queries fail fast without parsing large JSON documents.
        val compiledPath = getOrCompileJsonPath(expression)
        val context = getOrParseJsonPathContext(jsonString) ?: return null
        val result = context.read<Any?>(compiledPath)
        return standardObjectMapper.writeValueAsString(result)
    }

    private fun queryJmesPath(jsonString: String, expression: String): String? {
        val jmesPathExpression = getOrCompileJmesPath(expression)
        val jsonNode = getOrParseJsonNode(jsonString) ?: return null
        val result = jmesPathExpression.search(jsonNode) ?: return null
        return standardObjectMapper.writeValueAsString(result)
    }

    private fun queryJq(jsonString: String, expression: String): String? {
        val compiledQuery = getOrCompileJq(expression)
        val inputNode = getOrParseJsonNode(jsonString) ?: return null
        val results = mutableListOf<JsonNode>()

        compiledQuery.apply(jqScope, inputNode) { result ->
            results.add(result)
        }

        if (results.isEmpty()) {
            LOG.warn("jq 쿼리 결과가 없습니다: $expression")
            return null
        }

        return if (results.size == 1) {
            standardObjectMapper.writeValueAsString(results.single())
        } else {
            val arrayNode = standardObjectMapper.createArrayNode()
            results.forEach { arrayNode.add(it) }
            standardObjectMapper.writeValueAsString(arrayNode)
        }
    }

    /**
     * 설정된 쿼리 언어에 대해 표현식이 유효한지 검증합니다.
     * 컴파일된 표현식은 캐시에 저장되어 직후 query() 호출에서 재사용됩니다.
     *
     * @param expression 검증할 표현식
     * @return 유효성 여부
     */
    fun isValidExpression(expression: String): Boolean {
        if (expression.isBlank()) return false

        val settings = JsoninjaSettingsState.getInstance(project)
        val type = JsonQueryType.fromString(settings.jsonQueryType)

        return try {
            when (type) {
                JsonQueryType.JAYWAY_JSONPATH -> {
                    getOrCompileJsonPath(expression)
                    true
                }
                JsonQueryType.JMESPATH -> {
                    getOrCompileJmesPath(expression)
                    true
                }
                JsonQueryType.JACKSON_JQ -> {
                    getOrCompileJq(expression)
                    true
                }
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (processCanceledException: ProcessCanceledException) {
            throw processCanceledException
        } catch (e: Exception) {
            LOG.debug("유효하지 않은 표현식 ($type): $expression", e)
            false
        }
    }

    override fun dispose() {
        jsonPathExpressionCache.clear()
        jmesPathExpressionCache.clear()
        jqExpressionCache.clear()
        synchronized(documentCacheLock) {
            documentCache.clear()
        }
    }

    private fun getOrCompileJsonPath(expression: String): JsonPath {
        return jsonPathExpressionCache.getOrPut(expression) { JsonPath.compile(expression) }
    }

    private fun getOrCompileJmesPath(expression: String): Expression<JsonNode> {
        return jmesPathExpressionCache.getOrPut(expression) { jmesPathRuntime.compile(expression) }
    }

    private fun getOrCompileJq(expression: String): JsonQuery {
        return jqExpressionCache.getOrPut(expression) { JsonQuery.compile(expression, Versions.JQ_1_6) }
    }

    private fun getOrParseJsonNode(jsonString: String): JsonNode? {
        val entry = findOrCreateDocumentEntry(jsonString)
        entry.jsonNodeRef?.get()?.let { return it }

        val parsedNode = parseSingleValidJsonNode(jsonString) ?: return null
        entry.jsonNodeRef = SoftReference(parsedNode)
        return parsedNode
    }

    private fun getOrParseJsonPathContext(jsonString: String): DocumentContext? {
        val entry = findOrCreateDocumentEntry(jsonString)
        entry.jsonPathContextRef?.get()?.let { return it }

        val parsedContext = parseJsonPathContextFast(jsonString) ?: return null
        entry.jsonPathContextRef = SoftReference(parsedContext)
        return parsedContext
    }

    private fun parseSingleValidJsonNode(jsonString: String): JsonNode? {
        return try {
            parseSingleNode(standardObjectMapper, jsonString)
        } catch (_: JsonProcessingException) {
            try {
                parseSingleNode(objectMapper, jsonString)
            } catch (_: JsonProcessingException) {
                null
            }
        }
    }

    private fun parseSingleNode(mapper: ObjectMapper, jsonString: String): JsonNode? {
        return mapper.factory.createParser(jsonString).use { parser ->
            if (parser.nextToken() == null) return null
            val node = mapper.readTree<JsonNode>(parser) ?: return null
            if (parser.nextToken() != null) return null
            node
        }
    }

    private fun parseJsonPathContextFast(jsonString: String): DocumentContext? {
        return try {
            JsonPath.using(standardJsonPathConfiguration).parse(jsonString)
        } catch (_: InvalidJsonException) {
            try {
                JsonPath.using(jsonPathConfiguration).parse(jsonString)
            } catch (_: InvalidJsonException) {
                null
            }
        }
    }

    private fun findOrCreateDocumentEntry(jsonString: String): CachedDocumentEntry {
        val length = jsonString.length
        synchronized(documentCacheLock) {
            // Fast O(1) identity check first for repeated queries against the same String instance.
            for (index in documentCache.indices) {
                val existing = documentCache[index]
                if (existing.sourceJson === jsonString) {
                    promoteEntryLocked(index, existing)
                    return existing
                }
            }

            val contentHash = jsonString.hashCode()
            for (index in documentCache.indices) {
                val existing = documentCache[index]
                if (existing.length == length &&
                    existing.contentHash == contentHash &&
                    existing.sourceJson == jsonString
                ) {
                    promoteEntryLocked(index, existing)
                    return existing
                }
            }

            val newEntry = CachedDocumentEntry(
                sourceJson = jsonString,
                length = length,
                contentHash = contentHash
            )
            if (documentCache.size >= MAX_DOCUMENT_CACHE_SIZE) {
                documentCache.removeLast()
            }
            documentCache.addFirst(newEntry)
            return newEntry
        }
    }

    private fun promoteEntryLocked(index: Int, entry: CachedDocumentEntry) {
        if (index > 0) {
            documentCache.removeAt(index)
            documentCache.addFirst(entry)
        }
    }

    private class CachedDocumentEntry(
        val sourceJson: String,
        val length: Int,
        val contentHash: Int,
        @Volatile var jsonNodeRef: SoftReference<JsonNode>? = null,
        @Volatile var jsonPathContextRef: SoftReference<DocumentContext>? = null,
    )

    private class BoundedLruCache<K, V>(private val maxEntries: Int) {
        private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxEntries
            }
        }

        fun getOrPut(key: K, compute: () -> V): V {
            synchronized(map) {
                map[key]?.let { return it }
            }
            val computed = compute()
            synchronized(map) {
                map[key]?.let { return it }
                map[key] = computed
                return computed
            }
        }

        fun clear() {
            synchronized(map) {
                map.clear()
            }
        }
    }

    companion object {
        private const val MAX_EXPRESSION_CACHE_SIZE = 64
        private const val MAX_DOCUMENT_CACHE_SIZE = 4
    }
}
