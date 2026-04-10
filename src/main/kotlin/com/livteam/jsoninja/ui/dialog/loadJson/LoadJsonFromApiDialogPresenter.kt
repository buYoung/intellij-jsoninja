package com.livteam.jsoninja.ui.dialog.loadJson

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsoninjaCoroutineService
import com.livteam.jsoninja.services.JsonObjectMapperService
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoadJsonFromApiDialogPresenter(
    private val project: Project,
    private val onJsonLoaded: (String) -> Unit,
    private val onDialogCloseRequested: () -> Unit
) {
    private val view = LoadJsonFromApiDialogView(project)
    private val coroutineScope = project.service<JsoninjaCoroutineService>().coroutineScope
    private val objectMapper = service<JsonObjectMapperService>().objectMapper

    @Volatile
    private var isDisposed = false
    private var loadJob: Job? = null

    init {
        view.setOnSendRequested { handleSendRequested() }
    }

    fun getComponent(): JComponent {
        return view.component
    }

    fun dispose() {
        isDisposed = true
        loadJob?.cancel()
        view.dispose()
    }

    private fun handleSendRequested() {
        if (isDisposed) return

        view.clearErrorMessage()
        val apiLoadRequest = buildApiLoadRequest()
        val validationErrorMessage = validateApiLoadRequest(apiLoadRequest)
        if (validationErrorMessage != null) {
            view.showErrorMessage(validationErrorMessage)
            return
        }

        view.setLoading(true)
        loadJob?.cancel()
        loadJob = coroutineScope.launch {
            try {
                val responseJson = withContext(Dispatchers.IO) {
                    loadJsonResponse(apiLoadRequest)
                }

                withContext(Dispatchers.EDT) {
                    if (isDisposed) return@withContext
                    try {
                        onJsonLoaded(responseJson)
                        onDialogCloseRequested()
                    } catch (callbackException: Exception) {
                        view.showErrorMessage(
                            callbackException.message
                                ?: LocalizationBundle.message(
                                    "dialog.load.json.api.error.request.failed",
                                    callbackException.javaClass.simpleName
                                )
                        )
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (throwable: Throwable) {
                withContext(Dispatchers.EDT) {
                    if (isDisposed) return@withContext
                    view.showErrorMessage(
                        throwable.message
                            ?: LocalizationBundle.message(
                                "dialog.load.json.api.error.request.failed",
                                throwable.javaClass.simpleName
                            )
                    )
                }
            } finally {
                withContext(Dispatchers.EDT) {
                    if (isDisposed) return@withContext
                    view.setLoading(false)
                }
            }
        }
    }

    private fun buildApiLoadRequest(): ApiLoadRequest {
        return ApiLoadRequest(
            requestMethod = view.getSelectedRequestMethod(),
            requestUrl = view.getRequestUrlText(),
            authorizationType = view.getSelectedAuthorizationType(),
            basicUsername = view.getBasicUsernameText(),
            basicPassword = view.getBasicPasswordText(),
            bearerToken = view.getBearerTokenText(),
            requestBodyText = view.getRequestBodyText()
        )
    }

    private fun validateApiLoadRequest(apiLoadRequest: ApiLoadRequest): String? {
        if (apiLoadRequest.requestUrl.isBlank()) {
            return LocalizationBundle.message("dialog.load.json.api.error.url.empty")
        }

        if (!isValidHttpUrl(apiLoadRequest.requestUrl)) {
            return LocalizationBundle.message("dialog.load.json.api.error.url.invalid")
        }

        when (apiLoadRequest.authorizationType) {
            ApiAuthorizationType.BASIC -> {
                if (apiLoadRequest.basicUsername.isBlank()) {
                    return LocalizationBundle.message("dialog.load.json.api.error.auth.basic.username.empty")
                }
                if (apiLoadRequest.basicPassword.isBlank()) {
                    return LocalizationBundle.message("dialog.load.json.api.error.auth.basic.password.empty")
                }
            }

            ApiAuthorizationType.BEARER -> {
                if (apiLoadRequest.bearerToken.isBlank()) {
                    return LocalizationBundle.message("dialog.load.json.api.error.auth.bearer.token.empty")
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

    private fun loadJsonResponse(apiLoadRequest: ApiLoadRequest): String {
        val httpURLConnection = URI(apiLoadRequest.requestUrl).toURL().openConnection() as HttpURLConnection
        httpURLConnection.requestMethod = apiLoadRequest.requestMethod.name
        httpURLConnection.connectTimeout = 10_000
        httpURLConnection.readTimeout = 15_000
        httpURLConnection.instanceFollowRedirects = true
        httpURLConnection.useCaches = false
        httpURLConnection.setRequestProperty("Accept", "application/json")
        httpURLConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

        configureAuthorizationHeader(httpURLConnection, apiLoadRequest)
        writeRequestBodyIfNeeded(httpURLConnection, apiLoadRequest)

        return try {
            val responseCode = httpURLConnection.responseCode
            val responseBody = readResponseBody(httpURLConnection, responseCode)

            if (responseCode !in 200..299) {
                throw IOException(createHttpStatusErrorMessage(responseCode, responseBody))
            }

            if (responseBody.trim().isEmpty()) {
                throw IOException(LocalizationBundle.message("dialog.load.json.api.error.response.empty"))
            }

            try {
                objectMapper.readTree(responseBody)
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
        } finally {
            httpURLConnection.disconnect()
        }
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
        httpURLConnection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write(apiLoadRequest.requestBodyText)
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
