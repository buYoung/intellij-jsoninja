package com.livteam.jsoninja.services

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser

/** All shared editor, query, and conversion consumers parse text through this factory. */
internal class Json5Factory : JsonFactory {
    constructor() : super()
    private constructor(source: Json5Factory) : super(source, null)

    override fun copy(): Json5Factory = Json5Factory(this)

    override fun createParser(content: String): JsonParser =
        super.createParser(Json5InputNormalizer(content).normalize())
}
