package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
class JsonSchemaNormalizer(private val project: Project) {
    private val strictObjectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())

    data class NormalizedJsonSchema(
        val resolvedSchemaNode: JsonNode,
        val rootConstraint: JsonSchemaConstraint
    )

    private data class SchemaDocumentContext(
        val rootNode: JsonNode,
        val anchorNodes: Map<String, JsonNode>,
        val baseDirectory: Path?,
        val baseUri: String?
    )

    fun normalize(schemaNode: JsonNode): NormalizedJsonSchema {
        val projectBasePath = project.basePath?.let { Paths.get(it) }
        val rootDocumentContext = SchemaDocumentContext(
            rootNode = schemaNode.deepCopy(),
            anchorNodes = collectAnchorNodes(schemaNode),
            baseDirectory = projectBasePath,
            baseUri = null
        )

        val loadedDocumentContextByPath = mutableMapOf<Path, SchemaDocumentContext>()
        val loadedRemoteDocumentContextByUri = mutableMapOf<String, SchemaDocumentContext>()
        val resolvedSchemaNode = resolveReferences(
            schemaNode = rootDocumentContext.rootNode,
            currentDocumentContext = rootDocumentContext,
            loadedDocumentContextByPath = loadedDocumentContextByPath,
            loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri,
            resolutionStack = mutableSetOf()
        )

        validateSchemaContradictions(resolvedSchemaNode, "#")
        val rootConstraint = createConstraint(resolvedSchemaNode, "#")
        return NormalizedJsonSchema(
            resolvedSchemaNode = resolvedSchemaNode,
            rootConstraint = rootConstraint
        )
    }

    private fun resolveReferences(
        schemaNode: JsonNode,
        currentDocumentContext: SchemaDocumentContext,
        loadedDocumentContextByPath: MutableMap<Path, SchemaDocumentContext>,
        loadedRemoteDocumentContextByUri: MutableMap<String, SchemaDocumentContext>,
        resolutionStack: MutableSet<String>
    ): JsonNode {
        if (schemaNode.isObject) {
            val schemaObjectNode = schemaNode as ObjectNode
            val referenceValue = schemaObjectNode.path("\$ref").takeIf { it.isTextual }?.asText()
                ?: schemaObjectNode.path("\$dynamicRef").takeIf { it.isTextual }?.asText()

            if (referenceValue != null) {
                val resolvedReferenceNode = resolveSingleReference(
                    referenceValue = referenceValue,
                    currentDocumentContext = currentDocumentContext,
                    loadedDocumentContextByPath = loadedDocumentContextByPath,
                    loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri,
                    resolutionStack = resolutionStack
                )

                val siblingSchemaNode = schemaObjectNode.deepCopy().apply {
                    remove("\$ref")
                    remove("\$dynamicRef")
                }

                val mergedSchemaNode = if (siblingSchemaNode.size() == 0) {
                    resolvedReferenceNode.deepCopy()
                } else {
                    JsonNodeFactory.instance.objectNode().apply {
                        val allOfSchemaArrayNode = putArray("allOf")
                        allOfSchemaArrayNode.add(resolvedReferenceNode.deepCopy())
                        allOfSchemaArrayNode.add(siblingSchemaNode)
                    }
                }

                return resolveReferences(
                    schemaNode = mergedSchemaNode,
                    currentDocumentContext = currentDocumentContext,
                    loadedDocumentContextByPath = loadedDocumentContextByPath,
                    loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri,
                    resolutionStack = resolutionStack
                )
            }

            val resolvedObjectNode = JsonNodeFactory.instance.objectNode()
            val fieldIterator = schemaObjectNode.fields()
            while (fieldIterator.hasNext()) {
                val field = fieldIterator.next()
                resolvedObjectNode.set<JsonNode>(
                    field.key,
                    resolveReferences(
                        schemaNode = field.value,
                        currentDocumentContext = currentDocumentContext,
                        loadedDocumentContextByPath = loadedDocumentContextByPath,
                        loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri,
                        resolutionStack = resolutionStack
                    )
                )
            }
            return resolvedObjectNode
        }

        if (schemaNode.isArray) {
            val resolvedArrayNode = JsonNodeFactory.instance.arrayNode()
            val arrayIterator = schemaNode.elements()
            while (arrayIterator.hasNext()) {
                resolvedArrayNode.add(
                    resolveReferences(
                        schemaNode = arrayIterator.next(),
                        currentDocumentContext = currentDocumentContext,
                        loadedDocumentContextByPath = loadedDocumentContextByPath,
                        loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri,
                        resolutionStack = resolutionStack
                    )
                )
            }
            return resolvedArrayNode
        }

        return schemaNode
    }

    private fun resolveSingleReference(
        referenceValue: String,
        currentDocumentContext: SchemaDocumentContext,
        loadedDocumentContextByPath: MutableMap<Path, SchemaDocumentContext>,
        loadedRemoteDocumentContextByUri: MutableMap<String, SchemaDocumentContext>,
        resolutionStack: MutableSet<String>
    ): JsonNode {
        val referenceKey = "${currentDocumentContext.baseDirectory}:${referenceValue}"
        if (!resolutionStack.add(referenceKey)) {
            throw JsonSchemaGenerationException(
                message = "Recursive reference is not supported: $referenceValue",
                jsonPointer = referenceValue
            )
        }

        try {
            return when {
                referenceValue.startsWith("#") -> {
                    resolveReferenceFragmentInDocumentContext(
                        referenceFragment = referenceValue,
                        currentDocumentContext = currentDocumentContext,
                        loadedDocumentContextByPath = loadedDocumentContextByPath,
                        fragmentValue = referenceValue,
                        loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri,
                        resolutionStack = resolutionStack
                    )
                }

                else -> {
                    val referenceParts = referenceValue.split("#", limit = 2)
                    val referenceTarget = referenceParts.first()
                    val fragmentValue = if (referenceParts.size == 2) "#${referenceParts[1]}" else "#"

                    if (isHttpUrl(referenceTarget)) {
                        val referencedDocumentContext = loadReferencedRemoteDocumentContext(
                            referenceUri = referenceTarget,
                            loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri
                        )
                        resolveReferenceFragmentInDocumentContext(
                            referenceFragment = referenceValue,
                            currentDocumentContext = referencedDocumentContext,
                            loadedDocumentContextByPath = loadedDocumentContextByPath,
                            fragmentValue = fragmentValue,
                            loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri,
                            resolutionStack = resolutionStack
                        )
                    } else if (currentDocumentContext.baseUri != null) {
                        val resolvedRemoteUri = resolveRelativeRemoteReference(
                            referenceTarget = referenceTarget,
                            baseUri = currentDocumentContext.baseUri
                        )
                        val referencedDocumentContext = loadReferencedRemoteDocumentContext(
                            referenceUri = resolvedRemoteUri,
                            loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri
                        )
                        resolveReferenceFragmentInDocumentContext(
                            referenceFragment = referenceValue,
                            currentDocumentContext = referencedDocumentContext,
                            loadedDocumentContextByPath = loadedDocumentContextByPath,
                            fragmentValue = fragmentValue,
                            loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri,
                            resolutionStack = resolutionStack
                        )
                    } else {
                        val referencedDocumentContext = loadReferencedDocumentContext(
                            fileReference = referenceTarget,
                            currentDocumentContext = currentDocumentContext,
                            loadedDocumentContextByPath = loadedDocumentContextByPath
                        )
                        resolveReferenceFragmentInDocumentContext(
                            referenceFragment = referenceValue,
                            currentDocumentContext = referencedDocumentContext,
                            loadedDocumentContextByPath = loadedDocumentContextByPath,
                            fragmentValue = fragmentValue,
                            loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri,
                            resolutionStack = resolutionStack
                        )
                    }
                }
            }
        } finally {
            resolutionStack.remove(referenceKey)
        }
    }

    private fun resolveReferenceFragmentInDocumentContext(
        referenceFragment: String,
        currentDocumentContext: SchemaDocumentContext,
        loadedDocumentContextByPath: MutableMap<Path, SchemaDocumentContext>,
        fragmentValue: String,
        loadedRemoteDocumentContextByUri: MutableMap<String, SchemaDocumentContext>,
        resolutionStack: MutableSet<String>
    ): JsonNode {
        val resolvedFragmentNode = resolveFragmentReferenceWithFallback(
            currentDocumentContext = currentDocumentContext,
            fragmentValue = fragmentValue,
            loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri
        )
        return resolveReferences(
            schemaNode = resolvedFragmentNode,
            currentDocumentContext = currentDocumentContext,
            loadedDocumentContextByPath = loadedDocumentContextByPath,
            loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri,
            resolutionStack = resolutionStack
        )
    }

    private fun resolveFragmentReferenceWithFallback(
        currentDocumentContext: SchemaDocumentContext,
        fragmentValue: String,
        loadedRemoteDocumentContextByUri: MutableMap<String, SchemaDocumentContext>
    ): JsonNode {
        return try {
            resolveFragmentReference(
                rootNode = currentDocumentContext.rootNode,
                anchorNodes = currentDocumentContext.anchorNodes,
                fragmentValue = fragmentValue
            )
        } catch (generationException: JsonSchemaGenerationException) {
            val baseUri = currentDocumentContext.baseUri
            if (baseUri == null || !isInvalidJsonPointerReference(generationException)) {
                throw generationException
            }

            val remoteReferenceFallbackUris = buildRemoteReferenceFallbackUris(baseUri)
            remoteReferenceFallbackUris.forEach { remoteReferenceFallbackUri ->
                try {
                    val referencedDocumentContext = loadReferencedRemoteDocumentContext(
                        referenceUri = remoteReferenceFallbackUri,
                        loadedRemoteDocumentContextByUri = loadedRemoteDocumentContextByUri
                    )
                    return resolveFragmentReference(
                        rootNode = referencedDocumentContext.rootNode,
                        anchorNodes = referencedDocumentContext.anchorNodes,
                        fragmentValue = fragmentValue
                    )
                } catch (_: JsonSchemaGenerationException) {
                    // Try next fallback URI.
                }
            }

            JsonNodeFactory.instance.objectNode()
        }
    }

    private fun buildRemoteReferenceFallbackUris(referenceUri: String): List<String> {
        val referenceUriValue = runCatching { URI(referenceUri) }.getOrNull() ?: return emptyList()
        val scheme = referenceUriValue.scheme ?: return emptyList()
        val host = referenceUriValue.host?.lowercase() ?: return emptyList()
        val path = referenceUriValue.path ?: return emptyList()
        if (!host.contains("schemastore.org")) {
            return emptyList()
        }

        val candidatePaths = mutableListOf(path)
        if (!path.endsWith(".json")) {
            candidatePaths.add("$path.json")
        }

        val candidateHosts = mutableListOf(host)
        if (host == "json.schemastore.org") {
            candidateHosts.add("www.schemastore.org")
        } else if (host == "www.schemastore.org") {
            candidateHosts.add("json.schemastore.org")
        }

        return buildList {
            candidateHosts.forEach { candidateHost ->
                candidatePaths.forEach { candidatePath ->
                    val candidateUri = URI(
                        scheme,
                        referenceUriValue.userInfo,
                        candidateHost,
                        referenceUriValue.port,
                        candidatePath,
                        referenceUriValue.query,
                        null
                    ).toString()
                    if (candidateUri != referenceUri) {
                        add(candidateUri)
                    }
                }
            }
        }.distinct()
    }

    private fun loadReferencedDocumentContext(
        fileReference: String,
        currentDocumentContext: SchemaDocumentContext,
        loadedDocumentContextByPath: MutableMap<Path, SchemaDocumentContext>
    ): SchemaDocumentContext {
        val referencedPath = resolveReferencePath(fileReference, currentDocumentContext.baseDirectory)
        val normalizedReferencedPath = referencedPath.normalize()
        loadedDocumentContextByPath[normalizedReferencedPath]?.let { return it }

        if (!Files.exists(normalizedReferencedPath)) {
            throw JsonSchemaGenerationException(
                message = "Referenced schema file not found: $normalizedReferencedPath",
                jsonPointer = fileReference
            )
        }

        val schemaText = Files.readString(normalizedReferencedPath)
        val referencedRootNode = try {
            strictObjectMapper.readTree(schemaText)
        } catch (exception: Exception) {
            throw JsonSchemaGenerationException(
                message = "Failed to parse referenced schema file: ${exception.message}",
                jsonPointer = fileReference,
                cause = exception
            )
        }

        val documentContext = SchemaDocumentContext(
            rootNode = referencedRootNode,
            anchorNodes = collectAnchorNodes(referencedRootNode),
            baseDirectory = normalizedReferencedPath.parent,
            baseUri = null
        )
        loadedDocumentContextByPath[normalizedReferencedPath] = documentContext
        return documentContext
    }

    private fun loadReferencedRemoteDocumentContext(
        referenceUri: String,
        loadedRemoteDocumentContextByUri: MutableMap<String, SchemaDocumentContext>
    ): SchemaDocumentContext {
        loadedRemoteDocumentContextByUri[referenceUri]?.let { return it }

        val schemaText = fetchRemoteSchemaText(referenceUri)
        val referencedRootNode = try {
            strictObjectMapper.readTree(schemaText)
        } catch (exception: Exception) {
            throw JsonSchemaGenerationException(
                message = "Failed to parse referenced remote schema: ${exception.message}",
                jsonPointer = referenceUri,
                cause = exception
            )
        }

        val documentContext = SchemaDocumentContext(
            rootNode = referencedRootNode,
            anchorNodes = collectAnchorNodes(referencedRootNode),
            baseDirectory = null,
            baseUri = referenceUri
        )
        loadedRemoteDocumentContextByUri[referenceUri] = documentContext
        return documentContext
    }

    private fun fetchRemoteSchemaText(referenceUri: String): String {
        val connection = URL(referenceUri).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/schema+json, application/json;q=0.9, */*;q=0.8")

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Remote schema request failed with status code: $responseCode")
            }
            connection.inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveRelativeRemoteReference(referenceTarget: String, baseUri: String): String {
        return try {
            URI(baseUri).resolve(referenceTarget).toString()
        } catch (exception: Exception) {
            throw JsonSchemaGenerationException(
                message = "Failed to resolve remote reference: $referenceTarget",
                jsonPointer = referenceTarget,
                cause = exception
            )
        }
    }

    private fun isInvalidJsonPointerReference(generationException: JsonSchemaGenerationException): Boolean {
        return generationException.message?.startsWith("Invalid JSON pointer reference:") == true
    }

    private fun isHttpUrl(target: String): Boolean {
        return target.startsWith("http://") || target.startsWith("https://")
    }

    private fun resolveReferencePath(fileReference: String, baseDirectory: Path?): Path {
        val referencePath = Paths.get(fileReference)
        if (referencePath.isAbsolute) {
            return referencePath
        }

        if (baseDirectory != null) {
            return baseDirectory.resolve(referencePath)
        }

        val projectBasePath = project.basePath?.let { Paths.get(it) }
        if (projectBasePath != null) {
            return projectBasePath.resolve(referencePath)
        }

        throw JsonSchemaGenerationException(
            message = "Unable to resolve relative file reference without project base path: $fileReference",
            jsonPointer = fileReference
        )
    }

    private fun resolveFragmentReference(
        rootNode: JsonNode,
        anchorNodes: Map<String, JsonNode>,
        fragmentValue: String
    ): JsonNode {
        if (fragmentValue == "#" || fragmentValue.isBlank()) {
            return rootNode
        }

        if (fragmentValue.startsWith("#/")) {
            val pointerExpression = fragmentValue.removePrefix("#")
            return resolveJsonPointer(rootNode, pointerExpression)
        }

        val anchorName = fragmentValue.removePrefix("#")
        return anchorNodes[anchorName] ?: throw JsonSchemaGenerationException(
            message = "Anchor not found: $fragmentValue",
            jsonPointer = fragmentValue
        )
    }

    private fun resolveJsonPointer(rootNode: JsonNode, pointerExpression: String): JsonNode {
        if (pointerExpression == "") return rootNode
        val tokens = pointerExpression.removePrefix("/").split("/")
        var currentNode: JsonNode = rootNode
        for (token in tokens) {
            val unescapedToken = token
                .replace("~1", "/")
                .replace("~0", "~")
            currentNode = when {
                currentNode.isObject -> resolveObjectToken(currentNode, unescapedToken)
                currentNode.isArray -> {
                    val arrayIndex = unescapedToken.toIntOrNull() ?: -1
                    currentNode.path(arrayIndex)
                }

                else -> JsonNodeFactory.instance.missingNode()
            }

            if (currentNode.isMissingNode) {
                throw JsonSchemaGenerationException(
                    message = "Invalid JSON pointer reference: #$pointerExpression",
                    jsonPointer = "#$pointerExpression"
                )
            }
        }
        return currentNode
    }

    private fun resolveObjectToken(objectNode: JsonNode, token: String): JsonNode {
        val directNode = objectNode.path(token)
        if (!directNode.isMissingNode) {
            return directNode
        }

        val fallbackToken = when (token) {
            "definitions" -> "\$defs"
            "\$defs" -> "definitions"
            "id" -> "\$id"
            "\$id" -> "id"
            else -> null
        } ?: return JsonNodeFactory.instance.missingNode()

        return objectNode.path(fallbackToken)
    }

    private fun collectAnchorNodes(schemaNode: JsonNode): Map<String, JsonNode> {
        val anchorNodesByName = mutableMapOf<String, JsonNode>()
        collectAnchorNodesRecursive(schemaNode, anchorNodesByName)
        return anchorNodesByName
    }

    private fun collectAnchorNodesRecursive(
        schemaNode: JsonNode,
        anchorNodesByName: MutableMap<String, JsonNode>
    ) {
        if (schemaNode.isObject) {
            val schemaObjectNode = schemaNode as ObjectNode
            val anchorName = schemaObjectNode.path("\$anchor").takeIf { it.isTextual }?.asText()
                ?: schemaObjectNode.path("\$dynamicAnchor").takeIf { it.isTextual }?.asText()
            if (!anchorName.isNullOrBlank()) {
                anchorNodesByName[anchorName] = schemaNode
            }
            val fieldIterator = schemaObjectNode.fields()
            while (fieldIterator.hasNext()) {
                collectAnchorNodesRecursive(fieldIterator.next().value, anchorNodesByName)
            }
        } else if (schemaNode.isArray) {
            val arrayIterator = schemaNode.elements()
            while (arrayIterator.hasNext()) {
                collectAnchorNodesRecursive(arrayIterator.next(), anchorNodesByName)
            }
        }
    }

    private fun validateSchemaContradictions(schemaNode: JsonNode, jsonPointer: String) {
        if (!schemaNode.isObject) {
            return
        }

        val minimumLength = schemaNode.path("minLength").takeIf { it.isInt }?.asInt()
        val maximumLength = schemaNode.path("maxLength").takeIf { it.isInt }?.asInt()
        if (minimumLength != null && maximumLength != null && minimumLength > maximumLength) {
            throw JsonSchemaGenerationException(
                message = "Invalid schema: minLength cannot be greater than maxLength.",
                jsonPointer = jsonPointer
            )
        }

        val minimumItems = schemaNode.path("minItems").takeIf { it.isInt }?.asInt()
        val maximumItems = schemaNode.path("maxItems").takeIf { it.isInt }?.asInt()
        if (minimumItems != null && maximumItems != null && minimumItems > maximumItems) {
            throw JsonSchemaGenerationException(
                message = "Invalid schema: minItems cannot be greater than maxItems.",
                jsonPointer = jsonPointer
            )
        }

        val minimumProperties = schemaNode.path("minProperties").takeIf { it.isInt }?.asInt()
        val maximumProperties = schemaNode.path("maxProperties").takeIf { it.isInt }?.asInt()
        if (minimumProperties != null && maximumProperties != null && minimumProperties > maximumProperties) {
            throw JsonSchemaGenerationException(
                message = "Invalid schema: minProperties cannot be greater than maxProperties.",
                jsonPointer = jsonPointer
            )
        }

        val minimumContains = schemaNode.path("minContains").takeIf { it.isInt }?.asInt()
        val maximumContains = schemaNode.path("maxContains").takeIf { it.isInt }?.asInt()
        if (minimumContains != null && maximumContains != null && minimumContains > maximumContains) {
            throw JsonSchemaGenerationException(
                message = "Invalid schema: minContains cannot be greater than maxContains.",
                jsonPointer = jsonPointer
            )
        }

        val minimumValue = schemaNode.path("minimum").takeIf { it.isNumber }?.decimalValue()
        val maximumValue = schemaNode.path("maximum").takeIf { it.isNumber }?.decimalValue()
        val exclusiveMinimumValue = schemaNode.path("exclusiveMinimum").takeIf { it.isNumber }?.decimalValue()
        val exclusiveMaximumValue = schemaNode.path("exclusiveMaximum").takeIf { it.isNumber }?.decimalValue()
        val lowerBound = exclusiveMinimumValue ?: minimumValue
        val upperBound = exclusiveMaximumValue ?: maximumValue
        if (lowerBound != null && upperBound != null && lowerBound > upperBound) {
            throw JsonSchemaGenerationException(
                message = "Invalid schema: lower numeric bound cannot be greater than upper numeric bound.",
                jsonPointer = jsonPointer
            )
        }

        if (schemaNode.path("enum").isArray && schemaNode.path("enum").size() == 0) {
            throw JsonSchemaGenerationException(
                message = "Invalid schema: enum array cannot be empty.",
                jsonPointer = jsonPointer
            )
        }

        val fieldIterator = schemaNode.fields()
        while (fieldIterator.hasNext()) {
            val field = fieldIterator.next()
            when {
                field.value.isObject -> {
                    validateSchemaContradictions(field.value, buildJsonPointer(jsonPointer, field.key))
                }

                field.value.isArray -> {
                    val schemaArrayNode = field.value as ArrayNode
                    for (index in 0 until schemaArrayNode.size()) {
                        if (schemaArrayNode[index].isObject) {
                            validateSchemaContradictions(
                                schemaArrayNode[index],
                                buildJsonPointer(jsonPointer, "$index")
                            )
                        }
                    }
                }
            }
        }
    }

    fun createConstraint(schemaNode: JsonNode, jsonPointer: String): JsonSchemaConstraint {
        if (schemaNode.isBoolean) {
            return BooleanLiteralSchemaConstraint(
                jsonPointer = jsonPointer,
                schemaNode = schemaNode,
                allowsAllValues = schemaNode.booleanValue()
            )
        }

        val possibleTypeNames = extractTypeNames(schemaNode)
        val hasCompositeKeywords = schemaNode.has("allOf") ||
            schemaNode.has("anyOf") ||
            schemaNode.has("oneOf") ||
            schemaNode.has("not") ||
            schemaNode.has("if")

        if (hasCompositeKeywords || possibleTypeNames.size > 1) {
            return CompositeSchemaConstraint(
                jsonPointer = jsonPointer,
                schemaNode = schemaNode,
                allOfConstraints = normalizeKeywordArray(schemaNode, "allOf", jsonPointer),
                anyOfConstraints = normalizeKeywordArray(schemaNode, "anyOf", jsonPointer),
                oneOfConstraints = normalizeKeywordArray(schemaNode, "oneOf", jsonPointer),
                notConstraint = schemaNode.path("not")
                    .takeIf { it.isObject || it.isBoolean }
                    ?.let { createConstraint(it, buildJsonPointer(jsonPointer, "not")) },
                ifConstraint = schemaNode.path("if")
                    .takeIf { it.isObject || it.isBoolean }
                    ?.let { createConstraint(it, buildJsonPointer(jsonPointer, "if")) },
                thenConstraint = schemaNode.path("then")
                    .takeIf { it.isObject || it.isBoolean }
                    ?.let { createConstraint(it, buildJsonPointer(jsonPointer, "then")) },
                elseConstraint = schemaNode.path("else")
                    .takeIf { it.isObject || it.isBoolean }
                    ?.let { createConstraint(it, buildJsonPointer(jsonPointer, "else")) },
                baseConstraint = createSimpleConstraint(schemaNode, jsonPointer, possibleTypeNames.firstOrNull()),
                possibleTypeNames = possibleTypeNames
            )
        }

        return createSimpleConstraint(schemaNode, jsonPointer, possibleTypeNames.firstOrNull())
    }

    private fun normalizeKeywordArray(
        schemaNode: JsonNode,
        keyword: String,
        jsonPointer: String
    ): List<JsonSchemaConstraint> {
        val keywordArrayNode = schemaNode.path(keyword)
        if (!keywordArrayNode.isArray) return emptyList()
        return keywordArrayNode.mapIndexed { index, elementNode ->
            createConstraint(elementNode, buildJsonPointer(jsonPointer, "$keyword/$index"))
        }
    }

    private fun createSimpleConstraint(
        schemaNode: JsonNode,
        jsonPointer: String,
        explicitTypeName: String?
    ): JsonSchemaConstraint {
        val typeName = explicitTypeName ?: inferTypeName(schemaNode)

        return when (typeName) {
            "object" -> createObjectConstraint(schemaNode, jsonPointer)
            "array" -> createArrayConstraint(schemaNode, jsonPointer)
            "string" -> createStringConstraint(schemaNode, jsonPointer)
            "number" -> createNumberConstraint(schemaNode, jsonPointer)
            "integer" -> createIntegerConstraint(schemaNode, jsonPointer)
            "boolean" -> BooleanSchemaConstraint(jsonPointer, schemaNode)
            "null" -> NullSchemaConstraint(jsonPointer, schemaNode)
            else -> AnySchemaConstraint(jsonPointer, schemaNode)
        }
    }

    private fun createObjectConstraint(schemaNode: JsonNode, jsonPointer: String): ObjectSchemaConstraint {
        val propertyConstraints = mutableMapOf<String, JsonSchemaConstraint>()
        schemaNode.path("properties")
            .takeIf { it.isObject }
            ?.fields()
            ?.forEachRemaining { field ->
                propertyConstraints[field.key] = createConstraint(
                    field.value,
                    buildJsonPointer(jsonPointer, "properties/${field.key}")
                )
            }

        val patternPropertyConstraints = mutableMapOf<String, JsonSchemaConstraint>()
        schemaNode.path("patternProperties")
            .takeIf { it.isObject }
            ?.fields()
            ?.forEachRemaining { field ->
                patternPropertyConstraints[field.key] = createConstraint(
                    field.value,
                    buildJsonPointer(jsonPointer, "patternProperties/${field.key}")
                )
            }

        val requiredProperties = schemaNode.path("required")
            .takeIf { it.isArray }
            ?.mapNotNull { requiredPropertyNode -> requiredPropertyNode.takeIf { it.isTextual }?.asText() }
            ?.toSet()
            ?: emptySet()

        val additionalPropertiesNode = schemaNode.path("additionalProperties")
        val additionalPropertiesConstraint =
            if (additionalPropertiesNode.isObject || additionalPropertiesNode.isBoolean) {
                createConstraint(additionalPropertiesNode, buildJsonPointer(jsonPointer, "additionalProperties"))
            } else {
                null
            }

        val unevaluatedPropertiesNode = schemaNode.path("unevaluatedProperties")
        val unevaluatedPropertiesConstraint =
            if (unevaluatedPropertiesNode.isObject || unevaluatedPropertiesNode.isBoolean) {
                createConstraint(unevaluatedPropertiesNode, buildJsonPointer(jsonPointer, "unevaluatedProperties"))
            } else {
                null
            }

        val dependentRequiredProperties = mutableMapOf<String, Set<String>>()
        schemaNode.path("dependentRequired")
            .takeIf { it.isObject }
            ?.fields()
            ?.forEachRemaining { field ->
                val dependentPropertyNames = field.value
                    .takeIf { it.isArray }
                    ?.mapNotNull { dependentPropertyNode -> dependentPropertyNode.takeIf { it.isTextual }?.asText() }
                    ?.toSet()
                    ?: emptySet()
                dependentRequiredProperties[field.key] = dependentPropertyNames
            }

        val dependentSchemaConstraints = mutableMapOf<String, JsonSchemaConstraint>()
        schemaNode.path("dependentSchemas")
            .takeIf { it.isObject }
            ?.fields()
            ?.forEachRemaining { field ->
                dependentSchemaConstraints[field.key] = createConstraint(
                    field.value,
                    buildJsonPointer(jsonPointer, "dependentSchemas/${field.key}")
                )
            }

        return ObjectSchemaConstraint(
            jsonPointer = jsonPointer,
            schemaNode = schemaNode,
            propertyConstraints = propertyConstraints,
            requiredProperties = requiredProperties,
            patternPropertyConstraints = patternPropertyConstraints,
            additionalPropertiesConstraint = additionalPropertiesConstraint,
            allowsAdditionalProperties = !additionalPropertiesNode.isBoolean || additionalPropertiesNode.booleanValue(),
            unevaluatedPropertiesConstraint = unevaluatedPropertiesConstraint,
            allowsUnevaluatedProperties = !unevaluatedPropertiesNode.isBoolean || unevaluatedPropertiesNode.booleanValue(),
            dependentRequiredProperties = dependentRequiredProperties,
            dependentSchemaConstraints = dependentSchemaConstraints,
            minimumProperties = schemaNode.path("minProperties").takeIf { it.isInt }?.asInt(),
            maximumProperties = schemaNode.path("maxProperties").takeIf { it.isInt }?.asInt()
        )
    }

    private fun createArrayConstraint(schemaNode: JsonNode, jsonPointer: String): ArraySchemaConstraint {
        val prefixItemConstraints = schemaNode.path("prefixItems")
            .takeIf { it.isArray }
            ?.mapIndexed { index, itemSchemaNode ->
                createConstraint(itemSchemaNode, buildJsonPointer(jsonPointer, "prefixItems/$index"))
            }
            ?: emptyList()

        val itemSchemaNode = schemaNode.path("items")
        val itemConstraint = if (itemSchemaNode.isObject || itemSchemaNode.isBoolean) {
            createConstraint(itemSchemaNode, buildJsonPointer(jsonPointer, "items"))
        } else {
            null
        }

        val containsSchemaNode = schemaNode.path("contains")
        val containsConstraint = if (containsSchemaNode.isObject || containsSchemaNode.isBoolean) {
            createConstraint(containsSchemaNode, buildJsonPointer(jsonPointer, "contains"))
        } else {
            null
        }

        val unevaluatedItemsNode = schemaNode.path("unevaluatedItems")
        val unevaluatedItemsConstraint = if (unevaluatedItemsNode.isObject || unevaluatedItemsNode.isBoolean) {
            createConstraint(unevaluatedItemsNode, buildJsonPointer(jsonPointer, "unevaluatedItems"))
        } else {
            null
        }

        return ArraySchemaConstraint(
            jsonPointer = jsonPointer,
            schemaNode = schemaNode,
            prefixItemConstraints = prefixItemConstraints,
            itemConstraint = itemConstraint,
            containsConstraint = containsConstraint,
            minimumItems = schemaNode.path("minItems").takeIf { it.isInt }?.asInt(),
            maximumItems = schemaNode.path("maxItems").takeIf { it.isInt }?.asInt(),
            uniqueItems = schemaNode.path("uniqueItems").takeIf { it.isBoolean }?.booleanValue() ?: false,
            minimumContains = schemaNode.path("minContains").takeIf { it.isInt }?.asInt(),
            maximumContains = schemaNode.path("maxContains").takeIf { it.isInt }?.asInt(),
            unevaluatedItemsConstraint = unevaluatedItemsConstraint,
            allowsUnevaluatedItems = !unevaluatedItemsNode.isBoolean || unevaluatedItemsNode.booleanValue()
        )
    }

    private fun createStringConstraint(schemaNode: JsonNode, jsonPointer: String): StringSchemaConstraint {
        return StringSchemaConstraint(
            jsonPointer = jsonPointer,
            schemaNode = schemaNode,
            minimumLength = schemaNode.path("minLength").takeIf { it.isInt }?.asInt(),
            maximumLength = schemaNode.path("maxLength").takeIf { it.isInt }?.asInt(),
            patternExpression = schemaNode.path("pattern").takeIf { it.isTextual }?.asText(),
            formatName = schemaNode.path("format").takeIf { it.isTextual }?.asText()
        )
    }

    private fun createNumberConstraint(schemaNode: JsonNode, jsonPointer: String): NumberSchemaConstraint {
        return NumberSchemaConstraint(
            jsonPointer = jsonPointer,
            schemaNode = schemaNode,
            minimumValue = schemaNode.path("minimum").takeIf { it.isNumber }?.decimalValue(),
            maximumValue = schemaNode.path("maximum").takeIf { it.isNumber }?.decimalValue(),
            exclusiveMinimumValue = schemaNode.path("exclusiveMinimum").takeIf { it.isNumber }?.decimalValue(),
            exclusiveMaximumValue = schemaNode.path("exclusiveMaximum").takeIf { it.isNumber }?.decimalValue(),
            multipleOfValue = schemaNode.path("multipleOf").takeIf { it.isNumber }?.decimalValue()
        )
    }

    private fun createIntegerConstraint(schemaNode: JsonNode, jsonPointer: String): IntegerSchemaConstraint {
        return IntegerSchemaConstraint(
            jsonPointer = jsonPointer,
            schemaNode = schemaNode,
            minimumValue = schemaNode.path("minimum").takeIf { it.isNumber }?.decimalValue(),
            maximumValue = schemaNode.path("maximum").takeIf { it.isNumber }?.decimalValue(),
            exclusiveMinimumValue = schemaNode.path("exclusiveMinimum").takeIf { it.isNumber }?.decimalValue(),
            exclusiveMaximumValue = schemaNode.path("exclusiveMaximum").takeIf { it.isNumber }?.decimalValue(),
            multipleOfValue = schemaNode.path("multipleOf").takeIf { it.isNumber }?.decimalValue()
        )
    }

    private fun extractTypeNames(schemaNode: JsonNode): List<String> {
        val typeNode = schemaNode.path("type")
        if (typeNode.isTextual) {
            return listOf(typeNode.asText())
        }
        if (typeNode.isArray) {
            return typeNode
                .mapNotNull { typeElementNode -> typeElementNode.takeIf { it.isTextual }?.asText() }
        }
        return emptyList()
    }

    private fun inferTypeName(schemaNode: JsonNode): String? {
        if (schemaNode.has("properties") || schemaNode.has("required") || schemaNode.has("dependentRequired")) {
            return "object"
        }
        if (schemaNode.has("items") || schemaNode.has("prefixItems") || schemaNode.has("contains")) {
            return "array"
        }
        if (schemaNode.has("minLength") || schemaNode.has("maxLength") || schemaNode.has("pattern")) {
            return "string"
        }
        if (schemaNode.has("minimum") || schemaNode.has("maximum") || schemaNode.has("multipleOf")) {
            return if (schemaNode.path("type").asText() == "integer") "integer" else "number"
        }

        val constNode = schemaNode.path("const")
        if (!constNode.isMissingNode) {
            return when {
                constNode.isTextual -> "string"
                constNode.isIntegralNumber -> "integer"
                constNode.isNumber -> "number"
                constNode.isBoolean -> "boolean"
                constNode.isArray -> "array"
                constNode.isObject -> "object"
                constNode.isNull -> "null"
                else -> null
            }
        }

        return null
    }

    private fun buildJsonPointer(parentPointer: String, nextToken: String): String {
        val escapedToken = nextToken
            .replace("~", "~0")
            .replace("/", "~1")
        return if (parentPointer == "#") {
            "#/$escapedToken"
        } else {
            "$parentPointer/$escapedToken"
        }
    }

    @Suppress("unused")
    private fun asBigDecimal(numberNode: JsonNode): BigDecimal? {
        return if (numberNode.isNumber) numberNode.decimalValue() else null
    }
}
