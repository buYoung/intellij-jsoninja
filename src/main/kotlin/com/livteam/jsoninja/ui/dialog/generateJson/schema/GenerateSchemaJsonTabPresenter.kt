package com.livteam.jsoninja.ui.dialog.generateJson.schema

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationService
import com.livteam.jsoninja.services.schema.JsonSchemaGenerationException
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import io.burt.jmespath.jackson.JacksonRuntime
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.JComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

class GenerateSchemaJsonTabPresenter(
    private val project: Project?
) {
    private val LOG = logger<GenerateSchemaJsonTabPresenter>()
    private val initialConfig = JsonGenerationConfig()
    private val view = GenerateSchemaJsonTabView(project, initialConfig)
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val jmesPathRuntime = JacksonRuntime()
    private val schemaDataGenerationService = project?.getService(JsonSchemaDataGenerationService::class.java)
    private val coroutineScope = project?.service<JsoninjaCoroutineScopeService>()?.createChildScope()
        ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var isDisposed = false

    private var schemaStoreCatalogItems: List<SchemaStoreCatalogItem> = emptyList()
    private var schemaStoreCatalogState = SchemaStoreCatalogState.LOADING

    init {
        view.setOnSchemaUrlInputChanged { filterSchemaStoreCatalogItemsByInput() }
        view.setOnLoadSchemaFromUrlRequested { loadSchemaFromUrl() }
        loadSchemaStoreCatalog()
    }

    fun getComponent(): JComponent {
        return view.component
    }

    fun validate(): ValidationInfo? {
        val schemaOutputCount = view.getSchemaOutputCountText().toIntOrNull()
        if (schemaOutputCount == null || schemaOutputCount <= 0) {
            return ValidationInfo(
                LocalizationBundle.message("validation.error.positive.integer.required.ge1"),
                view.getSchemaOutputCountField()
            )
        }

        if (view.hasPendingSchemaStoreSelectionLoad()) {
            val warningMessage = LocalizationBundle.message("validation.error.schema.store.load.required")
            return ValidationInfo(warningMessage, view.getSchemaUrlInputComponent())
        }

        val schemaText = view.getSchemaText().trim()
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
            ValidationInfo(
                generationException.message ?: LocalizationBundle.message("validation.error.schema.invalid"),
                view.getSchemaInputComponent()
            )
        } catch (exception: Exception) {
            ValidationInfo(
                LocalizationBundle.message("validation.error.schema.invalid"),
                view.getSchemaInputComponent()
            )
        }
    }

    fun getConfig(): JsonGenerationConfig {
        val schemaPropertyGenerationMode = view.getSchemaPropertyGenerationMode()
        val isCommentedMode = schemaPropertyGenerationMode == SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
        return JsonGenerationConfig(
            generationMode = JsonGenerationMode.SCHEMA,
            isJson5 = view.isJson5Selected() || isCommentedMode,
            schemaText = view.getSchemaText(),
            schemaOutputCount = view.getSchemaOutputCountText().toIntOrNull() ?: initialConfig.schemaOutputCount,
            schemaPropertyGenerationMode = schemaPropertyGenerationMode
        )
    }

    fun dispose() {
        isDisposed = true
        coroutineScope.cancel()
        view.dispose()
    }


    private fun loadSchemaStoreCatalog() {
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
                val schemaStoreCatalogText = withContext(Dispatchers.IO) {
                    fetchSchemaText(SCHEMA_STORE_CATALOG_URL)
                }
                val parsedSchemaStoreCatalogItems = withContext(Dispatchers.Default) {
                    parseSchemaStoreCatalog(schemaStoreCatalogText)
                }
                if (parsedSchemaStoreCatalogItems.isEmpty()) {
                    throw IOException("SchemaStore catalog has no valid entries.")
                }

                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (isDisposed) return@withContext
                    schemaStoreCatalogItems = parsedSchemaStoreCatalogItems
                    schemaStoreCatalogState = SchemaStoreCatalogState.READY
                    filterSchemaStoreCatalogItemsByInput()
                }
            } catch (cancellationException: CancellationException) {
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

    private fun parseSchemaStoreCatalog(schemaStoreCatalogText: String): List<SchemaStoreCatalogItem> {
        val schemaStoreCatalogNode = objectMapper.readTree(schemaStoreCatalogText)
        val schemaNameExpression = jmesPathRuntime.compile("schemas[].name")
        val schemaUrlExpression = jmesPathRuntime.compile("schemas[].url")
        val schemaNameNodes = schemaNameExpression.search(schemaStoreCatalogNode)
        val schemaUrlNodes = schemaUrlExpression.search(schemaStoreCatalogNode)

        if (!schemaNameNodes.isArray || !schemaUrlNodes.isArray) {
            return emptyList()
        }

        val schemaStoreCatalogItemsByUrl = linkedMapOf<String, SchemaStoreCatalogItem>()
        val schemaCount = minOf(schemaNameNodes.size(), schemaUrlNodes.size())
        for (index in 0 until schemaCount) {
            val schemaName = schemaNameNodes.path(index).asText("").trim()
            val schemaUrl = schemaUrlNodes.path(index).asText("").trim()
            if (schemaName.isBlank() || schemaUrl.isBlank()) {
                continue
            }

            if (!schemaStoreCatalogItemsByUrl.containsKey(schemaUrl)) {
                schemaStoreCatalogItemsByUrl[schemaUrl] = SchemaStoreCatalogItem(
                    name = schemaName,
                    url = schemaUrl
                )
            }
        }

        return schemaStoreCatalogItemsByUrl.values.toList()
    }

    private fun filterSchemaStoreCatalogItemsByInput() {
        if (schemaStoreCatalogState != SchemaStoreCatalogState.READY || isDisposed) {
            return
        }

        val editorText = view.getSchemaUrlEditorText()
        val normalizedFilterKeyword = editorText.trim().lowercase()
        val filteredSchemaStoreCatalogItems = if (normalizedFilterKeyword.isBlank()) {
            schemaStoreCatalogItems
        } else {
            schemaStoreCatalogItems
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
            return
        }

        view.updateSchemaUrlSuggestions(
            schemaUrlSuggestionItems = filteredSchemaStoreCatalogItems.map { schemaStoreCatalogItem ->
                SchemaUrlComboBoxItem.CatalogEntry(schemaStoreCatalogItem)
            },
            editorText = editorText,
            showPopupWhenAvailable = true
        )
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

    private fun loadSchemaFromUrl() {
        val schemaUrl = view.getSchemaUrlInputText()
        if (schemaUrl.isBlank()) {
            showSchemaUrlError(LocalizationBundle.message("dialog.generate.json.schema.url.empty"))
            return
        }

        if (!schemaUrl.startsWith("http://") && !schemaUrl.startsWith("https://")) {
            showSchemaUrlError(LocalizationBundle.message("dialog.generate.json.schema.url.invalid"))
            return
        }

        view.setLoadSchemaFromUrlButtonEnabled(false)

        coroutineScope.launch {
            if (isDisposed) return@launch
            try {
                val fetchedSchemaText = withContext(Dispatchers.IO) {
                    fetchSchemaText(schemaUrl)
                }
                if (fetchedSchemaText.trim().isEmpty()) {
                    throw IOException(LocalizationBundle.message("dialog.generate.json.schema.url.fetch.empty"))
                }

                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (isDisposed) return@withContext
                    setSchemaEditorText(fetchedSchemaText)
                    view.markSchemaStoreSelectionLoaded()
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                val message = exception.message
                    ?: LocalizationBundle.message("dialog.generate.json.schema.url.fetch.failed")
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (isDisposed) return@withContext
                    showSchemaUrlError(message)
                }
            } finally {
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (isDisposed) return@withContext
                    view.setLoadSchemaFromUrlButtonEnabled(true)
                }
            }
        }
    }

    private fun fetchSchemaText(schemaUrl: String): String {
        val connection = URI(schemaUrl).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/schema+json, application/json;q=0.9, */*;q=0.8")

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException(
                    LocalizationBundle.message("dialog.generate.json.schema.url.http.status", responseCode)
                )
            }

            connection.inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun setSchemaEditorText(schemaText: String) {
        if (project == null) {
            view.setSchemaEditorText(schemaText)
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            view.setSchemaEditorText(schemaText)
        }
    }

    private fun showSchemaUrlError(errorMessage: String) {
        Messages.showErrorDialog(
            project,
            errorMessage,
            LocalizationBundle.message("dialog.generate.json.error.title")
        )
    }

    companion object {
        private const val SCHEMA_STORE_CATALOG_URL = "https://www.schemastore.org/api/json/catalog.json"
        private val SEARCH_TOKEN_SEPARATOR_REGEX = Regex("[^a-z0-9]+")
        private const val MINIMUM_SEARCH_LENGTH_FOR_FUZZY_MATCHING = 3
        private const val MINIMUM_TOKEN_SIMILARITY = 0.6
    }
}
