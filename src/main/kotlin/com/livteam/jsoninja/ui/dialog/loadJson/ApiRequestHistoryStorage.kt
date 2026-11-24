package com.livteam.jsoninja.ui.dialog.loadJson

import com.fasterxml.jackson.core.type.TypeReference
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiAuthorizationType
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiHeaderEntry
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiLoadRequest
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiRequestMethod
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal data class StoredApiRequestHistoryItem(
    val method: String = ApiRequestMethod.GET.name,
    val url: String = "",
    val authorizationType: String = ApiAuthorizationType.NONE.name,
    val basicUsername: String = "",
    val requestBodyText: String = "",
    val headers: List<ApiHeaderEntry> = emptyList(),
)

internal data class ApiStoredSecrets(
    val basicPassword: String = "",
    val bearerToken: String = "",
)

internal class ApiRequestHistoryStorage(private val project: Project) {
    private val LOG = logger<ApiRequestHistoryStorage>()
    private val objectMapper by lazy { service<JsonObjectMapperService>().standardObjectMapper }

    fun loadHistory(): List<StoredApiRequestHistoryItem> {
        val rawJson = PropertiesComponent.getInstance(project).getValue(HISTORY_PROPERTY_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return runCatching {
            objectMapper.readValue(rawJson, object : TypeReference<List<StoredApiRequestHistoryItem>>() {})
                .filter { it.url.isNotBlank() }
        }.onFailure { error ->
            LOG.warn("Failed to parse API request history.", error)
        }.getOrDefault(emptyList())
    }

    fun saveRequestMetadata(request: ApiLoadRequest): List<StoredApiRequestHistoryItem> {
        val sanitizedHeaders = request.headers.filterNot { header ->
            header.name.isBlank() || isSensitiveHeaderName(header.name)
        }
        val newItem = StoredApiRequestHistoryItem(
            method = request.requestMethod.name,
            url = request.requestUrl.trim(),
            authorizationType = request.authorizationType.name,
            basicUsername = if (request.authorizationType == ApiAuthorizationType.BASIC) request.basicUsername else "",
            requestBodyText = if (request.requestMethod.supportsRequestBody) request.requestBodyText else "",
            headers = sanitizedHeaders,
        )
        val updated = (listOf(newItem) + loadHistory().filterNot { it.url == newItem.url })
            .take(MAX_HISTORY_ENTRIES)
        runCatching {
            val serialized = objectMapper.writeValueAsString(updated)
            PropertiesComponent.getInstance(project).setValue(HISTORY_PROPERTY_KEY, serialized)
        }.onFailure { error ->
            LOG.warn("Failed to persist API request history.", error)
        }
        return updated
    }

    /** Must be called off EDT. */
    fun saveSecrets(request: ApiLoadRequest) {
        val url = request.requestUrl.trim()
        if (url.isEmpty()) return
        val attributes = credentialAttributesFor(url)
        runCatching {
            when (request.authorizationType) {
                ApiAuthorizationType.BASIC -> {
                    PasswordSafe.instance.set(attributes, Credentials(request.basicUsername, request.basicPassword))
                }

                ApiAuthorizationType.BEARER -> {
                    PasswordSafe.instance.set(attributes, Credentials(BEARER_USER_MARKER, request.bearerToken))
                }

                ApiAuthorizationType.NONE -> {
                    PasswordSafe.instance.set(attributes, null)
                }
            }
        }.onFailure { error ->
            LOG.warn("Failed to save API credentials in PasswordSafe.", error)
        }
    }

    /** Must be called off EDT. */
    fun loadSecrets(url: String, authorizationType: ApiAuthorizationType): ApiStoredSecrets {
        if (url.isBlank() || authorizationType == ApiAuthorizationType.NONE) {
            return ApiStoredSecrets()
        }
        return runCatching {
            val credentials = PasswordSafe.instance.get(credentialAttributesFor(url.trim()))
                ?: return ApiStoredSecrets()
            val secret = credentials.getPasswordAsString().orEmpty()
            when (authorizationType) {
                ApiAuthorizationType.BASIC -> ApiStoredSecrets(basicPassword = secret)
                ApiAuthorizationType.BEARER -> ApiStoredSecrets(bearerToken = secret)
                ApiAuthorizationType.NONE -> ApiStoredSecrets()
            }
        }.onFailure { error ->
            LOG.warn("Failed to read API credentials from PasswordSafe.", error)
        }.getOrDefault(ApiStoredSecrets())
    }

    fun toApiLoadRequest(item: StoredApiRequestHistoryItem): ApiLoadRequest {
        val method = ApiRequestMethod.entries.firstOrNull { it.name == item.method } ?: ApiRequestMethod.GET
        val authType = ApiAuthorizationType.entries.firstOrNull { it.name == item.authorizationType }
            ?: ApiAuthorizationType.NONE
        return ApiLoadRequest(
            requestMethod = method,
            requestUrl = item.url,
            authorizationType = authType,
            basicUsername = item.basicUsername,
            basicPassword = "",
            bearerToken = "",
            requestBodyText = item.requestBodyText,
            headers = item.headers,
        )
    }

    private fun credentialAttributesFor(url: String): CredentialAttributes {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return CredentialAttributes(generateServiceName("JSONinja", "api-auth:$digest"))
    }

    private fun isSensitiveHeaderName(headerName: String): Boolean {
        val lower = headerName.trim().lowercase(Locale.ROOT)
        return lower in SENSITIVE_HEADER_NAMES ||
            lower.contains("token") ||
            lower.contains("secret") ||
            lower.contains("api-key") ||
            lower.contains("apikey")
    }

    private companion object {
        const val HISTORY_PROPERTY_KEY = "com.livteam.jsoninja.api.requestHistory"
        const val MAX_HISTORY_ENTRIES = 15
        const val BEARER_USER_MARKER = "__bearer__"
        val SENSITIVE_HEADER_NAMES = setOf(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "api-key",
        )
    }
}
