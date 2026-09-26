package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import java.net.URI
import java.util.UUID
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import com.sun.net.httpserver.HttpServer

class JsonSchemaDataGenerationServiceTest : BasePlatformTestCase() {
    private lateinit var jsonSchemaDataGenerationService: JsonSchemaDataGenerationService
    private lateinit var objectMapper: ObjectMapper

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        jsonSchemaDataGenerationService = project.service()
        objectMapper = service<JsonObjectMapperService>().objectMapper
    }

    fun testGenerateFromSchemaRequiredAndOptionalModeIncludesOptionalPropertiesForTsconfigSchema() {
        val generationConfig = createSchemaGenerationConfig(
            schemaPropertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL
        )

        val generatedJson = jsonSchemaDataGenerationService.generateFromSchema(generationConfig)
        val generatedJsonNode = objectMapper.readTree(generatedJson)

        assertTrue(generatedJsonNode.isObject)
        assertTrue(generatedJsonNode.has("compilerOptions"))
        assertTrue(generatedJsonNode.has("extends"))
        assertTrue(generatedJsonNode.has("files"))
        assertTrue(generatedJsonNode.path("compilerOptions").isObject)
        assertTrue(generatedJsonNode.path("compilerOptions").has("target"))
        assertTrue(generatedJsonNode.path("compilerOptions").has("strict"))
    }

    fun testGenerateFromSchemaRequiredOnlyModeGeneratesEmptyObjectForTsconfigSchema() {
        val generationConfig = createSchemaGenerationConfig(
            schemaPropertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_ONLY
        )

        val generatedJson = jsonSchemaDataGenerationService.generateFromSchema(generationConfig)
        val generatedJsonNode = objectMapper.readTree(generatedJson)

        assertTrue(generatedJsonNode.isObject)
        assertEquals(0, generatedJsonNode.size())
    }

    fun testGenerateFromSchemaCommentedModeCommentsOutOptionalPropertiesForTsconfigSchema() {
        val generationConfig = createSchemaGenerationConfig(
            schemaPropertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL_COMMENTED
        )

        val generatedJson = jsonSchemaDataGenerationService.generateFromSchema(generationConfig)

        assertTrue(generatedJson.contains("// \"compilerOptions\":"))
        assertTrue(generatedJson.contains("// \"extends\":"))
        assertTrue(generatedJson.contains("// \"files\":"))
        assertFalse(generatedJson.trim() == "{}")
    }

    fun testGeneratedFormatsAndPatternsReachFinalJson() {
        val config = JsonGenerationConfig(
            generationMode = JsonGenerationMode.SCHEMA,
            schemaPropertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_ONLY,
            schemaText = """
                {
                  "type": "object",
                  "required": ["email", "uuid", "url", "letters", "digits"],
                  "properties": {
                    "email": {"type": "string", "format": "email"},
                    "uuid": {"type": "string", "format": "uuid"},
                    "url": {"type": "string", "format": "uri"},
                    "letters": {"type": "string", "pattern": "^[A-Za-z]+$"},
                    "digits": {"type": "string", "pattern": "^[0-9]+$"}
                  },
                  "additionalProperties": false
                }
            """.trimIndent(),
        )

        val result = objectMapper.readTree(jsonSchemaDataGenerationService.generateFromSchema(config))

        assertEquals(5, result.size())
        assertTrue(result.path("email").asText().contains('@'))
        assertEquals(result.path("uuid").asText(), UUID.fromString(result.path("uuid").asText()).toString())
        val url = URI(result.path("url").asText())
        assertTrue(url.scheme in setOf("http", "https"))
        assertFalse(url.host.isNullOrBlank())
        assertTrue(Regex("^[A-Za-z]+$").matches(result.path("letters").asText()))
        assertTrue(Regex("^[0-9]+$").matches(result.path("digits").asText()))
    }

    fun testGeneratedOutputMatchesPrecisionAndLocalReferenceGolden() {
        val fixtureDirectory = Path.of("src/test/testData/jsonSchema/generation")
        val config = JsonGenerationConfig(
            generationMode = JsonGenerationMode.SCHEMA,
            schemaText = Files.readString(fixtureDirectory.resolve("schema.json")),
            schemaPropertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_ONLY,
        )
        val generatedJson = jsonSchemaDataGenerationService.generateFromSchema(config)
        val strictMapper = project.service<JsonSchemaValidationService>().getStrictObjectMapper()
        assertEquals(strictMapper.readTree(Files.readString(fixtureDirectory.resolve("expected.json"))),
            strictMapper.readTree(generatedJson))
        val prepared = jsonSchemaDataGenerationService.prepareSchema(config.schemaText)
        assertTrue(project.service<JsonSchemaValidationService>()
            .validateInstance(prepared.compiledSchema, strictMapper.readTree(generatedJson)).isValid)
    }

    fun testExternalRelativeReferencesUseOneCachedFetch() {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/schemas/child.json") { exchange ->
            try {
                requests.incrementAndGet()
                val bytes = """{"type":"integer","const":7}""".toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            val config = JsonGenerationConfig(
                generationMode = JsonGenerationMode.SCHEMA,
                schemaRetrievalUri = "http://127.0.0.1:${server.address.port}/schemas/root.json",
                schemaPropertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_ONLY,
                schemaText = """{
                    "type":"object","required":["left","right"],
                    "properties":{"left":{"${'$'}ref":"child.json"},"right":{"${'$'}ref":"child.json"}}
                }""",
            )
            assertEquals(objectMapper.readTree("""{"left":7,"right":7}"""),
                objectMapper.readTree(jsonSchemaDataGenerationService.generateFromSchema(config)))
            assertEquals("Repeated references must share the normalizer's document cache", 1, requests.get())
        } finally {
            server.stop(0)
        }
    }

    fun testExternalFileReferenceReachesFinalJson() {
        val directory = Files.createTempDirectory("jsoninja-schema-reference")
        val child = directory.resolve("child.json")
        try {
            Files.writeString(child, """{"type":"string","const":"local-file"}""")
            val config = JsonGenerationConfig(
                generationMode = JsonGenerationMode.SCHEMA,
                schemaText = """{"${'$'}ref":"child.json"}""",
                schemaRetrievalUri = directory.resolve("root.json").toUri().toString(),
            )
            assertEquals("local-file", objectMapper.readTree(jsonSchemaDataGenerationService.generateFromSchema(config)).asText())
        } finally {
            Files.deleteIfExists(child)
            Files.deleteIfExists(directory)
        }
    }

    private fun createSchemaGenerationConfig(
        schemaPropertyGenerationMode: SchemaPropertyGenerationMode
    ): JsonGenerationConfig {
        return JsonGenerationConfig(
            generationMode = JsonGenerationMode.SCHEMA,
            schemaText = TS_CONFIG_SCHEMA_TEXT,
            schemaOutputCount = 1,
            isJson5 = false,
            schemaPropertyGenerationMode = schemaPropertyGenerationMode
        )
    }

    companion object {
        private val TS_CONFIG_SCHEMA_TEXT = """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "definitions": {
                "tsconfigRoot": {
                  "type": "object",
                  "properties": {
                    "compilerOptions": {
                      "${'$'}ref": "#/definitions/compilerOptions"
                    },
                    "files": {
                      "type": "array",
                      "items": { "type": "string" }
                    },
                    "include": {
                      "type": "array",
                      "items": { "type": "string" }
                    },
                    "exclude": {
                      "type": "array",
                      "items": { "type": "string" }
                    },
                    "extends": {
                      "type": "string"
                    }
                  },
                  "additionalProperties": false
                },
                "compilerOptions": {
                  "type": "object",
                  "properties": {
                    "target": {
                      "enum": ["es5", "es2015", "es2020"]
                    },
                    "module": {
                      "type": "string"
                    },
                    "baseUrl": {
                      "type": "string"
                    },
                    "strict": {
                      "type": "boolean"
                    }
                  },
                  "additionalProperties": false
                }
              },
              "allOf": [
                {
                  "${'$'}ref": "#/definitions/tsconfigRoot"
                }
              ]
            }
        """.trimIndent()
    }
}
