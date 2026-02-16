package com.livteam.jsoninja.ui.dialog.generateJson.schema

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationService
import com.livteam.jsoninja.services.schema.JsonSchemaGenerationException
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import io.burt.jmespath.jackson.JacksonRuntime
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.JComponent

class GenerateSchemaJsonTabPresenter(
    private val project: Project?
) {
    private val LOG = logger<GenerateSchemaJsonTabPresenter>()
    private val initialConfig = JsonGenerationConfig()
    private val view = GenerateSchemaJsonTabView(project, initialConfig)
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val jmesPathRuntime = JacksonRuntime()
    private val schemaDataGenerationService = project?.getService(JsonSchemaDataGenerationService::class.java)

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
        return JsonGenerationConfig(
            generationMode = JsonGenerationMode.SCHEMA,
            isJson5 = view.isJson5Selected(),
            schemaText = view.getSchemaText(),
            schemaOutputCount = view.getSchemaOutputCountText().toIntOrNull() ?: initialConfig.schemaOutputCount
        )
    }

    fun dispose() {
        isDisposed = true
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

        ApplicationManager.getApplication().executeOnPooledThread {
            if (isDisposed) return@executeOnPooledThread
            try {
                val schemaStoreCatalogText = fetchSchemaText(SCHEMA_STORE_CATALOG_URL)
                val parsedSchemaStoreCatalogItems = parseSchemaStoreCatalog(schemaStoreCatalogText)
                if (parsedSchemaStoreCatalogItems.isEmpty()) {
                    throw IOException("SchemaStore catalog has no valid entries.")
                }

                invokeLater(ModalityState.any()) {
                    if (isDisposed) return@invokeLater
                    schemaStoreCatalogItems = parsedSchemaStoreCatalogItems
                    schemaStoreCatalogState = SchemaStoreCatalogState.READY
                    filterSchemaStoreCatalogItemsByInput()
                }
            } catch (exception: Exception) {
                LOG.warn("Failed to load SchemaStore catalog.", exception)
                invokeLater(ModalityState.any()) {
                    if (isDisposed) return@invokeLater
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
            schemaStoreCatalogItems.filter { schemaStoreCatalogItem ->
                schemaStoreCatalogItem.name.lowercase().contains(normalizedFilterKeyword) ||
                    schemaStoreCatalogItem.url.lowercase().contains(normalizedFilterKeyword)
            }
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

        ApplicationManager.getApplication().executeOnPooledThread {
            if (isDisposed) return@executeOnPooledThread
            try {
                val fetchedSchemaText = fetchSchemaText(schemaUrl)
                if (fetchedSchemaText.trim().isEmpty()) {
                    throw IOException(LocalizationBundle.message("dialog.generate.json.schema.url.fetch.empty"))
                }

                invokeLater(ModalityState.any()) {
                    if (isDisposed) return@invokeLater
                    setSchemaEditorText(fetchedSchemaText)
                }
            } catch (exception: Exception) {
                val message = exception.message
                    ?: LocalizationBundle.message("dialog.generate.json.schema.url.fetch.failed")
                invokeLater(ModalityState.any()) {
                    if (isDisposed) return@invokeLater
                    showSchemaUrlError(message)
                }
            } finally {
                invokeLater(ModalityState.any()) {
                    if (isDisposed) return@invokeLater
                    view.setLoadSchemaFromUrlButtonEnabled(true)
                }
            }
        }
    }

    private fun fetchSchemaText(schemaUrl: String): String {
        val connection = URL(schemaUrl).openConnection() as HttpURLConnection
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
    }
}
