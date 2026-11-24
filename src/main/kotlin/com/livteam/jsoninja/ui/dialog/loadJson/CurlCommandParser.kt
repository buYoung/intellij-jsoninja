package com.livteam.jsoninja.ui.dialog.loadJson

import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiAuthorizationType
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiHeaderEntry
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiLoadRequest
import com.livteam.jsoninja.ui.dialog.loadJson.model.ApiRequestMethod
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

internal object CurlCommandParser {
    fun looksLikeCurl(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("curl ", ignoreCase = true) ||
            trimmed.startsWith("curl\t", ignoreCase = true) ||
            trimmed.startsWith("curl\n", ignoreCase = true) ||
            trimmed.startsWith("curl\r", ignoreCase = true)
    }

    fun parse(rawInput: String): ApiLoadRequest? {
        if (!looksLikeCurl(rawInput)) return null
        val normalized = rawInput
            .replace("\r\n", "\n")
            .replace("\\\n", " ")
            .trim()
        val tokens = tokenize(normalized)
        if (tokens.isEmpty() || !tokens.first().equals("curl", ignoreCase = true)) {
            return null
        }

        var explicitMethod: ApiRequestMethod? = null
        var url: String? = null
        var authorizationType = ApiAuthorizationType.NONE
        var basicUsername = ""
        var basicPassword = ""
        var bearerToken = ""
        val bodyParts = mutableListOf<String>()
        val headers = mutableListOf<ApiHeaderEntry>()

        var index = 1
        while (index < tokens.size) {
            val token = tokens[index]
            when {
                token == "-X" || token == "--request" -> {
                    val value = tokens.getOrNull(++index)
                    explicitMethod = parseMethod(value)
                }

                token.startsWith("--request=") -> {
                    explicitMethod = parseMethod(token.substringAfter('='))
                }

                token.startsWith("-X") && token.length > 2 -> {
                    explicitMethod = parseMethod(token.substring(2))
                }

                token == "--url" -> {
                    url = tokens.getOrNull(++index)?.trim()
                }

                token.startsWith("--url=") -> {
                    url = token.substringAfter('=').trim()
                }

                token == "-H" || token == "--header" -> {
                    tokens.getOrNull(++index)?.let { rawHeader ->
                        applyHeader(
                            rawHeader = rawHeader,
                            headers = headers,
                            onBearer = { tokenValue ->
                                authorizationType = ApiAuthorizationType.BEARER
                                bearerToken = tokenValue
                            },
                            onBasic = { user, pass ->
                                authorizationType = ApiAuthorizationType.BASIC
                                basicUsername = user
                                basicPassword = pass
                            },
                        )
                    }
                }

                token.startsWith("--header=") -> {
                    applyHeader(
                        rawHeader = token.substringAfter('='),
                        headers = headers,
                        onBearer = { tokenValue ->
                            authorizationType = ApiAuthorizationType.BEARER
                            bearerToken = tokenValue
                        },
                        onBasic = { user, pass ->
                            authorizationType = ApiAuthorizationType.BASIC
                            basicUsername = user
                            basicPassword = pass
                        },
                    )
                }

                token == "-u" || token == "--user" -> {
                    val credentials = tokens.getOrNull(++index).orEmpty()
                    if (credentials.isNotEmpty()) {
                        authorizationType = ApiAuthorizationType.BASIC
                        basicUsername = credentials.substringBefore(':')
                        basicPassword = credentials.substringAfter(':', "")
                    }
                }

                token.startsWith("--user=") -> {
                    val credentials = token.substringAfter('=')
                    if (credentials.isNotEmpty()) {
                        authorizationType = ApiAuthorizationType.BASIC
                        basicUsername = credentials.substringBefore(':')
                        basicPassword = credentials.substringAfter(':', "")
                    }
                }

                token in DATA_FLAGS -> {
                    tokens.getOrNull(++index)?.let(bodyParts::add)
                }

                DATA_PREFIXES.any { token.startsWith(it) } -> {
                    bodyParts += token.substringAfter('=')
                }

                token in FLAGS_WITH_IGNORED_VALUE -> {
                    index++
                }

                !token.startsWith("-") -> {
                    if (url == null && (token.startsWith("http://", ignoreCase = true) ||
                            token.startsWith("https://", ignoreCase = true))
                    ) {
                        url = token
                    }
                }
            }
            index++
        }

        val resolvedUrl = url?.takeIf { it.isNotBlank() } ?: return null
        val requestBody = bodyParts.joinToString("&")
        val resolvedMethod = explicitMethod ?: if (requestBody.isNotEmpty()) ApiRequestMethod.POST else ApiRequestMethod.GET
        return ApiLoadRequest(
            requestMethod = resolvedMethod,
            requestUrl = resolvedUrl,
            authorizationType = authorizationType,
            basicUsername = basicUsername,
            basicPassword = basicPassword,
            bearerToken = bearerToken,
            requestBodyText = requestBody,
            headers = headers,
        )
    }

    private fun applyHeader(
        rawHeader: String,
        headers: MutableList<ApiHeaderEntry>,
        onBearer: (String) -> Unit,
        onBasic: (String, String) -> Unit,
    ) {
        val separatorIndex = rawHeader.indexOf(':')
        if (separatorIndex <= 0) return
        val name = rawHeader.substring(0, separatorIndex).trim()
        val value = rawHeader.substring(separatorIndex + 1).trim()
        if (name.isEmpty()) return

        if (name.equals("Authorization", ignoreCase = true)) {
            when {
                value.startsWith("Bearer ", ignoreCase = true) -> {
                    onBearer(value.substring(7).trim())
                    return
                }

                value.startsWith("Basic ", ignoreCase = true) -> {
                    val encoded = value.substring(6).trim()
                    val decoded = runCatching {
                        String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
                    }.getOrNull()
                    if (decoded != null && ':' in decoded) {
                        onBasic(decoded.substringBefore(':'), decoded.substringAfter(':'))
                        return
                    }
                }
            }
        }
        headers += ApiHeaderEntry(name, value)
    }

    private fun parseMethod(rawMethod: String?): ApiRequestMethod? {
        val normalized = rawMethod?.trim()?.uppercase(Locale.ROOT) ?: return null
        return ApiRequestMethod.entries.firstOrNull { it.name == normalized }
    }

    private fun tokenize(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var hasToken = false
        var index = 0

        while (index < command.length) {
            val ch = command[index]
            when {
                inSingleQuote -> {
                    if (ch == '\'') {
                        inSingleQuote = false
                    } else {
                        current.append(ch)
                    }
                }

                inDoubleQuote -> {
                    if (ch == '\\' && index + 1 < command.length) {
                        val next = command[index + 1]
                        if (next == '"' || next == '\\' || next == '$' || next == '`') {
                            current.append(next)
                            index++
                        } else {
                            current.append(ch)
                        }
                    } else if (ch == '"') {
                        inDoubleQuote = false
                    } else {
                        current.append(ch)
                    }
                }

                ch == '$' && index + 1 < command.length && command[index + 1] == '\'' -> {
                    hasToken = true
                    index += 2
                    while (index < command.length && command[index] != '\'') {
                        if (command[index] == '\\' && index + 1 < command.length) {
                            when (val escaped = command[++index]) {
                                'n' -> current.append('\n')
                                'r' -> current.append('\r')
                                't' -> current.append('\t')
                                else -> current.append(escaped)
                            }
                        } else {
                            current.append(command[index])
                        }
                        index++
                    }
                }

                ch == '\'' -> {
                    hasToken = true
                    inSingleQuote = true
                }

                ch == '"' -> {
                    hasToken = true
                    inDoubleQuote = true
                }

                ch == '\\' && index + 1 < command.length -> {
                    hasToken = true
                    current.append(command[++index])
                }

                ch.isWhitespace() -> {
                    if (hasToken) {
                        tokens += current.toString()
                        current.setLength(0)
                        hasToken = false
                    }
                }

                else -> {
                    hasToken = true
                    current.append(ch)
                }
            }
            index++
        }

        if (hasToken) {
            tokens += current.toString()
        }
        return tokens
    }

    private val DATA_FLAGS = setOf("-d", "--data", "--data-raw", "--data-binary", "--data-ascii", "--data-urlencode")
    private val DATA_PREFIXES = listOf("--data=", "--data-raw=", "--data-binary=", "--data-ascii=", "--data-urlencode=")
    private val FLAGS_WITH_IGNORED_VALUE = setOf(
        "-A", "--user-agent", "-b", "--cookie", "-e", "--referer", "-o", "--output",
        "--connect-timeout", "-m", "--max-time", "-x", "--proxy", "--resolve",
    )
}
