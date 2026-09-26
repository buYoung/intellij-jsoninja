package com.livteam.jsoninja.services

import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URI

internal object JsonHttpConnection {
    fun <T> withConnection(requestUrl: String, action: (HttpURLConnection) -> T): T {
        val connection = URI(requestUrl).toURL().openConnection() as HttpURLConnection
        return ConnectionResource(connection).use { resource -> action(resource.connection) }
    }

    private class ConnectionResource(val connection: HttpURLConnection) : Closeable {
        override fun close() {
            connection.disconnect()
        }
    }
}
