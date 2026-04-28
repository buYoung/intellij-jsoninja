package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.JsonToTypeConversionResult
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisResult
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeField
import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import com.livteam.jsoninja.model.typeConversion.TypeReference
import com.livteam.jsoninja.services.JsonObjectMapperService
import com.livteam.jsoninja.services.treesitter.TreeSitterWasmRuntime
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode

class TypeConversionWasmIntegrationTest : BasePlatformTestCase() {
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

    fun testJsonToTypeInfersTsConfigShapeForAllLanguages() {
        SupportedLanguage.entries.forEach { language ->
            val conversionResult = jsonToTypeConversionService.convertDetailed(
                jsonText = TS_CONFIG_JSON_TEXT,
                language = language,
                options = createJsonToTypeOptions(language),
            )

            assertTsConfigDeclarations(conversionResult, language)
            assertRenderedSourceMatchesLanguage(conversionResult.sourceCode, language)
        }
    }

    fun testTypeDeclarationAnalyzerInvokesBundledWasmForRepresentativeLanguages() {
        REPRESENTATIVE_SOURCE_CASES.forEach { sourceCase ->
            TreeSitterWasmRuntime.clear()

            val analysisResult = typeDeclarationAnalyzerService.analyzeSource(
                sourceCode = sourceCase.sourceCode,
                language = sourceCase.language,
            )

            assertTrue(
                "Expected no diagnostics for ${sourceCase.language}, but found ${analysisResult.diagnostics}",
                analysisResult.diagnostics.isEmpty(),
            )
            assertRepresentativeAnalysisResult(analysisResult, sourceCase.language)
        }
    }

    fun testTypeToJsonGenerationUsesBundledWasmAndBuildsDeterministicJson() {
        REPRESENTATIVE_SOURCE_CASES.forEach { sourceCase ->
            TreeSitterWasmRuntime.clear()

            val generatedJson = typeToJsonGenerationService.generate(
                sourceCode = sourceCase.sourceCode,
                language = sourceCase.language,
                options = TYPE_TO_JSON_OPTIONS,
                rootTypeName = "UserResponse",
            )

            val generatedJsonNode = objectMapper.readTree(generatedJson)
            assertGeneratedUserResponseJson(generatedJsonNode, sourceCase.language)
        }
    }

    fun testTypeToJsonGenerationSelectsRootDeclarationWhenDependenciesAppearFirst() {
        TreeSitterWasmRuntime.clear()

        val generatedJson = typeToJsonGenerationService.generate(
            sourceCode = KOTLIN_ROOT_DECLARATION_SOURCE,
            language = SupportedLanguage.KOTLIN,
            options = TYPE_TO_JSON_OPTIONS,
        )

        val generatedJsonNode = objectMapper.readTree(generatedJson)
        assertTrue(generatedJsonNode.has("buildOptions"))
        assertTrue(generatedJsonNode.has("compilerOptions"))
        assertFalse(generatedJsonNode.has("allowArbitraryExtensions"))

        val compilerOptionsNode = generatedJsonNode.path("compilerOptions")
        assertTrue(compilerOptionsNode.path("allowArbitraryExtensions").isBoolean)
        assertTrue(compilerOptionsNode.path("allowImportingTsExtensions").isBoolean)
        assertEquals("charset", compilerOptionsNode.path("charset").asText())
        assertTrue(compilerOptionsNode.path("customConditions").isArray)
        assertTrue(compilerOptionsNode.path("declaration").isBoolean)

        val buildOptionsNode = generatedJsonNode.path("buildOptions")
        assertTrue(buildOptionsNode.path("assumeChangesOnlyAffectDirectDependencies").isBoolean)
        assertTrue(buildOptionsNode.path("dry").isBoolean)
        assertTrue(buildOptionsNode.path("force").isBoolean)
        assertTrue(buildOptionsNode.path("incremental").isBoolean)
        assertTrue(buildOptionsNode.path("traceResolution").isBoolean)
        assertTrue(buildOptionsNode.path("verbose").isBoolean)
    }

    private fun createJsonToTypeOptions(language: SupportedLanguage): JsonToTypeConversionOptions {
        return JsonToTypeConversionOptions(
            rootTypeName = "TsConfig",
            namingConvention = language.defaultNamingConvention,
            annotationStyle = language.defaultAnnotationStyle,
            allowsNullableFields = true,
            usesExperimentalGoUnionTypes = false,
        )
    }

    private fun assertTsConfigDeclarations(
        conversionResult: JsonToTypeConversionResult,
        language: SupportedLanguage,
    ) {
        val rootDeclaration = conversionResult.requireDeclaration("TsConfig")
        val compilerOptionsDeclaration = conversionResult.requireDeclaration("TsConfigCompilerOption")
        val buildOptionsDeclaration = conversionResult.requireDeclaration("TsConfigBuildOption")

        val compilerOptionsField = rootDeclaration.requireFieldBySourceName("compilerOptions")
        val buildOptionsField = rootDeclaration.requireFieldBySourceName("buildOptions")
        assertNamedType(compilerOptionsField.typeReference, "TsConfigCompilerOption")
        assertNamedType(buildOptionsField.typeReference, "TsConfigBuildOption")

        assertPrimitiveType(
            compilerOptionsDeclaration.requireFieldBySourceName("allowArbitraryExtensions").typeReference,
            TypePrimitiveKind.BOOLEAN,
        )
        assertPrimitiveType(
            compilerOptionsDeclaration.requireFieldBySourceName("allowImportingTsExtensions").typeReference,
            TypePrimitiveKind.BOOLEAN,
        )
        assertPrimitiveType(
            compilerOptionsDeclaration.requireFieldBySourceName("charset").typeReference,
            TypePrimitiveKind.STRING,
        )
        assertListType(compilerOptionsDeclaration.requireFieldBySourceName("customConditions").typeReference)
        assertPrimitiveType(
            compilerOptionsDeclaration.requireFieldBySourceName("declaration").typeReference,
            TypePrimitiveKind.BOOLEAN,
        )

        listOf("dry", "force", "verbose", "incremental", "assumeChangesOnlyAffectDirectDependencies", "traceResolution")
            .forEach { fieldSourceName ->
                assertPrimitiveType(
                    buildOptionsDeclaration.requireFieldBySourceName(fieldSourceName).typeReference,
                    TypePrimitiveKind.BOOLEAN,
                )
            }

        when (language) {
            SupportedLanguage.TYPESCRIPT -> assertEquals(TypeDeclarationKind.INTERFACE, rootDeclaration.declarationKind)
            SupportedLanguage.GO -> assertEquals(TypeDeclarationKind.STRUCT, rootDeclaration.declarationKind)
            SupportedLanguage.JAVA, SupportedLanguage.KOTLIN -> {
                assertEquals(TypeDeclarationKind.CLASS, rootDeclaration.declarationKind)
            }
        }
    }

    private fun assertRenderedSourceMatchesLanguage(
        sourceCode: String,
        language: SupportedLanguage,
    ) {
        val expectedFragment = when (language) {
            SupportedLanguage.KOTLIN -> "data class TsConfig("
            SupportedLanguage.JAVA -> "public class TsConfig"
            SupportedLanguage.GO -> "type TsConfig struct"
            SupportedLanguage.TYPESCRIPT -> "export interface TsConfig"
        }
        assertTrue(
            "Expected rendered ${language.name} source to contain `$expectedFragment`, but was:\n$sourceCode",
            sourceCode.contains(expectedFragment),
        )
    }

    private fun assertRepresentativeAnalysisResult(
        analysisResult: TypeAnalysisResult,
        language: SupportedLanguage,
    ) {
        val userResponseDeclaration = analysisResult.requireDeclaration("UserResponse")
        assertEquals(expectedUserResponseDeclarationKind(language), userResponseDeclaration.declarationKind)

        val nameField = userResponseDeclaration.requireFieldBySourceName(expectedNameFieldSourceName(language))
        val tagsField = userResponseDeclaration.requireFieldBySourceName(expectedTagsFieldSourceName(language))
        val metadataField = userResponseDeclaration.requireFieldBySourceName(expectedMetadataFieldSourceName(language))

        assertNameFieldType(nameField.typeReference, language)
        assertPrimitiveListType(tagsField.typeReference, TypePrimitiveKind.STRING)
        assertStringBooleanMapType(metadataField.typeReference)

        when (language) {
            SupportedLanguage.GO -> {
                val lookupDeclaration = analysisResult.requireDeclaration("UserLookup")
                assertEquals(TypeDeclarationKind.TYPE_ALIAS, lookupDeclaration.declarationKind)
                assertStringNamedMapType(lookupDeclaration.aliasedTypeReference, "UserResponse")
            }
            else -> {
                val statusDeclaration = analysisResult.requireDeclaration("Status")
                assertEquals(TypeDeclarationKind.ENUM, statusDeclaration.declarationKind)
                assertTrue(statusDeclaration.enumValues.contains("READY"))
                assertTrue(statusDeclaration.enumValues.contains("DONE"))
            }
        }
    }

    private fun assertGeneratedUserResponseJson(
        generatedJsonNode: JsonNode,
        language: SupportedLanguage,
    ) {
        assertTrue("Generated ${language.name} JSON should be an object.", generatedJsonNode.isObject)

        val nameFieldSourceName = expectedNameFieldSourceName(language)
        val tagsFieldSourceName = expectedTagsFieldSourceName(language)
        val metadataFieldSourceName = expectedMetadataFieldSourceName(language)

        assertTrue(generatedJsonNode.has(nameFieldSourceName))
        assertTrue(generatedJsonNode.path(nameFieldSourceName).isTextual)

        assertTrue(generatedJsonNode.has(tagsFieldSourceName))
        assertTrue(generatedJsonNode.path(tagsFieldSourceName).isArray)
        assertTrue(generatedJsonNode.path(tagsFieldSourceName).first().isTextual)

        assertTrue(generatedJsonNode.has(metadataFieldSourceName))
        assertTrue(generatedJsonNode.path(metadataFieldSourceName).isObject)
        assertTrue(generatedJsonNode.path(metadataFieldSourceName).path("key").isBoolean)
    }

    private fun expectedUserResponseDeclarationKind(language: SupportedLanguage): TypeDeclarationKind {
        return when (language) {
            SupportedLanguage.GO -> TypeDeclarationKind.STRUCT
            SupportedLanguage.TYPESCRIPT -> TypeDeclarationKind.INTERFACE
            SupportedLanguage.JAVA, SupportedLanguage.KOTLIN -> TypeDeclarationKind.CLASS
        }
    }

    private fun expectedNameFieldSourceName(language: SupportedLanguage): String {
        return when (language) {
            SupportedLanguage.GO -> "Name"
            else -> "name"
        }
    }

    private fun expectedTagsFieldSourceName(language: SupportedLanguage): String {
        return when (language) {
            SupportedLanguage.GO -> "Tags"
            else -> "tags"
        }
    }

    private fun expectedMetadataFieldSourceName(language: SupportedLanguage): String {
        return when (language) {
            SupportedLanguage.GO -> "Metadata"
            else -> "metadata"
        }
    }

    private fun assertNameFieldType(
        typeReference: TypeReference,
        language: SupportedLanguage,
    ) {
        when (language) {
            SupportedLanguage.KOTLIN, SupportedLanguage.GO, SupportedLanguage.TYPESCRIPT -> {
                val nullableTypeReference = typeReference as? TypeReference.Nullable
                    ?: throw AssertionError("Expected nullable string type, but was $typeReference")
                assertPrimitiveType(nullableTypeReference.wrappedType, TypePrimitiveKind.STRING)
            }
            SupportedLanguage.JAVA -> assertPrimitiveType(typeReference, TypePrimitiveKind.STRING)
        }
    }

    private fun assertPrimitiveType(
        typeReference: TypeReference,
        primitiveKind: TypePrimitiveKind,
    ) {
        val primitiveTypeReference = typeReference as? TypeReference.Primitive
            ?: throw AssertionError("Expected primitive $primitiveKind type, but was $typeReference")
        assertEquals(primitiveKind, primitiveTypeReference.primitiveKind)
    }

    private fun assertNamedType(
        typeReference: TypeReference,
        typeName: String,
    ) {
        val namedTypeReference = typeReference as? TypeReference.Named
            ?: throw AssertionError("Expected named $typeName type, but was $typeReference")
        assertEquals(typeName, namedTypeReference.name)
    }

    private fun assertListType(typeReference: TypeReference) {
        if (typeReference !is TypeReference.ListReference) {
            throw AssertionError("Expected list type, but was $typeReference")
        }
    }

    private fun assertPrimitiveListType(
        typeReference: TypeReference,
        primitiveKind: TypePrimitiveKind,
    ) {
        val listTypeReference = typeReference as? TypeReference.ListReference
            ?: throw AssertionError("Expected list type, but was $typeReference")
        assertPrimitiveType(listTypeReference.elementType, primitiveKind)
    }

    private fun assertStringBooleanMapType(typeReference: TypeReference) {
        val mapTypeReference = typeReference as? TypeReference.MapReference
            ?: throw AssertionError("Expected map type, but was $typeReference")
        assertPrimitiveType(mapTypeReference.keyType, TypePrimitiveKind.STRING)
        assertPrimitiveType(mapTypeReference.valueType, TypePrimitiveKind.BOOLEAN)
    }

    private fun assertStringNamedMapType(
        typeReference: TypeReference?,
        valueTypeName: String,
    ) {
        val mapTypeReference = typeReference as? TypeReference.MapReference
            ?: throw AssertionError("Expected map type, but was $typeReference")
        assertPrimitiveType(mapTypeReference.keyType, TypePrimitiveKind.STRING)
        assertNamedType(mapTypeReference.valueType, valueTypeName)
    }

    private fun JsonToTypeConversionResult.requireDeclaration(declarationName: String): TypeDeclaration {
        return declarations.firstOrNull { it.name == declarationName }
            ?: throw AssertionError(
                "Expected declaration `$declarationName`, but found ${declarations.map(TypeDeclaration::name)}",
            )
    }

    private fun TypeAnalysisResult.requireDeclaration(declarationName: String): TypeDeclaration {
        return declarations.firstOrNull { it.name == declarationName }
            ?: throw AssertionError(
                "Expected declaration `$declarationName`, but found ${declarations.map(TypeDeclaration::name)}",
            )
    }

    private fun TypeDeclaration.requireFieldBySourceName(fieldSourceName: String): TypeField {
        return fields.firstOrNull { it.sourceName == fieldSourceName }
            ?: throw AssertionError(
                "Expected field with source name `$fieldSourceName` in `${name}`, but found ${fields.map(TypeField::sourceName)}",
            )
    }

    private data class RepresentativeSourceCase(
        val language: SupportedLanguage,
        val sourceCode: String,
    )

    companion object {
        private val TYPE_TO_JSON_OPTIONS = TypeToJsonGenerationOptions(
            propertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL,
            includesNullableFieldWithNullValue = false,
            usesRealisticSampleData = false,
            outputCount = 1,
            formatState = JsonFormatState.PRETTIFY,
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

        private val REPRESENTATIVE_SOURCE_CASES = listOf(
            RepresentativeSourceCase(
                language = SupportedLanguage.KOTLIN,
                sourceCode = """
                    data class UserResponse(
                        val name: String?,
                        val tags: List<String>,
                        val metadata: Map<String, Boolean>,
                    )

                    enum class Status {
                        READY,
                        DONE,
                    }
                """.trimIndent(),
            ),
            RepresentativeSourceCase(
                language = SupportedLanguage.JAVA,
                sourceCode = """
                    import java.util.List;
                    import java.util.Map;

                    class UserResponse {
                        String name;
                        List<String> tags;
                        Map<String, Boolean> metadata;
                    }

                    enum Status {
                        READY,
                        DONE
                    }
                """.trimIndent(),
            ),
            RepresentativeSourceCase(
                language = SupportedLanguage.GO,
                sourceCode = """
                    type UserLookup map[string]UserResponse

                    type UserResponse struct {
                        Name *string
                        Tags []string
                        Metadata map[string]bool
                    }
                """.trimIndent(),
            ),
            RepresentativeSourceCase(
                language = SupportedLanguage.TYPESCRIPT,
                sourceCode = """
                    type UserLookup = Record<string, UserResponse>;

                    interface UserResponse {
                      name?: string | null;
                      tags: string[];
                      metadata: Record<string, boolean>;
                    }

                    enum Status {
                      READY = "READY",
                      DONE = "DONE",
                    }
                """.trimIndent(),
            ),
        )

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
    }
}
