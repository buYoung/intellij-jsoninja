package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisSeverity
import com.livteam.jsoninja.services.treesitter.TreeSitterWasmRuntime
import com.livteam.jsoninja.services.treesitter.WasmMemoryBridge
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode

class TypeLanguageWasmIntegrationTest : BasePlatformTestCase() {
    private val mapper = ObjectMapper()
    private val addedLanguages get() = SupportedLanguage.entries.filter { it.wasmLanguageId >= 4 }

    override fun tearDown() {
        try { TreeSitterWasmRuntime.clear() } finally { super.tearDown() }
    }

    fun testGeneratedSourcesParseQueryAndGenerateJsonThroughBundledWasm() {
        for (language in addedLanguages) {
            val source = convert("""{"active":true,"id":1,"items":[1,2],"child":{"name":"Ada"}}""", language)
            val analysis = project.getService(TypeDeclarationAnalyzerService::class.java).analyzeSource(source, language)
            assertFalse("$language: ${analysis.diagnostics}\n$source", analysis.diagnostics.any { it.severity == TypeAnalysisSeverity.ERROR })
            val query = executeQuery(language, source)
            assertFalse(query.toString(), query.path("has_syntax_errors").asBoolean())
            if (language == SupportedLanguage.JSDOC) {
                assertTrue("$language: $query", query.path("captures").any { it.path("name").asText() == "documentation" && it.path("text").asText().contains("@typedef {Object} Root\n") })
            } else {
                assertTrue("$language: $query", query.path("captures").any { it.path("text").asText() == "Root" })
            }
            assertTrue("$language: $query", query.path("captures").any { it.path("text").asText().contains("items") })
            val result = project.getService(TypeToJsonGenerationService::class.java).generateDetailed(
                source, language, TypeToJsonGenerationOptions(usesRealisticSampleData = false), rootTypeName = "Root",
            )
            val json = mapper.readTree(result.jsonText)
            assertTrue("$language: $json", json.path("active").isBoolean)
            assertTrue("$language: $json", json.path("id").isNumber)
            assertTrue("$language: $json", json.path("items").isArray)
            assertTrue("$language: $json", json.path("items")[0].isNumber)
            assertTrue("$language: $json", json.path("child").path("name").isTextual)
            assertEquals(4, json.size())
        }
    }

    fun testCGeneratedEdgeShapesNeverExposeHelperBookkeeping() {
        val cases = listOf("[]", "{}", "null", "42", "[1,2]", "{\"matrix\":[[1,2],[3]],\"empty\":{},\"mixed\":[null,1]}")
        for (input in cases) {
            val source = convert(input, SupportedLanguage.C)
            val result = project.getService(TypeToJsonGenerationService::class.java).generateDetailed(
                source, SupportedLanguage.C, TypeToJsonGenerationOptions(usesRealisticSampleData = false), rootTypeName = "Root",
            )
            val json = mapper.readTree(result.jsonText)
            assertFalse(result.jsonText, result.jsonText.contains("jsoninja_empty"))
            assertFalse(result.jsonText, result.jsonText.contains("\"length\""))
            assertFalse(result.jsonText, result.jsonText.contains("\"data\""))
            when {
                input.startsWith("[") -> assertTrue(json.toString(), json.isArray)
                input == "{}" -> assertEquals(mapper.readTree("{}"), json)
                input == "42" -> assertTrue(json.isNumber)
            }
        }
    }

    fun testWarningsAndOptionalityTravelSeparatelyFromStrictJson() {
        val source = """
            typedef struct Root {
                /* JSONinja optional v1 */
                int optional;
                int *nullable;
            } Root;
        """.trimIndent()
        val service = project.getService(TypeToJsonGenerationService::class.java)
        val options = TypeToJsonGenerationOptions(
            usesRealisticSampleData = false,
            propertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_ONLY,
            includesNullableFieldWithNullValue = true,
        )
        val result = service.generateDetailed(source, SupportedLanguage.C, options)
        assertTrue(result.diagnostics.any { it.severity == TypeAnalysisSeverity.WARNING })
        val json = mapper.readTree(result.jsonText)
        assertFalse(json.has("optional"))
        assertTrue(json.has("nullable"))
        assertTrue(json.path("nullable").isNull)
        assertEquals(result.jsonText, service.generate(source, SupportedLanguage.C, options))
    }

    fun testMalformedInputIsRejectedAndCancellationPropagates() {
        val service = project.getService(TypeToJsonGenerationService::class.java)
        assertThrows(IllegalStateException::class.java) {
            service.generate("struct Root { int x;", SupportedLanguage.C, TypeToJsonGenerationOptions())
        }
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            service.generateDetailed("struct Root { int x; };", SupportedLanguage.C, TypeToJsonGenerationOptions()) {
                throw java.util.concurrent.CancellationException("cancelled test")
            }
        }
    }

    fun testCEnumsUseNumericConstantsAndRejectUnevaluatedExpressions() {
        val service = project.getService(TypeToJsonGenerationService::class.java)
        val options = TypeToJsonGenerationOptions(usesRealisticSampleData = false)
        val result = service.generate("enum Code { First=010, Next }; struct Root { enum Code code; };", SupportedLanguage.C, options, "Root")
        assertEquals(8, mapper.readTree(result).path("code").asInt())
        assertThrows(IllegalStateException::class.java) {
            service.generate("enum Code { First=1 << 3 }; struct Root { enum Code code; };", SupportedLanguage.C, options, "Root")
        }
    }

    fun testCppWarningResultAndNumericEnumReachFinalJson() {
        val service = project.getService(TypeToJsonGenerationService::class.java)
        val result = service.generateDetailed(
            "enum class Code { First=0b1000 }; struct Root { Code code; std::any value; };",
            SupportedLanguage.CPP, TypeToJsonGenerationOptions(usesRealisticSampleData = false), "Root",
        )
        assertEquals(8, mapper.readTree(result.jsonText).path("code").asInt())
        assertTrue(mapper.readTree(result.jsonText).path("value").isNull)
        assertTrue(result.diagnostics.any { it.code == "cpp.type.unknown" })
    }

    fun testCSharpSourceKeysNameCollisionsAndRootAliasesReachJson() {
        val service = project.getService(TypeToJsonGenerationService::class.java)
        for (input in listOf("[]", "42", "null", "[{\"Root\":1,\"RootValue\":2,\"a-b\":true}]", "{\"Root\":1,\"RootValue\":2,\"a-b\":true}")) {
            val source = convert(input, SupportedLanguage.CSHARP)
            val result = mapper.readTree(service.generate(source, SupportedLanguage.CSHARP, TypeToJsonGenerationOptions(usesRealisticSampleData = false), "Root"))
            when {
                input == "42" -> assertTrue(result.isNumber)
                input == "null" -> assertTrue(result.isNull)
                input == "[]" -> assertTrue(result.isArray)
                else -> {
                    val obj = if (result.isArray) result[0] else result
                    assertEquals(setOf("Root", "RootValue", "a-b"), obj.fieldNames().asSequence().toSet())
                    assertTrue(source.contains("JsonPropertyName"))
                }
            }
        }
        val warned = service.generateDetailed("public class Root { public object Value {get;set;} }", SupportedLanguage.CSHARP, TypeToJsonGenerationOptions(usesRealisticSampleData = false))
        assertTrue(warned.diagnostics.any { it.code == "csharp.type.unknown" })
        assertTrue(mapper.readTree(warned.jsonText).path("Value").isNull)
    }

    fun testPythonOriginalKeysOptionalKeysAndNullableValuesAreIndependent() {
        val service = project.getService(TypeToJsonGenerationService::class.java)
        val source = convert("""{"zip-code":"x","class":1,"이름":true,"__private":false}""", SupportedLanguage.PYTHON)
        assertTrue(source, source.contains("Root = TypedDict"))
        val generated = mapper.readTree(service.generate(source, SupportedLanguage.PYTHON, TypeToJsonGenerationOptions(usesRealisticSampleData = false), "Root"))
        assertEquals(setOf("zip-code", "class", "이름", "__private"), generated.fieldNames().asSequence().toSet())
        val typedDict = """
            class Base(TypedDict):
                required: int
            class Root(Base, total=False):
                optional: str
                nullable: Required[str | None]
                explicit: NotRequired[bool]
        """.trimIndent()
        val result = service.generateDetailed(typedDict, SupportedLanguage.PYTHON, TypeToJsonGenerationOptions(
            usesRealisticSampleData = false,
            propertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_ONLY,
            includesNullableFieldWithNullValue = true,
        ), "Root")
        val required = mapper.readTree(result.jsonText)
        assertEquals(setOf("required", "nullable"), required.fieldNames().asSequence().toSet())
        assertTrue(required.path("nullable").isNull)
        assertTrue(result.diagnostics.toString(), result.diagnostics.isEmpty())
        val warned = service.generateDetailed("class Root:\n    value: make_type()", SupportedLanguage.PYTHON, TypeToJsonGenerationOptions())
        assertTrue(warned.diagnostics.any { it.severity == TypeAnalysisSeverity.WARNING })
        assertTrue(mapper.readTree(warned.jsonText).path("value").isNull)
    }

    fun testRustOwnedFallbackAndSerdeMetadataReachTheJsonGenerator() {
        val service = project.getService(TypeToJsonGenerationService::class.java)
        for (input in listOf("null", "[]", "{}", "[1,2]", "{\"type\":\"x\",\"unknown\":null}")) {
            val source = convert(input, SupportedLanguage.RUST)
            val result = service.generateDetailed(source, SupportedLanguage.RUST, TypeToJsonGenerationOptions(usesRealisticSampleData = false), "Root")
            val json = mapper.readTree(result.jsonText)
            assertFalse(result.jsonText, result.jsonText.contains("JsoninjaValue"))
            if (input.startsWith("[")) assertTrue(json.isArray)
            if (input == "{}") assertEquals(mapper.readTree("{}"), json)
            if (input.contains("type")) assertTrue(json.path("type").isTextual)
        }
        val source = """
            #[serde(rename_all = "camelCase")]
            struct Root {
                user_id: i64,
                #[serde(default)]
                maybe: Option<String>,
                #[serde(rename = "display-name")]
                name: String,
                #[serde(skip)]
                secret: bool,
            }
        """.trimIndent()
        val result = service.generateDetailed(source, SupportedLanguage.RUST, TypeToJsonGenerationOptions(
            usesRealisticSampleData = false, propertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_ONLY,
        ))
        val json = mapper.readTree(result.jsonText)
        assertEquals(setOf("userId", "display-name"), json.fieldNames().asSequence().toSet())
        assertTrue(result.diagnostics.toString(), result.diagnostics.isEmpty())
    }

    fun testScalaRootAliasesAndNullableMembersUseTheGeneratedScope() {
        val service = project.getService(TypeToJsonGenerationService::class.java)
        for (input in listOf("42", "[]", "[1,2]", "[{}]", "{}")) {
            val source = convert(input, SupportedLanguage.SCALA)
            val result = mapper.readTree(service.generate(source, SupportedLanguage.SCALA, TypeToJsonGenerationOptions(usesRealisticSampleData = false), "Root"))
            if (input.startsWith("[")) assertTrue("$source => $result", result.isArray)
            if (input == "42") assertTrue(result.isNumber)
            if (input == "{}") assertEquals(mapper.readTree("{}"), result)
        }
        val result = service.generateDetailed("case class Root(required: Int = 1, nullable: String | Null, /* JSONinja optional v1 */ maybe: Option[String])", SupportedLanguage.SCALA, TypeToJsonGenerationOptions(
            usesRealisticSampleData = false, propertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_ONLY, includesNullableFieldWithNullValue = true,
        ))
        val json = mapper.readTree(result.jsonText)
        assertEquals(setOf("required", "nullable"), json.fieldNames().asSequence().toSet())
        assertTrue(json.path("nullable").isNull)
        assertTrue(result.diagnostics.any { it.severity == TypeAnalysisSeverity.WARNING })
    }

    fun testJsDocQuotedOriginalKeysAndStrictJsonSurviveCommentSensitiveInput() {
        val input = mapper.createObjectNode().put("a.b", "x").put("close*/after", true).put("white space", 1).put("q\"key", false).put("이름", "name")
        val source = convert(mapper.writeValueAsString(input), SupportedLanguage.JSDOC)
        val service = project.getService(TypeToJsonGenerationService::class.java)
        val result = mapper.readTree(service.generate(source, SupportedLanguage.JSDOC, TypeToJsonGenerationOptions(usesRealisticSampleData = false), "Root"))
        assertEquals(input.fieldNames().asSequence().toSet(), result.fieldNames().asSequence().toSet())
        val typed = """
            const fake = "/** @typedef {number} Fake */";
            /**
             * @typedef {Object} Root
             * @property {number} required
             * @property {?string} nullable
             * @property {string} [optional="default"]
             */
        """.trimIndent()
        val generated = service.generateDetailed(typed, SupportedLanguage.JSDOC, TypeToJsonGenerationOptions(
            usesRealisticSampleData = false, propertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_ONLY, includesNullableFieldWithNullValue = true,
        ), "Root")
        val json = mapper.readTree(generated.jsonText)
        assertEquals(setOf("required", "nullable"), json.fieldNames().asSequence().toSet())
        assertTrue(json.path("nullable").isNull)
        assertTrue(generated.diagnostics.toString(), generated.diagnostics.isEmpty())
        assertThrows(IllegalStateException::class.java) { service.generate("/** @typedef {Array<string} Bad */", SupportedLanguage.JSDOC, TypeToJsonGenerationOptions()) }
    }

    fun testDefaultRootSelectionUsesUserDeclarationsInsteadOfGeneratedHelpers() {
        val service = project.getService(TypeToJsonGenerationService::class.java)
        val options = TypeToJsonGenerationOptions(usesRealisticSampleData = false)
        for (language in addedLanguages) {
            for (input in listOf("{\"id\":1,\"items\":[1,2],\"child\":{\"name\":\"Ada\"}}", "[[1,2],[3]]", "null")) {
                val source = convert(input, language)
                val implicit = mapper.readTree(service.generate(source, language, options))
                val explicit = mapper.readTree(service.generate(source, language, options, "Root"))
                assertEquals("$language\n$source", explicit, implicit)
                if (input.startsWith("{")) assertEquals(setOf("id", "items", "child"), implicit.fieldNames().asSequence().toSet())
                if (input.startsWith("[")) assertTrue(implicit.isArray)
            }
        }
    }

    private fun convert(input: String, language: SupportedLanguage): String =
        project.getService(JsonToTypeConversionService::class.java).convert(input, language,
            JsonToTypeConversionOptions(namingConvention = language.defaultNamingConvention, annotationStyle = language.defaultAnnotationStyle))

    private fun executeQuery(language: SupportedLanguage, source: String): JsonNode {
        val runtime = TreeSitterWasmRuntime.getOrCreate()
        return runtime.withTransaction({}) {
            val bridge = WasmMemoryBridge(runtime)
            val buffers = mutableListOf<WasmMemoryBridge.BufferSlice>()
            var parser = 0L
            var tree = 0L
            try {
                val sourceBuffer = bridge.writeUtf8String(source).also(buffers::add)
                val queryText = project.getService(TreeSitterAssetRegistryService::class.java).loadQuery(language)
                val queryBuffer = bridge.writeUtf8String(queryText).also(buffers::add)
                parser = runtime.instance.export("parser_create").apply(language.wasmLanguageId.toLong()).single()
                assertTrue(bridge.readLastErrorMessage(), parser > 0)
                tree = runtime.instance.export("tree_parse").apply(parser, sourceBuffer.pointer.toLong(), sourceBuffer.length.toLong()).single()
                assertTrue(bridge.readLastErrorMessage(), tree > 0)
                val packed = runtime.instance.export("tree_query").apply(tree, sourceBuffer.pointer.toLong(), sourceBuffer.length.toLong(), queryBuffer.pointer.toLong(), queryBuffer.length.toLong()).single()
                assertFalse(bridge.readLastErrorMessage(), bridge.isErrorCode(packed))
                val output = bridge.unpackPointerLength(packed).also(buffers::add)
                mapper.readTree(bridge.readUtf8String(output))
            } finally {
                if (tree > 0) runtime.instance.export("tree_destroy").apply(tree)
                if (parser > 0) runtime.instance.export("parser_destroy").apply(parser)
                buffers.asReversed().forEach(bridge::releaseBuffer)
            }
        }
    }
}
