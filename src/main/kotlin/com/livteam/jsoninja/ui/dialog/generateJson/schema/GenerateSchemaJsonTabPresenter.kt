package com.livteam.jsoninja.ui.dialog.generateJson.schema

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.JsonToken
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationService
import com.livteam.jsoninja.services.schema.JsonSchemaGenerationException
import com.livteam.jsoninja.services.schema.SchemaStoreCatalogService
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import com.livteam.jsoninja.utils.JsonHelperUtils
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.JComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GenerateSchemaJsonTabPresenter(
    private val project: Project,
    private val initialConfig: JsonGenerationConfig = JsonGenerationConfig(generationMode = JsonGenerationMode.SCHEMA),
    initialSchemaUrlText: String = "",
) {
    private val LOG = logger<GenerateSchemaJsonTabPresenter>()
    private val view = GenerateSchemaJsonTabView(project, initialConfig, initialSchemaUrlText)
    private val schemaStoreCatalogService = service<SchemaStoreCatalogService>()
    private val schemaDataGenerationService = project.getService(JsonSchemaDataGenerationService::class.java)
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()

    @Volatile
    private var isDisposed = false

    private var schemaStoreCatalogItems: List<SchemaStoreCatalogItem> = emptyList()
    private var schemaStoreCatalogState = SchemaStoreCatalogState.LOADING
    private var hasStartedCatalogLoad = false
    private var schemaStoreFilterJob: Job? = null
    private var schemaUrlLoadJob: Job? = null
    private var schemaUrlLoadSequence = 0L
    private var schemaRetrievalUri: String? = initialConfig.schemaRetrievalUri

    init {
        view.setOnSchemaUrlInputChanged {
            view.clearUrlError()
            if (!hasStartedCatalogLoad || schemaStoreCatalogState == SchemaStoreCatalogState.FAILED) {
                ensureSchemaStoreCatalogLoaded()
            }
            filterSchemaStoreCatalogItemsByInput()
        }
        view.setOnLoadSchemaFromUrlRequested { handleLoadOrCancelSchemaFromUrl() }
        view.setOnLoadSchemaFromFileRequested { loadSchemaFromFile() }
        view.setOnLoadSchemaFromTabRequested { loadSchemaFromCurrentTab() }
        view.setOnSchemaSourceReplaced { schemaRetrievalUri = null }
    }

    fun getComponent(): JComponent {
        return view.component
    }

    fun setOnOptionsChanged(callback: () -> Unit) = view.setOnOptionsChanged(callback)

    fun setActive(isActive: Boolean) {
        view.setActive(isActive)
        if (isActive) {
            ensureSchemaStoreCatalogLoaded()
        }
    }

    fun registerValidators(parentDisposable: Disposable) = view.registerValidators(parentDisposable)

    fun getPreferredFocusedComponent(): JComponent = view.getSchemaInputComponent()

    fun getSummary(): String {
        val outputCount = view.getSchemaOutputCountText().toIntOrNull()
        if (outputCount == null || outputCount !in 1..100) {
            return LocalizationBundle.message("dialog.generate.json.summary.invalid")
        }
        val outputFormat = if (view.isJson5Selected()) "JSON5" else "JSON"
        return if (outputCount == 1) {
            LocalizationBundle.message("dialog.generate.json.summary.schema.single", outputFormat)
        } else {
            LocalizationBundle.message("dialog.generate.json.summary.schema.multiple", outputCount, outputFormat)
        }
    }

    fun validate(): ValidationInfo? {
        val schemaOutputCount = view.getSchemaOutputCountText().toIntOrNull()
        if (schemaOutputCount == null || schemaOutputCount !in 1..100) {
            return ValidationInfo(
                LocalizationBundle.message("dialog.generate.json.validation.integer.range", 1, 100),
                view.getSchemaOutputCountField()
            )
        }

        if (view.hasPendingSchemaStoreSelectionLoad()) {
            val warningMessage = LocalizationBundle.message("validation.error.schema.store.load.required")
            return ValidationInfo(warningMessage, view.getSchemaUrlInputComponent())
        }

        val rawSchemaText = view.getSchemaText()
        val schemaText = rawSchemaText.trim()
        if (schemaText.isBlank()) {
            return ValidationInfo(
                LocalizationBundle.message("validation.error.schema.required"),
                view.getSchemaInputComponent()
            )
        }

        if (schemaDataGenerationService == null) {
            return ValidationInfo(
                LocalizationBundle.message("validation.error.schema.service.unavailable"),
                view.getSchemaInputComponent()
            )
        }

        return try {
            schemaDataGenerationService.validateSchemaText(schemaText)
            null
        } catch (generationException: JsonSchemaGenerationException) {
            resolveErrorOffset(rawSchemaText, generationException.jsonPointer, generationException.cause)
                ?.let(view::navigateToOffset)
            val pointerSuffix = generationException.jsonPointer
                ?.takeIf { it.isNotBlank() && it != "#" }
                ?.let { " ($it)" }
                .orEmpty()
            ValidationInfo(
                (generationException.message ?: LocalizationBundle.message("validation.error.schema.invalid")) + pointerSuffix,
                view.getSchemaInputComponent()
            )
        } catch (exception: Exception) {
            ValidationInfo(
                LocalizationBundle.message("validation.error.schema.invalid"),
                view.getSchemaInputComponent()
            )
        }
    }

    fun getCurrentSessionConfig(): JsonGenerationConfig {
        val schemaPropertyGenerationMode = view.getSchemaPropertyGenerationMode()
        val isCommentedMode = schemaPropertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
        return JsonGenerationConfig(
            generationMode = JsonGenerationMode.SCHEMA,
            isJson5 = view.isJson5Selected() || isCommentedMode,
            schemaText = view.getSchemaText(),
            schemaOutputCount = view.getSchemaOutputCountText().toIntOrNull()?.coerceIn(1, 100)
                ?: initialConfig.schemaOutputCount,
            schemaPropertyGenerationMode = schemaPropertyGenerationMode,
            schemaRetrievalUri = schemaRetrievalUri
        )
    }

    fun getCurrentSchemaUrlText(): String = view.getSchemaUrlEditorText()

    fun getConfig(): JsonGenerationConfig {
        val schemaPropertyGenerationMode = view.getSchemaPropertyGenerationMode()
        val isCommentedMode = schemaPropertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
        return JsonGenerationConfig(
            generationMode = JsonGenerationMode.SCHEMA,
            isJson5 = view.isJson5Selected() || isCommentedMode,
            schemaText = view.getSchemaText(),
            schemaOutputCount = view.getSchemaOutputCountText().toInt(),
            schemaPropertyGenerationMode = schemaPropertyGenerationMode,
            schemaRetrievalUri = schemaRetrievalUri
        )
    }

    fun dispose() {
        isDisposed = true
        schemaStoreFilterJob?.cancel()
        schemaUrlLoadJob?.cancel()
        coroutineScope.cancel()
        view.dispose()
    }

    private fun ensureSchemaStoreCatalogLoaded() {
        if (isDisposed) return
        if (schemaStoreCatalogState == SchemaStoreCatalogState.READY && schemaStoreCatalogItems.isNotEmpty()) {
            return
        }
        schemaStoreCatalogService.getCachedCatalogOrNull()?.let { cachedItems ->
            hasStartedCatalogLoad = true
            schemaStoreCatalogItems = cachedItems
            schemaStoreCatalogState = SchemaStoreCatalogState.READY
            filterSchemaStoreCatalogItemsByInput()
            return
        }
        if (hasStartedCatalogLoad && schemaStoreCatalogState == SchemaStoreCatalogState.LOADING) {
            return
        }
        loadSchemaStoreCatalog()
    }

    private fun loadSchemaStoreCatalog() {
        hasStartedCatalogLoad = true
        schemaStoreCatalogState = SchemaStoreCatalogState.LOADING
        view.updateSchemaUrlSuggestions(
            schemaUrlSuggestionItems = listOf(
                SchemaUrlComboBoxItem.StatusEntry(
                    LocalizationBundle.message("dialog.generate.json.schema.store.loading")
                )
            ),
            editorText = view.getSchemaUrlEditorText(),
            showPopupWhenAvailable = false
        )

        coroutineScope.launch {
            if (isDisposed) return@launch
            try {
                val parsedSchemaStoreCatalogItems = schemaStoreCatalogService.getCatalog()

                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (isDisposed) return@withContext
                    schemaStoreCatalogItems = parsedSchemaStoreCatalogItems
                    schemaStoreCatalogState = SchemaStoreCatalogState.READY
                    filterSchemaStoreCatalogItemsByInput()
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (cancellationException: ProcessCanceledException) {
                throw cancellationException
            } catch (exception: Exception) {
                LOG.warn("Failed to load SchemaStore catalog.", exception)
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (isDisposed) return@withContext
                    schemaStoreCatalogItems = emptyList()
                    schemaStoreCatalogState = SchemaStoreCatalogState.FAILED
                    view.updateSchemaUrlSuggestions(
                        schemaUrlSuggestionItems = listOf(
                            SchemaUrlComboBoxItem.StatusEntry(
                                LocalizationBundle.message("dialog.generate.json.schema.store.unavailable")
                            )
                        ),
                        editorText = view.getSchemaUrlEditorText(),
                        showPopupWhenAvailable = false
                    )
                }
            }
        }
    }

    private fun filterSchemaStoreCatalogItemsByInput() {
        if (schemaStoreCatalogState != SchemaStoreCatalogState.READY || isDisposed) {
            return
        }

        val editorText = view.getSchemaUrlEditorText()
        val catalogItems = schemaStoreCatalogItems
        schemaStoreFilterJob?.cancel()
        schemaStoreFilterJob = coroutineScope.launch {
            delay(SCHEMA_STORE_FILTER_DEBOUNCE_MS)

            // 무거운 필터링(토큰 분해 + Levenshtein + 정렬)은 Default에서 계산하고, EDT에는 UI 반영만 남긴다.
            val filteredSchemaStoreCatalogItems = withContext(Dispatchers.Default) {
                filterSchemaStoreCatalogItems(catalogItems, editorText)
            }

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                if (isDisposed) return@withContext
                if (schemaStoreCatalogState != SchemaStoreCatalogState.READY) return@withContext
                if (view.getSchemaUrlEditorText() != editorText) return@withContext

                if (filteredSchemaStoreCatalogItems.isEmpty()) {
                    view.updateSchemaUrlSuggestions(
                        schemaUrlSuggestionItems = listOf(
                            SchemaUrlComboBoxItem.StatusEntry(
                                LocalizationBundle.message("dialog.generate.json.schema.store.no.match")
                            )
                        ),
                        editorText = editorText,
                        showPopupWhenAvailable = true
                    )
                    return@withContext
                }

                view.updateSchemaUrlSuggestions(
                    schemaUrlSuggestionItems = filteredSchemaStoreCatalogItems.map { schemaStoreCatalogItem ->
                        SchemaUrlComboBoxItem.CatalogEntry(schemaStoreCatalogItem)
                    },
                    editorText = editorText,
                    showPopupWhenAvailable = true
                )
            }
        }
    }

    private fun filterSchemaStoreCatalogItems(
        catalogItems: List<SchemaStoreCatalogItem>,
        editorText: String
    ): List<SchemaStoreCatalogItem> {
        val normalizedFilterKeyword = editorText.trim().lowercase()
        return if (normalizedFilterKeyword.isBlank()) {
            catalogItems
        } else {
            catalogItems
                .mapNotNull { schemaStoreCatalogItem ->
                    calculateSearchScore(schemaStoreCatalogItem, normalizedFilterKeyword)?.let { searchScore ->
                        schemaStoreCatalogItem to searchScore
                    }
                }
                .sortedWith(
                    compareByDescending<Pair<SchemaStoreCatalogItem, Int>> { it.second }
                        .thenBy { it.first.name.length }
                        .thenBy { it.first.name.lowercase() }
                )
                .map { it.first }
        }
    }

    private fun calculateSearchScore(
        schemaStoreCatalogItem: SchemaStoreCatalogItem,
        searchKeyword: String
    ): Int? {
        val normalizedName = schemaStoreCatalogItem.name.lowercase()
        val normalizedUrl = schemaStoreCatalogItem.url.lowercase()

        var bestSearchScore = 0

        if (normalizedName == searchKeyword) {
            bestSearchScore = maxOf(bestSearchScore, 10_000)
        }

        if (normalizedUrl == searchKeyword) {
            bestSearchScore = maxOf(bestSearchScore, 9_500)
        }

        if (normalizedName.startsWith(searchKeyword)) {
            bestSearchScore = maxOf(bestSearchScore, 9_000)
        }

        if (normalizedUrl.startsWith(searchKeyword)) {
            bestSearchScore = maxOf(bestSearchScore, 8_500)
        }

        val nameContainsPosition = normalizedName.indexOf(searchKeyword)
        if (nameContainsPosition >= 0) {
            bestSearchScore = maxOf(bestSearchScore, 8_000 - nameContainsPosition.coerceAtMost(500))
        }

        val urlContainsPosition = normalizedUrl.indexOf(searchKeyword)
        if (urlContainsPosition >= 0) {
            bestSearchScore = maxOf(bestSearchScore, 7_500 - urlContainsPosition.coerceAtMost(500))
        }

        val fuzzyNameSearchScore = calculateBestTokenSimilarityScore(
            text = normalizedName,
            searchKeyword = searchKeyword,
            baseScore = 6_000
        )
        if (fuzzyNameSearchScore > 0) {
            bestSearchScore = maxOf(bestSearchScore, fuzzyNameSearchScore)
        }

        val fuzzyUrlSearchScore = calculateBestTokenSimilarityScore(
            text = normalizedUrl,
            searchKeyword = searchKeyword,
            baseScore = 5_500
        )
        if (fuzzyUrlSearchScore > 0) {
            bestSearchScore = maxOf(bestSearchScore, fuzzyUrlSearchScore)
        }

        return bestSearchScore.takeIf { it > 0 }
    }

    private fun calculateBestTokenSimilarityScore(
        text: String,
        searchKeyword: String,
        baseScore: Int
    ): Int {
        if (searchKeyword.length < MINIMUM_SEARCH_LENGTH_FOR_FUZZY_MATCHING) {
            return 0
        }

        val searchTokens = extractSearchTokens(text)
        if (searchTokens.isEmpty()) {
            return 0
        }

        var bestSimilaritySearchScore = 0
        searchTokens.forEach { searchToken ->
            val maximumLength = maxOf(searchToken.length, searchKeyword.length)
            if (maximumLength == 0) {
                return@forEach
            }

            val editDistance = calculateLevenshteinDistance(searchToken, searchKeyword)
            val similarityRatio = 1.0 - (editDistance.toDouble() / maximumLength.toDouble())
            if (similarityRatio >= MINIMUM_TOKEN_SIMILARITY) {
                val similaritySearchScore = baseScore + (similarityRatio * 1_000).toInt()
                bestSimilaritySearchScore = maxOf(bestSimilaritySearchScore, similaritySearchScore)
            }
        }

        return bestSimilaritySearchScore
    }

    private fun extractSearchTokens(text: String): List<String> {
        return text.split(SEARCH_TOKEN_SEPARATOR_REGEX)
            .map { rawToken -> rawToken.trim() }
            .filter { token -> token.isNotBlank() }
    }

    private fun calculateLevenshteinDistance(firstText: String, secondText: String): Int {
        if (firstText == secondText) {
            return 0
        }
        if (firstText.isEmpty()) {
            return secondText.length
        }
        if (secondText.isEmpty()) {
            return firstText.length
        }

        var previousDistanceRow = IntArray(secondText.length + 1) { index -> index }
        var currentDistanceRow = IntArray(secondText.length + 1)

        for (firstTextIndex in 1..firstText.length) {
            currentDistanceRow[0] = firstTextIndex
            for (secondTextIndex in 1..secondText.length) {
                val substitutionCost = if (firstText[firstTextIndex - 1] == secondText[secondTextIndex - 1]) 0 else 1
                currentDistanceRow[secondTextIndex] = minOf(
                    currentDistanceRow[secondTextIndex - 1] + 1,
                    previousDistanceRow[secondTextIndex] + 1,
                    previousDistanceRow[secondTextIndex - 1] + substitutionCost
                )
            }

            val nextPreviousDistanceRow = previousDistanceRow
            previousDistanceRow = currentDistanceRow
            currentDistanceRow = nextPreviousDistanceRow
        }

        return previousDistanceRow[secondText.length]
    }

    private fun handleLoadOrCancelSchemaFromUrl() {
        val activeJob = schemaUrlLoadJob
        if (activeJob != null && activeJob.isActive) {
            schemaUrlLoadSequence++
            activeJob.cancel()
            schemaUrlLoadJob = null
            view.setUrlLoading(false)
            return
        }
        loadSchemaFromUrl()
    }

    private fun loadSchemaFromUrl() {
        view.clearUrlError()
        val schemaUrl = view.getSchemaUrlInputText()
        if (schemaUrl.isBlank()) {
            showSchemaUrlError(LocalizationBundle.message("dialog.generate.json.schema.url.empty"))
            return
        }

        if (!schemaUrl.startsWith("http://") && !schemaUrl.startsWith("https://")) {
            showSchemaUrlError(LocalizationBundle.message("dialog.generate.json.schema.url.invalid"))
            return
        }

        val loadSequence = ++schemaUrlLoadSequence
        schemaUrlLoadJob?.cancel()
        view.setUrlLoading(true)

        schemaUrlLoadJob = coroutineScope.launch {
            if (isDisposed) return@launch
            try {
                val fetchedSchemaText = withContext(Dispatchers.IO) {
                    fetchSchemaText(schemaUrl)
                }
                if (fetchedSchemaText.trim().isEmpty()) {
                    throw IOException(LocalizationBundle.message("dialog.generate.json.schema.url.fetch.empty"))
                }

                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (isDisposed || loadSequence != schemaUrlLoadSequence) return@withContext
                    setSchemaEditorText(fetchedSchemaText)
                    schemaRetrievalUri = schemaUrl
                    view.markSchemaStoreSelectionLoaded()
                    view.clearUrlError()
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (cancellationException: ProcessCanceledException) {
                throw cancellationException
            } catch (exception: Exception) {
                val message = exception.message
                    ?: LocalizationBundle.message("dialog.generate.json.schema.url.fetch.failed")
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (isDisposed || loadSequence != schemaUrlLoadSequence) return@withContext
                    showSchemaUrlError(message)
                }
            } finally {
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (isDisposed || loadSequence != schemaUrlLoadSequence) return@withContext
                    view.setUrlLoading(false)
                }
            }
        }
    }

    private fun loadSchemaFromFile() {
        view.clearUrlError()
        val descriptor = FileChooserDescriptor(
            true,
            false,
            false,
            false,
            false,
            false,
        ).withFileFilter { file ->
            val lower = file.name.lowercase()
            lower.endsWith(".json") || lower.endsWith(".json5")
        }

        FileChooser.chooseFile(descriptor, project, null) { virtualFile ->
            if (isDisposed || project.isDisposed) return@chooseFile
            coroutineScope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        String(virtualFile.contentsToByteArray(), virtualFile.charset)
                    }
                    val fileUri = runCatching { virtualFile.toNioPath().toUri().toString() }
                        .getOrElse { virtualFile.url }
                    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                        if (isDisposed || project.isDisposed) return@withContext
                        setSchemaEditorText(content)
                        schemaRetrievalUri = fileUri
                        view.clearUrlError()
                    }
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (cancellationException: ProcessCanceledException) {
                    throw cancellationException
                } catch (exception: Exception) {
                    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                        if (isDisposed || project.isDisposed) return@withContext
                        showSchemaUrlError(
                            exception.message ?: LocalizationBundle.message("dialog.generate.json.schema.url.fetch.failed")
                        )
                    }
                }
            }
        }
    }

    private fun loadSchemaFromCurrentTab() {
        view.clearUrlError()
        val currentJson = JsonHelperUtils.getCurrentJsonFromToolWindow(project)?.trim()
        if (currentJson.isNullOrBlank()) {
            showSchemaUrlError(LocalizationBundle.message("dialog.generate.json.schema.load.tab.empty"))
            return
        }
        setSchemaEditorText(currentJson)
        schemaRetrievalUri = null
    }

    private suspend fun fetchSchemaText(schemaUrl: String): String {
        val connection = URI(schemaUrl).toURL().openConnection() as HttpURLConnection
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                runCatching { connection.disconnect() }
            }
        }
        return try {
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
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private fun resolveErrorOffset(schemaText: String, jsonPointer: String?, cause: Throwable?): Int? {
        val processingException = cause as? JsonProcessingException
        val location = processingException?.location
        if (location != null) {
            if (location.charOffset >= 0) {
                return location.charOffset.toInt()
            }
            if (location.lineNr > 0) {
                return lineColumnToOffset(schemaText, location.lineNr, location.columnNr.coerceAtLeast(1))
            }
        }
        return findJsonPointerOffset(schemaText, jsonPointer)
    }

    private fun findJsonPointerOffset(schemaText: String, jsonPointer: String?): Int? {
        val normalized = jsonPointer?.trim()?.removePrefix("#") ?: return null
        if (normalized.isEmpty() || normalized == "/") return 0
        if (!normalized.startsWith("/")) return null

        val targetSegments = normalized.substring(1).split("/").map { segment ->
            segment.replace("~1", "/").replace("~0", "~")
        }
        val mapper = service<JsonObjectMapperService>().standardObjectMapper
        return runCatching {
            mapper.factory.createParser(schemaText).use { parser ->
                val containerTypes = ArrayDeque<Boolean>() // true = object, false = array
                val pathSegments = ArrayDeque<String>()
                val arrayIndices = ArrayDeque<Int>()
                var pendingFieldName: String? = null

                while (true) {
                    val token = parser.nextToken() ?: break
                    when (token) {
                        JsonToken.FIELD_NAME -> {
                            pendingFieldName = parser.currentName()
                            val candidatePath = pathSegments.toList() + pendingFieldName
                            if (candidatePath == targetSegments) {
                                return@use parser.currentTokenLocation().charOffset.toInt().coerceAtLeast(0)
                            }
                        }

                        JsonToken.START_OBJECT, JsonToken.START_ARRAY -> {
                            val pushed = enterValue(containerTypes, pathSegments, arrayIndices, pendingFieldName)
                            pendingFieldName = null
                            if (pathSegments.toList() == targetSegments) {
                                return@use parser.currentTokenLocation().charOffset.toInt().coerceAtLeast(0)
                            }
                            val isObject = token == JsonToken.START_OBJECT
                            containerTypes.addLast(isObject)
                            if (!isObject) {
                                arrayIndices.addLast(0)
                            }
                            if (!pushed && containerTypes.size == 1 && targetSegments.isEmpty()) {
                                return@use parser.currentTokenLocation().charOffset.toInt().coerceAtLeast(0)
                            }
                        }

                        JsonToken.END_OBJECT, JsonToken.END_ARRAY -> {
                            if (containerTypes.isNotEmpty()) {
                                val wasObject = containerTypes.removeLast()
                                if (!wasObject && arrayIndices.isNotEmpty()) {
                                    arrayIndices.removeLast()
                                }
                            }
                            if (pathSegments.isNotEmpty()) {
                                pathSegments.removeLast()
                            }
                        }

                        else -> {
                            val pushed = enterValue(containerTypes, pathSegments, arrayIndices, pendingFieldName)
                            pendingFieldName = null
                            if (pathSegments.toList() == targetSegments) {
                                return@use parser.currentTokenLocation().charOffset.toInt().coerceAtLeast(0)
                            }
                            if (pushed && pathSegments.isNotEmpty()) {
                                pathSegments.removeLast()
                            }
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun enterValue(
        containerTypes: ArrayDeque<Boolean>,
        pathSegments: ArrayDeque<String>,
        arrayIndices: ArrayDeque<Int>,
        pendingFieldName: String?,
    ): Boolean {
        val parentIsObject = containerTypes.lastOrNull() ?: return false
        return if (parentIsObject) {
            if (pendingFieldName != null) {
                pathSegments.addLast(pendingFieldName)
                true
            } else {
                false
            }
        } else {
            val nextIndex = arrayIndices.removeLast()
            arrayIndices.addLast(nextIndex + 1)
            pathSegments.addLast(nextIndex.toString())
            true
        }
    }

    private fun lineColumnToOffset(text: String, lineNumber: Int, columnNumber: Int): Int {
        var currentLine = 1
        var index = 0
        while (index < text.length && currentLine < lineNumber) {
            if (text[index] == '\n') currentLine++
            index++
        }
        return (index + (columnNumber - 1)).coerceIn(0, text.length)
    }

    private fun setSchemaEditorText(schemaText: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            view.setSchemaEditorText(schemaText)
        }
    }

    private fun showSchemaUrlError(errorMessage: String) {
        view.showUrlError(errorMessage)
    }

    companion object {
        private val SEARCH_TOKEN_SEPARATOR_REGEX = Regex("[^a-z0-9]+")
        private const val MINIMUM_SEARCH_LENGTH_FOR_FUZZY_MATCHING = 3
        private const val MINIMUM_TOKEN_SIMILARITY = 0.6
        private const val SCHEMA_STORE_FILTER_DEBOUNCE_MS = 250L
    }
}
