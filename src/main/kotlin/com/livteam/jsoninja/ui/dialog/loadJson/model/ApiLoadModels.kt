package com.livteam.jsoninja.ui.dialog.loadJson.model

enum class ApiRequestMethod(
    val supportsRequestBody: Boolean
) {
    GET(false),
    POST(true),
    PUT(true),
    PATCH(true),
    DELETE(true)
}

enum class ApiAuthorizationType {
    NONE,
    BASIC,
    BEARER
}

data class ApiHeaderEntry(
    val name: String,
    val value: String
)

data class ApiLoadRequest(
    val requestMethod: ApiRequestMethod,
    val requestUrl: String,
    val authorizationType: ApiAuthorizationType,
    val basicUsername: String,
    val basicPassword: String,
    val bearerToken: String,
    val requestBodyText: String,
    val headers: List<ApiHeaderEntry> = emptyList()
)
