package com.livteam.jsoninja.services.schema

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationConfig
import com.livteam.jsoninja.ui.dialog.generateJson.model.JsonGenerationMode
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode

class JsonSchemaDataGenerationServiceTest : BasePlatformTestCase() {
    private lateinit var jsonSchemaDataGenerationService: JsonSchemaDataGenerationService
    private lateinit var objectMapper: ObjectMapper

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
