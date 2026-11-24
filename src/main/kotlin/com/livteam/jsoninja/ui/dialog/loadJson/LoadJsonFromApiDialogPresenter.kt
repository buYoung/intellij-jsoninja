package com.livteam.jsoninja.ui.dialog.loadJson

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiAuthorizationType
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiLoadRequest
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.swing.JComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoadJsonFromApiDialogPresenter(
    private val project: Project,
    private val onJsonLoaded: (String) -> Unit,
    private val onDialogCloseRequested: () -> Unit,
    private val onLoadingChanged: (Boolean) -> Unit,
    private val onValidationChanged: (ValidationInfo?) -> Unit,
    onLayoutChanged: () -> Unit
) {
    private class ApiHttpErrorException(
        message: String,
        val jsonErrorBody: String?,
    ) : IOException(message)

    private val view = LoadJsonFromApiDialogView(project)
    private val objectMapper = service<JsonObjectMapperService>().objectMapper
    private val historyStorage = ApiRequestHistoryStorage(project)
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()

    @Volatile
    private var isDisposed = false
    private var isLoading = false
    private var activeRequestJob: Job? = null
    private var requestSequence = 0L
    private var historyItems: List<StoredApiRequestHistoryItem> = emptyList()
    private var lastErrorResponseJson: String? = null

    init {
        view.setOnInputsChanged {
            if (!isLoading) {
                onValidationChanged(null)
                lastErrorResponseJson = null
                view.clearRequestError()
                updateSummary()
            }
        }
        view.setOnHistoryUrlSelected { selectedUrl ->
            applyHistorySelection(selectedUrl)
        }
        view.setOnCurlPasted { rawCurl ->
            applyCurlCommand(rawCurl)
        }
        view.setOnOpenErrorResponseRequested {
            val errorJson = lastErrorResponseJson ?: return@setOnOpenErrorResponseRequested
            onJsonLoaded(errorJson)
            onDialogCloseRequested()
        }
        view.setOnLayoutChanged(onLayoutChanged)
    }

    fun getComponent(): JComponent {
        val component = view.component
        historyItems = historyStorage.loadHistory()
        view.setHistoryUrls(historyItems.map { it.url })
        updateSummary()
        return component
    }

    fun getPreferredFocusedComponent(): JComponent = view.getRequestUrlComponent()

    fun dispose() {
        isDisposed = true
        activeRequestJob?.cancel()
        coroutineScope.cancel()
        view.dispose()
    }

    fun handleSendOrCancelRequested() {
        if (isDisposed) return
        if (isLoading) {
            requestSequence++
            activeRequestJob?.cancel()
            activeRequestJob = null
            setLoading(false)
            return
        }
        handleSendRequested()
    }

    fun handleSendRequested() {
        if (isDisposed || isLoading) return

        onValidationChanged(null)
        lastErrorResponseJson = null
        view.clearRequestError()

        val apiLoadRequest = buildApiLoadRequest()
        val validationInfo = validateApiLoadRequest(apiLoadRequest)
        if (validationInfo != null) {
            onValidationChanged(validationInfo)
            return
        }

        val currentSequence = ++requestSequence
        setLoading(true)

        activeRequestJob = coroutineScope.launch {
            val loadResult = try {
                Result.success(
                    withContext(Dispatchers.IO) {
                        loadJsonResponse(apiLoadRequest)
                    }
                )
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (cancellationException: ProcessCanceledException) {
                throw cancellationException
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }

            if (loadResult.isSuccess) {
                withContext(Dispatchers.IO) {
                    historyStorage.saveSecrets(apiLoadRequest)
                }
            }

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                if (isDisposed || currentSequence != requestSequence) return@withContext
                activeRequestJob = null
                setLoading(false)

                loadResult.fold(
                    onSuccess = { responseJson ->
                        historyItems = historyStorage.saveRequestMetadata(apiLoadRequest)
                        view.setHistoryUrls(historyItems.map { it.url })
                        runCatching {
                            onJsonLoaded(responseJson)
                            onDialogCloseRequested()
                        }.onFailure { callbackException ->
                            showRequestError(
                                message = callbackException.message
                                    ?: LocalizationBundle.message(
                                        "dialog.load.json.api.error.request.failed",
                                        callbackException.javaClass.simpleName
                                    ),
                                jsonErrorBody = null,
                            )
                        }
                    },
                    onFailure = { throwable ->
                        val jsonErrorBody = (throwable as? ApiHttpErrorException)?.jsonErrorBody
                        showRequestError(
                            message = throwable.message
                                ?: LocalizationBundle.message(
                                    "dialog.load.json.api.error.request.failed",
                                    throwable.javaClass.simpleName
                                ),
                            jsonErrorBody = jsonErrorBody,
                        )
                    }
                )
            }
        }
    }

    private fun applyHistorySelection(selectedUrl: String) {
        if (isLoading) return
        val matched = historyItems.firstOrNull { it.url == selectedUrl } ?: return
        val restored = historyStorage.toApiLoadRequest(matched)
        onValidationChanged(null)
        lastErrorResponseJson = null
        view.clearRequestError()
        view.applyRequest(restored)
        updateSummary()

        if (restored.authorizationType != ApiAuthorizationType.NONE) {
            val targetUrl = restored.requestUrl
            val authType = restored.authorizationType
            coroutineScope.launch {
                val secrets = withContext(Dispatchers.IO) {
                    historyStorage.loadSecrets(targetUrl, authType)
                }
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (isDisposed) return@withContext
                    if (view.getRequestUrlText() == targetUrl && view.getSelectedAuthorizationType() == authType) {
                        view.setSecretCredentials(secrets.basicPassword, secrets.bearerToken)
                        updateSummary()
                    }
                }
            }
        }
    }

    private fun applyCurlCommand(rawCurl: String) {
        if (isLoading) return
        val parsed = CurlCommandParser.parse(rawCurl) ?: return
        onValidationChanged(null)
        lastErrorResponseJson = null
        view.clearRequestError()
        view.applyRequest(parsed)
        updateSummary()
    }

    private fun setLoading(isLoading: Boolean) {
        this.isLoading = isLoading
        view.setLoading(isLoading)
        onLoadingChanged(isLoading)
        updateSummary()
    }

    private fun showRequestError(message: String, jsonErrorBody: String?) {
        lastErrorResponseJson = jsonErrorBody
        view.showRequestError(message, canOpenResponseInTab = !jsonErrorBody.isNullOrBlank())
    }

    private fun updateSummary() {
        val request = buildApiLoadRequest()
        val authorization = when (request.authorizationType) {
            ApiAuthorizationType.NONE -> LocalizationBundle.message("dialog.load.json.api.auth.none")
            ApiAuthorizationType.BASIC -> LocalizationBundle.message("dialog.load.json.api.auth.basic")
            ApiAuthorizationType.BEARER -> LocalizationBundle.message("dialog.load.json.api.auth.bearer")
        }
        val summary = when {
            isLoading -> LocalizationBundle.message("dialog.load.json.api.loading")
            request.requestUrl.isBlank() -> LocalizationBundle.message("dialog.load.json.api.summary", request.requestMethod.name, authorization)
            validateApiLoadRequest(request) != null -> LocalizationBundle.message("dialog.load.json.api.summary.invalid")
            else -> LocalizationBundle.message("dialog.load.json.api.summary", request.requestMethod.name, authorization)
        }
        view.setSummary(summary)
    }

    private fun buildApiLoadRequest(): ApiLoadRequest {
        return ApiLoadRequest(
            requestMethod = view.getSelectedRequestMethod(),
            requestUrl = view.getRequestUrlText(),
            authorizationType = view.getSelectedAuthorizationType(),
            basicUsername = view.getBasicUsernameText(),
            basicPassword = view.getBasicPasswordText(),
            bearerToken = view.getBearerTokenText(),
            requestBodyText = view.getRequestBodyText(),
            headers = view.getHeaders(),
        )
    }

    private fun validateApiLoadRequest(apiLoadRequest: ApiLoadRequest): ValidationInfo? {
        if (apiLoadRequest.requestUrl.isBlank()) {
            return ValidationInfo(
                LocalizationBundle.message("dialog.load.json.api.error.url.empty"),
                view.getRequestUrlComponent()
            )
        }

        if (!isValidHttpUrl(apiLoadRequest.requestUrl)) {
            return ValidationInfo(
                LocalizationBundle.message("dialog.load.json.api.error.url.invalid"),
                view.getRequestUrlComponent()
            )
        }

        when (apiLoadRequest.authorizationType) {
            ApiAuthorizationType.BASIC -> {
                if (apiLoadRequest.basicUsername.isBlank()) {
                    return ValidationInfo(
                        LocalizationBundle.message("dialog.load.json.api.error.auth.basic.username.empty"),
                        view.getBasicUsernameComponent()
                    )
                }
                if (apiLoadRequest.basicPassword.isBlank()) {
                    return ValidationInfo(
                        LocalizationBundle.message("dialog.load.json.api.error.auth.basic.password.empty"),
                        view.getBasicPasswordComponent()
                    )
                }
            }

            ApiAuthorizationType.BEARER -> {
                if (apiLoadRequest.bearerToken.isBlank()) {
                    return ValidationInfo(
                        LocalizationBundle.message("dialog.load.json.api.error.auth.bearer.token.empty"),
                        view.getBearerTokenComponent()
                    )
                }
            }

            ApiAuthorizationType.NONE -> Unit
        }

        return null
    }

    private fun isValidHttpUrl(requestUrl: String): Boolean {
        return try {
            val parsedUri = URI(requestUrl)
            val scheme = parsedUri.scheme
            (scheme == "http" || scheme == "https") && !parsedUri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun loadJsonResponse(apiLoadRequest: ApiLoadRequest): String {
        val httpURLConnection = URI(apiLoadRequest.requestUrl).toURL().openConnection() as HttpURLConnection
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                runCatching { httpURLConnection.disconnect() }
            }
        }
        return try {
            configureMethod(httpURLConnection, apiLoadRequest.requestMethod.name)
            httpURLConnection.connectTimeout = 10_000
            httpURLConnection.readTimeout = 15_000
            httpURLConnection.instanceFollowRedirects = true
            httpURLConnection.useCaches = false

            val hasAccept = apiLoadRequest.headers.any { it.name.equals("Accept", ignoreCase = true) }
            val hasContentType = apiLoadRequest.headers.any { it.name.equals("Content-Type", ignoreCase = true) }
            if (!hasAccept) {
                httpURLConnection.setRequestProperty("Accept", "application/json")
            }
            if (!hasContentType) {
                httpURLConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            apiLoadRequest.headers.forEach { header ->
                if (header.name.isNotBlank()) {
                    httpURLConnection.setRequestProperty(header.name, header.value)
                }
            }

            configureAuthorizationHeader(httpURLConnection, apiLoadRequest)
            writeRequestBodyIfNeeded(httpURLConnection, apiLoadRequest)

            try {
                val responseCode = httpURLConnection.responseCode
                val responseBody = readResponseBody(httpURLConnection, responseCode)

                if (responseCode !in 200..299) {
                    val validJsonErrorBody = responseBody.takeIf { isValidJsonPayload(it) }
                    throw ApiHttpErrorException(
                        message = createHttpStatusErrorMessage(responseCode, responseBody),
                        jsonErrorBody = validJsonErrorBody,
                    )
                }

                if (responseBody.trim().isEmpty()) {
                    throw IOException(LocalizationBundle.message("dialog.load.json.api.error.response.empty"))
                }

                try {
                    objectMapper.readTree(responseBody)
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (cancellationException: ProcessCanceledException) {
                    throw cancellationException
                } catch (jsonParseException: Exception) {
                    throw IOException(
                        LocalizationBundle.message(
                            "dialog.load.json.api.error.response.invalid.json",
                            jsonParseException.message ?: jsonParseException.javaClass.simpleName
                        ),
                        jsonParseException
                    )
                }

                responseBody
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (cancellationException: ProcessCanceledException) {
                throw cancellationException
            } catch (ioException: IOException) {
                throw ioException
            } catch (exception: Exception) {
                throw IOException(
                    LocalizationBundle.message(
                        "dialog.load.json.api.error.request.failed",
                        exception.message ?: exception.javaClass.simpleName
                    ),
                    exception
                )
            }
        } finally {
            cancellationHandle?.dispose()
            httpURLConnection.disconnect()
        }
    }

    private fun configureMethod(connection: HttpURLConnection, method: String) {
        if (method == "PATCH") {
            // HttpURLConnection rejects PATCH directly on JDK; tunnel cleanly via X-HTTP-Method-Override if needed.
            try {
                connection.requestMethod = method
            } catch (_: java.net.ProtocolException) {
                connection.requestMethod = "POST"
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            }
        } else {
            connection.requestMethod = method
        }
    }

    private fun isValidJsonPayload(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return false
        return runCatching { objectMapper.readTree(trimmed) != null }.getOrDefault(false)
    }

    private fun configureAuthorizationHeader(
        httpURLConnection: HttpURLConnection,
        apiLoadRequest: ApiLoadRequest
    ) {
        when (apiLoadRequest.authorizationType) {
            ApiAuthorizationType.BASIC -> {
                val credentialText = "${apiLoadRequest.basicUsername}:${apiLoadRequest.basicPassword}"
                val encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentialText.toByteArray(StandardCharsets.UTF_8))
                httpURLConnection.setRequestProperty("Authorization", "Basic $encodedCredentials")
            }

            ApiAuthorizationType.BEARER -> {
                httpURLConnection.setRequestProperty("Authorization", "Bearer ${apiLoadRequest.bearerToken}")
            }

            ApiAuthorizationType.NONE -> Unit
        }
    }

    private fun writeRequestBodyIfNeeded(
        httpURLConnection: HttpURLConnection,
        apiLoadRequest: ApiLoadRequest
    ) {
        if (!apiLoadRequest.requestMethod.supportsRequestBody) {
            return
        }

        if (apiLoadRequest.requestBodyText.isBlank()) {
            return
        }

        httpURLConnection.doOutput = true
        httpURLConnection.outputStream.use { outputStream ->
            outputStream.write(apiLoadRequest.requestBodyText.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun readResponseBody(
        httpURLConnection: HttpURLConnection,
        responseCode: Int
    ): String {
        val responseInputStream = if (responseCode in 200..299) {
            httpURLConnection.inputStream
        } else {
            httpURLConnection.errorStream
        } ?: return ""

        return responseInputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            reader.readText()
        }
    }

    private fun createHttpStatusErrorMessage(
        responseCode: Int,
        responseBody: String
    ): String {
        val normalizedResponseBody = responseBody
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(240)

        if (normalizedResponseBody.isBlank()) {
            return LocalizationBundle.message("dialog.load.json.api.error.http.status", responseCode)
        }

        return LocalizationBundle.message(
            "dialog.load.json.api.error.http.status.with.body",
            responseCode,
            normalizedResponseBody
        )
    }
}
