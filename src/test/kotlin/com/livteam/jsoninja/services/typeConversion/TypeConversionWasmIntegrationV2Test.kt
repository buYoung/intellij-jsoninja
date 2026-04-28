package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.treesitter.TreeSitterWasmRuntime
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode

class TypeConversionWasmIntegrationV2Test : BasePlatformTestCase() {
    private lateinit var jsonToTypeConversionService: JsonToTypeConversionService
    private lateinit var typeDeclarationAnalyzerService: TypeDeclarationAnalyzerService
    private lateinit var typeToJsonGenerationService: TypeToJsonGenerationService
    private lateinit var objectMapper: ObjectMapper

    override fun setUp() {
        super.setUp()
        TreeSitterWasmRuntime.clear()
        jsonToTypeConversionService = project.service()
        typeDeclarationAnalyzerService = project.service()
        typeToJsonGenerationService = project.service()
        objectMapper = service<JsonObjectMapperService>().objectMapper
    }

    override fun tearDown() {
        try {
            TreeSitterWasmRuntime.clear()
        } finally {
            super.tearDown()
        }
    }

    fun testSuccessfulJsonToTypeCasesV2() {
        JSON_TO_TYPE_SUCCESS_CASES.forEach { successCase ->
            val conversionResult = jsonToTypeConversionService.convertDetailed(
                jsonText = successCase.jsonText,
                language = successCase.language,
                options = JsonToTypeConversionOptions(
                    rootTypeName = successCase.rootTypeName,
                    namingConvention = successCase.language.defaultNamingConvention,
                    annotationStyle = successCase.language.defaultAnnotationStyle,
                    allowsNullableFields = true,
                    usesExperimentalGoUnionTypes = false,
                ),
            )

            val declarationNames = conversionResult.declarations.map { it.name }.toSet()
            successCase.expectedDeclarationNames.forEach { declarationName ->
                assertTrue(
                    "성공 케이스 `${successCase.caseName}`는 ${successCase.successReason} 때문에 `$declarationName` 선언을 만들어야 합니다. 실제 선언: $declarationNames",
                    declarationName in declarationNames,
                )
            }
            assertTrue(
                "성공 케이스 `${successCase.caseName}`는 ${successCase.successReason} 때문에 `${successCase.expectedRenderedFragment}`를 렌더링해야 합니다.",
                conversionResult.sourceCode.contains(successCase.expectedRenderedFragment),
            )
        }
    }

    fun testSuccessfulTypeToJsonCasesV2() {
        TYPE_TO_JSON_SUCCESS_CASES.forEach { successCase ->
            TreeSitterWasmRuntime.clear()

            val generatedJson = typeToJsonGenerationService.generate(
                sourceCode = successCase.sourceCode,
                language = successCase.language,
                options = TYPE_TO_JSON_OPTIONS,
                rootTypeName = successCase.rootTypeName,
            )
            val generatedJsonNode = objectMapper.readTree(generatedJson)

            successCase.assertJson(generatedJsonNode, successCase)
        }
    }

    fun testJsonToTypeFailureCasesV2() {
        JSON_TO_TYPE_FAILURE_CASES.forEach { failureCase ->
            val failure = expectFailure(failureCase.expectedMessageFragment) {
                jsonToTypeConversionService.convertDetailed(
                    jsonText = failureCase.jsonText,
                    language = failureCase.language,
                    options = JsonToTypeConversionOptions(
                        rootTypeName = failureCase.rootTypeName,
                        namingConvention = failureCase.language.defaultNamingConvention,
                        annotationStyle = failureCase.language.defaultAnnotationStyle,
                        allowsNullableFields = true,
                        usesExperimentalGoUnionTypes = false,
                    ),
                )
            }
            assertTrue(
                "실패 케이스 `${failureCase.caseName}`는 ${failureCase.failureReason} 때문에 실패해야 합니다. 실제 예외: $failure",
                failure.message.orEmpty().contains(failureCase.expectedMessageFragment, ignoreCase = true),
            )
        }
    }

    fun testTypeToJsonFailureCasesV2() {
        TYPE_TO_JSON_FAILURE_CASES.forEach { failureCase ->
            TreeSitterWasmRuntime.clear()

            val failure = expectFailure(failureCase.expectedMessageFragment) {
                typeToJsonGenerationService.generate(
                    sourceCode = failureCase.sourceCode,
                    language = failureCase.language,
                    options = failureCase.options,
                    rootTypeName = failureCase.rootTypeName,
                )
            }
            assertTrue(
                "실패 케이스 `${failureCase.caseName}`는 ${failureCase.failureReason} 때문에 실패해야 합니다. 실제 예외: $failure",
                failure.message.orEmpty().contains(failureCase.expectedMessageFragment, ignoreCase = true),
            )
        }
    }

    fun testUnsupportedTypeScriptTupleEmitsWasmDiagnosticAndDegradesToNullV2() {
        TreeSitterWasmRuntime.clear()

        val analysisResult = typeDeclarationAnalyzerService.analyzeSource(
            sourceCode = TYPESCRIPT_UNSUPPORTED_TUPLE_SOURCE,
            language = SupportedLanguage.TYPESCRIPT,
        )

        assertTrue(
            "실패 케이스 `typescript tuple`은 tuple 타입을 현재 wasm 분석기가 지원하지 않기 때문에 `typescript.type.tuple` 진단을 만들어야 합니다. 실제 진단: ${analysisResult.diagnostics}",
            analysisResult.diagnostics.any { it.code == "typescript.type.tuple" },
        )

        val generatedJson = typeToJsonGenerationService.generate(
            sourceCode = TYPESCRIPT_UNSUPPORTED_TUPLE_SOURCE,
            language = SupportedLanguage.TYPESCRIPT,
            options = TYPE_TO_JSON_OPTIONS,
            rootTypeName = "Pair",
        )
        assertTrue(
            "TypeScript tuple은 진단을 남기지만 현재 생성 서비스는 실패하지 않고 알 수 없는 타입을 null로 낮춥니다.",
            objectMapper.readTree(generatedJson).isNull,
        )
    }

    private fun expectFailure(
        expectedMessageFragment: String,
        operation: () -> Unit,
    ): Throwable {
        try {
            operation()
        } catch (failure: Throwable) {
            assertTrue(
                "예외 메시지에 `$expectedMessageFragment`가 포함되어야 합니다. 실제 메시지: `${failure.message}`",
                failure.message.orEmpty().contains(expectedMessageFragment, ignoreCase = true),
            )
            return failure
        }
        throw AssertionError("`$expectedMessageFragment` 메시지를 가진 실패를 기대했지만 성공했습니다.")
    }

    private data class JsonToTypeSuccessCase(
        val caseName: String,
        val successReason: String,
        val jsonText: String,
        val language: SupportedLanguage,
        val rootTypeName: String,
        val expectedDeclarationNames: Set<String>,
        val expectedRenderedFragment: String,
    )

    private data class TypeToJsonSuccessCase(
        val caseName: String,
        val successReason: String,
        val sourceCode: String,
        val language: SupportedLanguage,
        val rootTypeName: String?,
        val assertJson: (JsonNode, TypeToJsonSuccessCase) -> Unit,
    )

    private data class JsonToTypeFailureCase(
        val caseName: String,
        val failureReason: String,
        val jsonText: String,
        val language: SupportedLanguage,
        val rootTypeName: String,
        val expectedMessageFragment: String,
    )

    private data class TypeToJsonFailureCase(
        val caseName: String,
        val failureReason: String,
        val sourceCode: String,
        val language: SupportedLanguage,
        val rootTypeName: String?,
        val options: TypeToJsonGenerationOptions,
        val expectedMessageFragment: String,
    )

    companion object {
        private val TYPE_TO_JSON_OPTIONS = TypeToJsonGenerationOptions(
            propertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL,
            includesNullableFieldWithNullValue = false,
            usesRealisticSampleData = false,
            outputCount = 1,
            formatState = JsonFormatState.PRETTIFY,
        )

        private val INVALID_OUTPUT_COUNT_OPTIONS = TYPE_TO_JSON_OPTIONS.copy(outputCount = 0)

        private val JSON_TO_TYPE_SUCCESS_CASES by lazy {
            listOf(
            JsonToTypeSuccessCase(
                caseName = "Kotlin tsconfig JSON with trailing comma",
                successReason = "공유 ObjectMapper가 trailing comma를 허용하고 중첩 객체를 별도 선언으로 추론하기 때문",
                jsonText = TS_CONFIG_JSON_TEXT,
                language = SupportedLanguage.KOTLIN,
                rootTypeName = "TsConfig",
                expectedDeclarationNames = setOf("TsConfig", "TsConfigCompilerOption", "TsConfigBuildOption"),
                expectedRenderedFragment = "data class TsConfig(",
            ),
            JsonToTypeSuccessCase(
                caseName = "Java tsconfig JSON with trailing comma",
                successReason = "공유 ObjectMapper가 trailing comma를 허용하고 Java class 렌더러가 중첩 선언을 class로 만들 수 있기 때문",
                jsonText = TS_CONFIG_JSON_TEXT,
                language = SupportedLanguage.JAVA,
                rootTypeName = "TsConfig",
                expectedDeclarationNames = setOf("TsConfig", "TsConfigCompilerOption", "TsConfigBuildOption"),
                expectedRenderedFragment = "public class TsConfig",
            ),
            JsonToTypeSuccessCase(
                caseName = "TypeScript array of mixed object shapes",
                successReason = "배열 내부 객체들의 필드를 병합하고 누락 필드를 optional로 표시할 수 있기 때문",
                jsonText = USER_ARRAY_JSON_TEXT,
                language = SupportedLanguage.TYPESCRIPT,
                rootTypeName = "Users",
                expectedDeclarationNames = setOf("Users", "UsersItem"),
                expectedRenderedFragment = "export type Users = UsersItem[]",
            ),
            JsonToTypeSuccessCase(
                caseName = "Go nested JSON object",
                successReason = "Go 기본 명명 규칙이 PascalCase이고 중첩 객체를 struct 선언으로 렌더링할 수 있기 때문",
                jsonText = PROFILE_JSON_TEXT,
                language = SupportedLanguage.GO,
                rootTypeName = "ProfileEnvelope",
                expectedDeclarationNames = setOf("ProfileEnvelope", "ProfileEnvelopeProfile"),
                expectedRenderedFragment = "type ProfileEnvelope struct",
            ),
        )
        }

        private val TYPE_TO_JSON_SUCCESS_CASES by lazy {
            listOf(
            TypeToJsonSuccessCase(
                caseName = "Kotlin implicit unique root",
                successReason = "Root만 다른 선언에서 참조되지 않는 유일한 선언이기 때문",
                sourceCode = KOTLIN_ROOT_DECLARATION_SOURCE,
                language = SupportedLanguage.KOTLIN,
                rootTypeName = null,
                assertJson = { generatedJsonNode, successCase ->
                    assertTrue(
                        "성공 케이스 `${successCase.caseName}`는 ${successCase.successReason} 때문에 buildOptions를 최상위에 포함해야 합니다.",
                        generatedJsonNode.has("buildOptions"),
                    )
                    assertTrue(generatedJsonNode.has("compilerOptions"))
                    assertFalse(generatedJsonNode.has("allowArbitraryExtensions"))
                    assertTrue(generatedJsonNode.path("compilerOptions").path("allowArbitraryExtensions").isBoolean)
                    assertTrue(generatedJsonNode.path("buildOptions").path("traceResolution").isBoolean)
                },
            ),
            TypeToJsonSuccessCase(
                caseName = "Kotlin explicit dependency root",
                successReason = "명시한 rootTypeName이 자동 루트 추론보다 우선하기 때문",
                sourceCode = KOTLIN_ROOT_DECLARATION_SOURCE,
                language = SupportedLanguage.KOTLIN,
                rootTypeName = "RootCompilerOption",
                assertJson = { generatedJsonNode, successCase ->
                    assertTrue(
                        "성공 케이스 `${successCase.caseName}`는 ${successCase.successReason} 때문에 RootCompilerOption 필드를 최상위에 포함해야 합니다.",
                        generatedJsonNode.has("allowArbitraryExtensions"),
                    )
                    assertFalse(generatedJsonNode.has("compilerOptions"))
                    assertTrue(generatedJsonNode.path("customConditions").isArray)
                },
            ),
            TypeToJsonSuccessCase(
                caseName = "TypeScript interface with map and array",
                successReason = "wasm 분석기가 interface, Record, 배열 타입을 도메인 타입으로 해석할 수 있기 때문",
                sourceCode = TYPESCRIPT_USER_RESPONSE_SOURCE,
                language = SupportedLanguage.TYPESCRIPT,
                rootTypeName = "UserResponse",
                assertJson = { generatedJsonNode, successCase ->
                    assertTrue(
                        "성공 케이스 `${successCase.caseName}`는 ${successCase.successReason} 때문에 metadata map을 객체로 생성해야 합니다.",
                        generatedJsonNode.path("metadata").isObject,
                    )
                    assertTrue(generatedJsonNode.path("tags").isArray)
                    assertTrue(generatedJsonNode.path("metadata").path("key").isBoolean)
                },
            ),
            TypeToJsonSuccessCase(
                caseName = "Go struct with map and array",
                successReason = "wasm 분석기가 Go struct, slice, map 타입을 도메인 타입으로 해석할 수 있기 때문",
                sourceCode = GO_USER_RESPONSE_SOURCE,
                language = SupportedLanguage.GO,
                rootTypeName = "UserResponse",
                assertJson = { generatedJsonNode, successCase ->
                    assertTrue(
                        "성공 케이스 `${successCase.caseName}`는 ${successCase.successReason} 때문에 Tags를 배열로 생성해야 합니다.",
                        generatedJsonNode.path("Tags").isArray,
                    )
                    assertTrue(generatedJsonNode.path("Metadata").isObject)
                    assertTrue(generatedJsonNode.path("Metadata").path("key").isBoolean)
                },
            ),
            TypeToJsonSuccessCase(
                caseName = "Java record with enum field",
                successReason = "wasm 분석기가 Java record 컴포넌트와 enum 값을 추출할 수 있기 때문",
                sourceCode = JAVA_RECORD_AND_ENUM_SOURCE,
                language = SupportedLanguage.JAVA,
                rootTypeName = "AuditRecord",
                assertJson = { generatedJsonNode, successCase ->
                    assertTrue(
                        "성공 케이스 `${successCase.caseName}`는 ${successCase.successReason} 때문에 record 필드를 JSON 필드로 생성해야 합니다.",
                        generatedJsonNode.path("name").isTextual,
                    )
                    assertEquals("READY", generatedJsonNode.path("status").asText())
                },
            ),
        )
        }

        private val JSON_TO_TYPE_FAILURE_CASES = listOf(
            JsonToTypeFailureCase(
                caseName = "blank JSON input",
                failureReason = "변환할 JSON 본문이 없기 때문",
                jsonText = "   ",
                language = SupportedLanguage.KOTLIN,
                rootTypeName = "Root",
                expectedMessageFragment = "JSON text must not be blank",
            ),
            JsonToTypeFailureCase(
                caseName = "Go invalid JSON with trailing token",
                failureReason = "언어가 Go여도 JSON 파서가 완전한 JSON 이후의 쓰레기 토큰을 거절하기 때문",
                jsonText = """{"enabled": true} trailing""",
                language = SupportedLanguage.GO,
                rootTypeName = "Root",
                expectedMessageFragment = "trailing",
            ),
            JsonToTypeFailureCase(
                caseName = "invalid root type name",
                failureReason = "루트 타입명이 식별자 규칙을 만족하지 않기 때문",
                jsonText = """{"enabled": true}""",
                language = SupportedLanguage.JAVA,
                rootTypeName = "123Root",
                expectedMessageFragment = "Root type name must be a valid identifier",
            ),
            JsonToTypeFailureCase(
                caseName = "invalid JSON with trailing token",
                failureReason = "공유 ObjectMapper가 완전한 JSON 이후의 쓰레기 토큰을 거절하기 때문",
                jsonText = """{"enabled": true} trailing""",
                language = SupportedLanguage.TYPESCRIPT,
                rootTypeName = "Root",
                expectedMessageFragment = "trailing",
            ),
        )

        private val TYPE_TO_JSON_FAILURE_CASES = listOf(
            TypeToJsonFailureCase(
                caseName = "blank type source",
                failureReason = "wasm 분석기에 넘길 타입 선언 본문이 없기 때문",
                sourceCode = "   ",
                language = SupportedLanguage.KOTLIN,
                rootTypeName = null,
                options = TYPE_TO_JSON_OPTIONS,
                expectedMessageFragment = "Type declaration source code must not be blank",
            ),
            TypeToJsonFailureCase(
                caseName = "invalid output count",
                failureReason = "출력 개수는 1에서 100 사이여야 하기 때문",
                sourceCode = "data class Root(val enabled: Boolean)",
                language = SupportedLanguage.KOTLIN,
                rootTypeName = null,
                options = INVALID_OUTPUT_COUNT_OPTIONS,
                expectedMessageFragment = "Output count must be between 1 and 100",
            ),
            TypeToJsonFailureCase(
                caseName = "source without declarations",
                failureReason = "wasm 분석 결과에 JSON 루트로 사용할 타입 선언이 없기 때문",
                sourceCode = "func helper() {}",
                language = SupportedLanguage.GO,
                rootTypeName = null,
                options = TYPE_TO_JSON_OPTIONS,
                expectedMessageFragment = "No type declarations found",
            ),
            TypeToJsonFailureCase(
                caseName = "malformed TypeScript declaration",
                failureReason = "wasm 분석기가 구문 오류 진단을 만들고 생성 서비스가 오류 진단을 거절하기 때문",
                sourceCode = "interface Broken {",
                language = SupportedLanguage.TYPESCRIPT,
                rootTypeName = null,
                options = TYPE_TO_JSON_OPTIONS,
                expectedMessageFragment = "syntax",
            ),
        )

        private val TS_CONFIG_JSON_TEXT = """
            {
              "compilerOptions": {
                "allowArbitraryExtensions": true,
                "allowImportingTsExtensions": true,
                "charset": "1QBlSNdO9bFO",
                "composite": true,
                "customConditions": [ ],
                "declaration": false
              },
              "buildOptions": {
                "dry": false,
                "force": false,
                "verbose": false,
                "incremental": false,
                "assumeChangesOnlyAffectDirectDependencies": false,
                "traceResolution": false
              },
            }
        """.trimIndent()

        private val USER_ARRAY_JSON_TEXT = """
            [
              {
                "id": 1,
                "name": "Ada",
                "email": "ada@example.com"
              },
              {
                "id": 2,
                "name": "Grace",
                "active": true
              }
            ]
        """.trimIndent()

        private val PROFILE_JSON_TEXT = """
            {
              "profile": {
                "name": "Ada",
                "tags": ["compiler", "math"],
                "metadata": {
                  "verified": true
                }
              }
            }
        """.trimIndent()

        private val KOTLIN_ROOT_DECLARATION_SOURCE = """
            data class RootCompilerOption(
                val allowArbitraryExtensions: Boolean,
                val allowImportingTsExtensions: Boolean,
                val charset: String,
                val composite: Boolean,
                val customConditions: List<Any>,
                val declaration: Boolean
            )

            data class RootBuildOption(
                val assumeChangesOnlyAffectDirectDependencies: Boolean,
                val dry: Boolean,
                val force: Boolean,
                val incremental: Boolean,
                val traceResolution: Boolean,
                val verbose: Boolean
            )

            data class Root(
                val buildOptions: RootBuildOption,
                val compilerOptions: RootCompilerOption
            )
        """.trimIndent()

        private val TYPESCRIPT_USER_RESPONSE_SOURCE = """
            interface UserResponse {
              name?: string | null;
              tags: string[];
              metadata: Record<string, boolean>;
            }
        """.trimIndent()

        private val GO_USER_RESPONSE_SOURCE = """
            type UserResponse struct {
                Name *string
                Tags []string
                Metadata map[string]bool
            }
        """.trimIndent()

        private val JAVA_RECORD_AND_ENUM_SOURCE = """
            record AuditRecord(String name, Status status) {}

            enum Status {
                READY,
                DONE
            }
        """.trimIndent()

        private val TYPESCRIPT_UNSUPPORTED_TUPLE_SOURCE = """
            type Pair = [string, number];
        """.trimIndent()
    }
}
