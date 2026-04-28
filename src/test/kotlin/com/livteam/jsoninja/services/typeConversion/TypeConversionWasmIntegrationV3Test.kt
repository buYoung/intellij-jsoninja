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

class TypeConversionWasmIntegrationV3Test : BasePlatformTestCase() {
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

    fun testComplexJsonToTypeDatasetForAllLanguagesV3() {
        SupportedLanguage.entries.forEach { language ->
            val conversionResult = jsonToTypeConversionService.convertDetailed(
                jsonText = COMPLEX_WORKSPACE_REPORT_JSON_TEXT,
                language = language,
                options = createJsonToTypeOptions(language),
            )

            assertComplexJsonToTypeDeclarations(conversionResult, language)
            assertComplexJsonToTypeRendering(conversionResult.sourceCode, language)
        }
    }

    fun testComplexTypeToJsonDatasetUsesWasmForAllLanguagesV3() {
        COMPLEX_TYPE_TO_JSON_CASES.forEach { conversionCase ->
            TreeSitterWasmRuntime.clear()

            val analysisResult = typeDeclarationAnalyzerService.analyzeSource(
                sourceCode = conversionCase.sourceCode,
                language = conversionCase.language,
            )
            assertComplexTypeAnalysisResult(analysisResult, conversionCase)

            TreeSitterWasmRuntime.clear()

            val generatedJson = typeToJsonGenerationService.generate(
                sourceCode = conversionCase.sourceCode,
                language = conversionCase.language,
                options = TYPE_TO_JSON_OPTIONS,
                rootTypeName = "WorkspaceEnvelope",
            )
            val generatedJsonNode = objectMapper.readTree(generatedJson)

            assertComplexGeneratedJson(generatedJsonNode, conversionCase)
        }
    }

    private fun createJsonToTypeOptions(language: SupportedLanguage): JsonToTypeConversionOptions {
        return JsonToTypeConversionOptions(
            rootTypeName = "WorkspaceReport",
            namingConvention = language.defaultNamingConvention,
            annotationStyle = language.defaultAnnotationStyle,
            allowsNullableFields = true,
            usesExperimentalGoUnionTypes = false,
        )
    }

    private fun assertComplexJsonToTypeDeclarations(
        conversionResult: JsonToTypeConversionResult,
        language: SupportedLanguage,
    ) {
        val rootDeclaration = conversionResult.requireDeclaration("WorkspaceReport")
        val workspaceDeclaration = conversionResult.requireDeclaration("WorkspaceReportWorkspace")
        val limitDeclaration = conversionResult.requireDeclaration("WorkspaceReportWorkspaceLimit")
        val ownerDeclaration = conversionResult.requireDeclaration("WorkspaceReportWorkspaceOwnerItem")
        val preferenceDeclaration = conversionResult.requireDeclaration("WorkspaceReportWorkspaceOwnerItemPreference")
        val notificationDeclaration = conversionResult.requireDeclaration(
            "WorkspaceReportWorkspaceOwnerItemPreferenceNotification",
        )
        val featureFlagDeclaration = conversionResult.requireDeclaration("WorkspaceReportWorkspaceFeatureFlag")
        val auditDeclaration = conversionResult.requireDeclaration("WorkspaceReportAudit")
        val auditEventDeclaration = conversionResult.requireDeclaration("WorkspaceReportAuditEventItem")
        val metadataDeclaration = conversionResult.requireDeclaration("WorkspaceReportAuditEventItemMetadata")

        assertEquals(expectedJsonToTypeDeclarationKind(language), rootDeclaration.declarationKind)
        assertEquals(expectedJsonToTypeDeclarationKind(language), workspaceDeclaration.declarationKind)
        assertEquals(expectedJsonToTypeDeclarationKind(language), auditDeclaration.declarationKind)

        assertNamedType(rootDeclaration.requireFieldBySourceName("workspace").typeReference, "WorkspaceReportWorkspace")
        assertNamedType(rootDeclaration.requireFieldBySourceName("audit").typeReference, "WorkspaceReportAudit")
        assertNullableAnyType(rootDeclaration.requireFieldBySourceName("nullableNote").typeReference)
        assertListOfNullablePrimitiveType(
            rootDeclaration.requireFieldBySourceName("thresholds").typeReference,
            TypePrimitiveKind.NUMBER,
        )
        assertNestedPrimitiveListType(rootDeclaration.requireFieldBySourceName("matrix").typeReference)

        assertPrimitiveType(workspaceDeclaration.requireFieldBySourceName("id").typeReference, TypePrimitiveKind.STRING)
        assertPrimitiveType(
            workspaceDeclaration.requireFieldBySourceName("enabled").typeReference,
            TypePrimitiveKind.BOOLEAN,
        )
        assertNamedType(workspaceDeclaration.requireFieldBySourceName("limits").typeReference, limitDeclaration.name)
        assertListOfNamedType(workspaceDeclaration.requireFieldBySourceName("owners").typeReference, ownerDeclaration.name)
        assertNamedType(
            workspaceDeclaration.requireFieldBySourceName("featureFlags").typeReference,
            featureFlagDeclaration.name,
        )

        assertPrimitiveType(limitDeclaration.requireFieldBySourceName("maxUsers").typeReference, TypePrimitiveKind.INTEGER)
        assertPrimitiveType(limitDeclaration.requireFieldBySourceName("storageGb").typeReference, TypePrimitiveKind.DECIMAL)

        assertPrimitiveType(ownerDeclaration.requireFieldBySourceName("id").typeReference, TypePrimitiveKind.INTEGER)
        assertPrimitiveType(ownerDeclaration.requireFieldBySourceName("name").typeReference, TypePrimitiveKind.STRING)
        assertListOfPrimitiveType(ownerDeclaration.requireFieldBySourceName("roles").typeReference, TypePrimitiveKind.STRING)
        val emailField = ownerDeclaration.requireFieldBySourceName("email")
        assertTrue("두 번째 owner에 email이 없으므로 optional이어야 합니다.", emailField.isOptional)
        assertNullablePrimitiveType(emailField.typeReference, TypePrimitiveKind.STRING)
        assertNamedType(ownerDeclaration.requireFieldBySourceName("preferences").typeReference, preferenceDeclaration.name)

        assertNullablePrimitiveType(preferenceDeclaration.requireFieldBySourceName("theme").typeReference, TypePrimitiveKind.STRING)
        assertNamedType(
            preferenceDeclaration.requireFieldBySourceName("notifications").typeReference,
            notificationDeclaration.name,
        )
        assertPrimitiveType(notificationDeclaration.requireFieldBySourceName("email").typeReference, TypePrimitiveKind.BOOLEAN)
        val smsField = notificationDeclaration.requireFieldBySourceName("sms")
        assertTrue("두 번째 notifications에 sms가 없으므로 optional이어야 합니다.", smsField.isOptional)
        assertNullablePrimitiveType(smsField.typeReference, TypePrimitiveKind.BOOLEAN)

        val searchFlagType = featureFlagDeclaration.requireFieldBySourceName("search").typeReference
        val billingFlagType = featureFlagDeclaration.requireFieldBySourceName("billing").typeReference
        val searchFlagTypeName = assertNamedType(searchFlagType)
        val billingFlagTypeName = assertNamedType(billingFlagType)
        listOf(searchFlagTypeName, billingFlagTypeName).forEach { featureFlagItemTypeName ->
            val featureFlagItemDeclaration = conversionResult.requireDeclaration(featureFlagItemTypeName)
            assertPrimitiveType(
                featureFlagItemDeclaration.requireFieldBySourceName("enabled").typeReference,
                TypePrimitiveKind.BOOLEAN,
            )
            assertNumericPrimitiveType(featureFlagItemDeclaration.requireFieldBySourceName("rollout").typeReference)
        }

        assertListOfNamedType(auditDeclaration.requireFieldBySourceName("events").typeReference, auditEventDeclaration.name)
        assertListOfPrimitiveType(auditDeclaration.requireFieldBySourceName("labels").typeReference, TypePrimitiveKind.STRING)
        assertPrimitiveType(auditEventDeclaration.requireFieldBySourceName("name").typeReference, TypePrimitiveKind.STRING)
        assertNullablePrimitiveType(
            auditEventDeclaration.requireFieldBySourceName("timestamp").typeReference,
            TypePrimitiveKind.STRING,
        )
        assertNamedType(auditEventDeclaration.requireFieldBySourceName("metadata").typeReference, metadataDeclaration.name)
        assertPrimitiveType(metadataDeclaration.requireFieldBySourceName("source").typeReference, TypePrimitiveKind.STRING)
        val retryField = metadataDeclaration.requireFieldBySourceName("retry")
        val attemptField = metadataDeclaration.requireFieldBySourceName("attempt")
        assertTrue("두 번째 metadata에 retry가 없으므로 optional이어야 합니다.", retryField.isOptional)
        assertTrue("첫 번째 metadata에 attempt가 없으므로 optional이어야 합니다.", attemptField.isOptional)
        assertNullablePrimitiveType(retryField.typeReference, TypePrimitiveKind.BOOLEAN)
        assertNullablePrimitiveType(attemptField.typeReference, TypePrimitiveKind.INTEGER)
    }

    private fun assertComplexJsonToTypeRendering(
        sourceCode: String,
        language: SupportedLanguage,
    ) {
        val expectedFragments = when (language) {
            SupportedLanguage.KOTLIN -> listOf(
                "data class WorkspaceReport(",
                "val owners: List<WorkspaceReportWorkspaceOwnerItem>",
            )
            SupportedLanguage.JAVA -> listOf(
                "public class WorkspaceReport",
                "private List<WorkspaceReportWorkspaceOwnerItem> owners;",
            )
            SupportedLanguage.GO -> listOf(
                "type WorkspaceReport struct",
                "Owners []WorkspaceReportWorkspaceOwnerItem",
            )
            SupportedLanguage.TYPESCRIPT -> listOf(
                "export interface WorkspaceReport",
                "owners: WorkspaceReportWorkspaceOwnerItem[];",
            )
        }

        expectedFragments.forEach { expectedFragment ->
            assertTrue(
                "${language.name} 렌더링은 복합 데이터셋의 핵심 조각 `$expectedFragment`를 포함해야 합니다.\n$sourceCode",
                sourceCode.contains(expectedFragment),
            )
        }
    }

    private fun assertComplexTypeAnalysisResult(
        analysisResult: TypeAnalysisResult,
        conversionCase: ComplexTypeToJsonCase,
    ) {
        assertTrue(
            "${conversionCase.language.name} 복합 타입 선언은 지원되는 타입 조합만 사용하므로 진단이 없어야 합니다. 실제 진단: ${analysisResult.diagnostics}",
            analysisResult.diagnostics.isEmpty(),
        )

        val rootDeclaration = analysisResult.requireDeclaration("WorkspaceEnvelope")
        val memberDeclaration = analysisResult.requireDeclaration("WorkspaceMember")
        val limitDeclaration = analysisResult.requireDeclaration("LimitProfile")
        val contactDeclaration = analysisResult.requireDeclaration("Contact")
        val planStateDeclaration = analysisResult.requireDeclaration("PlanState")
        val fieldNames = conversionCase.fieldNames

        assertEquals(conversionCase.expectedRootDeclarationKind, rootDeclaration.declarationKind)
        assertNamedType(rootDeclaration.requireFieldBySourceName(fieldNames.state).typeReference, "PlanState")
        assertListOfNamedType(rootDeclaration.requireFieldBySourceName(fieldNames.members).typeReference, "WorkspaceMember")
        assertMapOfNamedValueType(
            rootDeclaration.requireFieldBySourceName(fieldNames.limitsByRegion).typeReference,
            "LimitProfile",
        )
        assertAuditTrailType(
            typeReference = rootDeclaration.requireFieldBySourceName(fieldNames.auditTrail).typeReference,
            analysisResult = analysisResult,
        )
        assertMapOfStringListType(rootDeclaration.requireFieldBySourceName(fieldNames.extra).typeReference)
        assertInlineSettingsType(rootDeclaration.requireFieldBySourceName(fieldNames.inlineSettings).typeReference, conversionCase)

        assertPrimitiveType(
            memberDeclaration.requireFieldBySourceName(fieldNames.memberId).typeReference,
            conversionCase.expectedMemberIdPrimitiveKind,
        )
        assertPrimitiveType(
            memberDeclaration.requireFieldBySourceName(fieldNames.memberDisplayName).typeReference,
            TypePrimitiveKind.STRING,
        )
        assertListOfPrimitiveType(
            memberDeclaration.requireFieldBySourceName(fieldNames.memberRoles).typeReference,
            TypePrimitiveKind.STRING,
        )
        assertNullableNamedType(memberDeclaration.requireFieldBySourceName(fieldNames.memberContact).typeReference, contactDeclaration.name)

        assertNullablePrimitiveType(contactDeclaration.requireFieldBySourceName(fieldNames.contactEmail).typeReference, TypePrimitiveKind.STRING)
        assertListOfPrimitiveType(
            contactDeclaration.requireFieldBySourceName(fieldNames.contactPhones).typeReference,
            TypePrimitiveKind.STRING,
        )

        assertPrimitiveType(
            limitDeclaration.requireFieldBySourceName(fieldNames.limitMaxUsers).typeReference,
            conversionCase.expectedLimitMaxUsersPrimitiveKind,
        )
        assertPrimitiveType(
            limitDeclaration.requireFieldBySourceName(fieldNames.limitStorageGb).typeReference,
            conversionCase.expectedLimitStorageGbPrimitiveKind,
        )
        assertMapOfPrimitiveValueType(
            limitDeclaration.requireFieldBySourceName(fieldNames.limitFlags).typeReference,
            TypePrimitiveKind.BOOLEAN,
        )

        when (conversionCase.language) {
            SupportedLanguage.GO -> {
                assertEquals(TypeDeclarationKind.TYPE_ALIAS, planStateDeclaration.declarationKind)
                assertPrimitiveType(
                    planStateDeclaration.aliasedTypeReference ?: TypeReference.AnyValue,
                    TypePrimitiveKind.STRING,
                )
            }
            else -> {
                assertEquals(TypeDeclarationKind.ENUM, planStateDeclaration.declarationKind)
                assertTrue(planStateDeclaration.enumValues.contains("DRAFT"))
                assertTrue(planStateDeclaration.enumValues.contains("ACTIVE"))
            }
        }
    }

    private fun assertInlineSettingsType(
        typeReference: TypeReference,
        conversionCase: ComplexTypeToJsonCase,
    ) {
        when (conversionCase.language) {
            SupportedLanguage.GO, SupportedLanguage.TYPESCRIPT -> {
                val inlineObjectType = typeReference as? TypeReference.InlineObject
                    ?: throw AssertionError("inlineSettings는 inline object여야 합니다. 실제 타입: $typeReference")
                val compactField = inlineObjectType.requireFieldBySourceName(conversionCase.fieldNames.inlineCompact)
                val labelsField = inlineObjectType.requireFieldBySourceName(conversionCase.fieldNames.inlineLabels)
                assertPrimitiveType(compactField.typeReference, TypePrimitiveKind.BOOLEAN)
                assertListOfPrimitiveType(labelsField.typeReference, TypePrimitiveKind.STRING)
            }
            SupportedLanguage.KOTLIN, SupportedLanguage.JAVA -> {
                assertNamedType(typeReference, "InlineSettings")
            }
        }
    }

    private fun assertAuditTrailType(
        typeReference: TypeReference,
        analysisResult: TypeAnalysisResult,
    ) {
        if (typeReference is TypeReference.Named && typeReference.name == "AuditTrail") {
            val auditTrailDeclaration = analysisResult.requireDeclaration("AuditTrail")
            assertEquals(TypeDeclarationKind.TYPE_ALIAS, auditTrailDeclaration.declarationKind)
            assertListOfStringMapType(auditTrailDeclaration.aliasedTypeReference ?: TypeReference.AnyValue)
            return
        }

        assertListOfStringMapType(typeReference)
    }

    private fun assertComplexGeneratedJson(
        generatedJsonNode: JsonNode,
        conversionCase: ComplexTypeToJsonCase,
    ) {
        val fieldNames = conversionCase.fieldNames

        assertTrue("${conversionCase.language.name} 생성 결과는 객체여야 합니다.", generatedJsonNode.isObject)
        assertTrue(generatedJsonNode.path(fieldNames.workspaceId).isTextual)
        assertTrue(generatedJsonNode.path(fieldNames.state).isTextual)

        val memberNode = generatedJsonNode.path(fieldNames.members).first()
        assertTrue(memberNode.path(fieldNames.memberId).isIntegralNumber)
        assertTrue(memberNode.path(fieldNames.memberDisplayName).isTextual)
        assertTrue(memberNode.path(fieldNames.memberRoles).isArray)
        assertTrue(memberNode.path(fieldNames.memberRoles).first().isTextual)

        val contactNode = memberNode.path(fieldNames.memberContact)
        assertTrue(contactNode.path(fieldNames.contactEmail).isTextual)
        assertTrue(contactNode.path(fieldNames.contactPhones).isArray)
        assertTrue(contactNode.path(fieldNames.contactPhones).first().isTextual)

        val limitNode = generatedJsonNode.path(fieldNames.limitsByRegion).path("key")
        assertTrue(limitNode.path(fieldNames.limitMaxUsers).isIntegralNumber)
        assertTrue(limitNode.path(fieldNames.limitStorageGb).isNumber)
        assertTrue(limitNode.path(fieldNames.limitFlags).path("key").isBoolean)

        val auditTrailNode = generatedJsonNode.path(fieldNames.auditTrail).first()
        assertTrue(auditTrailNode.path("key").isTextual)

        val extraNode = generatedJsonNode.path(fieldNames.extra).path("key")
        assertTrue(extraNode.isArray)
        assertTrue(extraNode.first().isTextual)

        val inlineSettingsNode = generatedJsonNode.path(fieldNames.inlineSettings)
        assertTrue(inlineSettingsNode.path(fieldNames.inlineCompact).isBoolean)
        assertTrue(inlineSettingsNode.path(fieldNames.inlineLabels).isArray)
        assertTrue(inlineSettingsNode.path(fieldNames.inlineLabels).first().isTextual)
    }

    private fun expectedJsonToTypeDeclarationKind(language: SupportedLanguage): TypeDeclarationKind {
        return when (language) {
            SupportedLanguage.KOTLIN, SupportedLanguage.JAVA -> TypeDeclarationKind.CLASS
            SupportedLanguage.GO -> TypeDeclarationKind.STRUCT
            SupportedLanguage.TYPESCRIPT -> TypeDeclarationKind.INTERFACE
        }
    }

    private fun assertPrimitiveType(
        typeReference: TypeReference,
        primitiveKind: TypePrimitiveKind,
    ) {
        val primitiveTypeReference = typeReference as? TypeReference.Primitive
            ?: throw AssertionError("primitive $primitiveKind 타입이어야 합니다. 실제 타입: $typeReference")
        assertEquals(primitiveKind, primitiveTypeReference.primitiveKind)
    }

    private fun assertNumericPrimitiveType(typeReference: TypeReference) {
        val primitiveTypeReference = typeReference as? TypeReference.Primitive
            ?: throw AssertionError("numeric primitive 타입이어야 합니다. 실제 타입: $typeReference")
        assertTrue(
            primitiveTypeReference.primitiveKind in setOf(
                TypePrimitiveKind.INTEGER,
                TypePrimitiveKind.DECIMAL,
                TypePrimitiveKind.NUMBER,
            ),
        )
    }

    private fun assertNamedType(typeReference: TypeReference): String {
        val namedTypeReference = typeReference as? TypeReference.Named
            ?: throw AssertionError("named 타입이어야 합니다. 실제 타입: $typeReference")
        return namedTypeReference.name
    }

    private fun assertNamedType(
        typeReference: TypeReference,
        typeName: String,
    ) {
        assertEquals(typeName, assertNamedType(typeReference))
    }

    private fun assertNullableAnyType(typeReference: TypeReference) {
        val nullableTypeReference = typeReference as? TypeReference.Nullable
            ?: throw AssertionError("nullable any 타입이어야 합니다. 실제 타입: $typeReference")
        assertEquals(TypeReference.AnyValue, nullableTypeReference.wrappedType)
    }

    private fun assertNullablePrimitiveType(
        typeReference: TypeReference,
        primitiveKind: TypePrimitiveKind,
    ) {
        val nullableTypeReference = typeReference as? TypeReference.Nullable
            ?: throw AssertionError("nullable primitive 타입이어야 합니다. 실제 타입: $typeReference")
        assertPrimitiveType(nullableTypeReference.wrappedType, primitiveKind)
    }

    private fun assertNullableNamedType(
        typeReference: TypeReference,
        typeName: String,
    ) {
        val nullableTypeReference = typeReference as? TypeReference.Nullable
            ?: throw AssertionError("nullable named 타입이어야 합니다. 실제 타입: $typeReference")
        assertNamedType(nullableTypeReference.wrappedType, typeName)
    }

    private fun assertListOfNamedType(
        typeReference: TypeReference,
        typeName: String,
    ) {
        val listTypeReference = typeReference as? TypeReference.ListReference
            ?: throw AssertionError("list 타입이어야 합니다. 실제 타입: $typeReference")
        assertNamedType(listTypeReference.elementType, typeName)
    }

    private fun assertListOfPrimitiveType(
        typeReference: TypeReference,
        primitiveKind: TypePrimitiveKind,
    ) {
        val listTypeReference = typeReference as? TypeReference.ListReference
            ?: throw AssertionError("list 타입이어야 합니다. 실제 타입: $typeReference")
        assertPrimitiveType(listTypeReference.elementType, primitiveKind)
    }

    private fun assertListOfNullablePrimitiveType(
        typeReference: TypeReference,
        primitiveKind: TypePrimitiveKind,
    ) {
        val listTypeReference = typeReference as? TypeReference.ListReference
            ?: throw AssertionError("list 타입이어야 합니다. 실제 타입: $typeReference")
        assertNullablePrimitiveType(listTypeReference.elementType, primitiveKind)
    }

    private fun assertNestedPrimitiveListType(typeReference: TypeReference) {
        val outerListTypeReference = typeReference as? TypeReference.ListReference
            ?: throw AssertionError("중첩 list 타입이어야 합니다. 실제 타입: $typeReference")
        assertListOfPrimitiveType(outerListTypeReference.elementType, TypePrimitiveKind.INTEGER)
    }

    private fun assertMapOfNamedValueType(
        typeReference: TypeReference,
        valueTypeName: String,
    ) {
        val mapTypeReference = typeReference as? TypeReference.MapReference
            ?: throw AssertionError("map 타입이어야 합니다. 실제 타입: $typeReference")
        assertPrimitiveType(mapTypeReference.keyType, TypePrimitiveKind.STRING)
        assertNamedType(mapTypeReference.valueType, valueTypeName)
    }

    private fun assertMapOfPrimitiveValueType(
        typeReference: TypeReference,
        primitiveKind: TypePrimitiveKind,
    ) {
        val mapTypeReference = typeReference as? TypeReference.MapReference
            ?: throw AssertionError("map 타입이어야 합니다. 실제 타입: $typeReference")
        assertPrimitiveType(mapTypeReference.keyType, TypePrimitiveKind.STRING)
        assertPrimitiveType(mapTypeReference.valueType, primitiveKind)
    }

    private fun assertListOfStringMapType(typeReference: TypeReference) {
        val listTypeReference = typeReference as? TypeReference.ListReference
            ?: throw AssertionError("list 타입이어야 합니다. 실제 타입: $typeReference")
        assertMapOfPrimitiveValueType(listTypeReference.elementType, TypePrimitiveKind.STRING)
    }

    private fun assertMapOfStringListType(typeReference: TypeReference) {
        val mapTypeReference = typeReference as? TypeReference.MapReference
            ?: throw AssertionError("map 타입이어야 합니다. 실제 타입: $typeReference")
        assertPrimitiveType(mapTypeReference.keyType, TypePrimitiveKind.STRING)
        assertListOfPrimitiveType(mapTypeReference.valueType, TypePrimitiveKind.STRING)
    }

    private fun JsonToTypeConversionResult.requireDeclaration(declarationName: String): TypeDeclaration {
        return declarations.firstOrNull { it.name == declarationName }
            ?: throw AssertionError(
                "선언 `$declarationName`이 필요합니다. 실제 선언: ${declarations.map(TypeDeclaration::name)}",
            )
    }

    private fun TypeAnalysisResult.requireDeclaration(declarationName: String): TypeDeclaration {
        return declarations.firstOrNull { it.name == declarationName }
            ?: throw AssertionError(
                "선언 `$declarationName`이 필요합니다. 실제 선언: ${declarations.map(TypeDeclaration::name)}",
            )
    }

    private fun TypeDeclaration.requireFieldBySourceName(fieldSourceName: String): TypeField {
        return fields.firstOrNull { it.sourceName == fieldSourceName }
            ?: throw AssertionError(
                "`${name}` 선언에 source name `$fieldSourceName` 필드가 필요합니다. 실제 필드: ${fields.map(TypeField::sourceName)}",
            )
    }

    private fun TypeReference.InlineObject.requireFieldBySourceName(fieldSourceName: String): TypeField {
        return fields.firstOrNull { it.sourceName == fieldSourceName }
            ?: throw AssertionError(
                "inline object에 source name `$fieldSourceName` 필드가 필요합니다. 실제 필드: ${fields.map(TypeField::sourceName)}",
            )
    }

    private data class ComplexTypeToJsonCase(
        val language: SupportedLanguage,
        val sourceCode: String,
        val expectedRootDeclarationKind: TypeDeclarationKind,
        val fieldNames: ComplexFieldNames,
        val expectedMemberIdPrimitiveKind: TypePrimitiveKind,
        val expectedLimitMaxUsersPrimitiveKind: TypePrimitiveKind,
        val expectedLimitStorageGbPrimitiveKind: TypePrimitiveKind,
    )

    private data class ComplexFieldNames(
        val workspaceId: String,
        val state: String,
        val members: String,
        val limitsByRegion: String,
        val auditTrail: String,
        val extra: String,
        val inlineSettings: String,
        val memberId: String,
        val memberDisplayName: String,
        val memberRoles: String,
        val memberContact: String,
        val contactEmail: String,
        val contactPhones: String,
        val limitMaxUsers: String,
        val limitStorageGb: String,
        val limitFlags: String,
        val inlineCompact: String,
        val inlineLabels: String,
    )

    companion object {
        private val TYPE_TO_JSON_OPTIONS = TypeToJsonGenerationOptions(
            propertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL,
            includesNullableFieldWithNullValue = false,
            usesRealisticSampleData = false,
            outputCount = 1,
            formatState = JsonFormatState.PRETTIFY,
        )

        private val LOWER_CAMEL_FIELD_NAMES = ComplexFieldNames(
            workspaceId = "workspaceId",
            state = "state",
            members = "members",
            limitsByRegion = "limitsByRegion",
            auditTrail = "auditTrail",
            extra = "extra",
            inlineSettings = "inlineSettings",
            memberId = "id",
            memberDisplayName = "displayName",
            memberRoles = "roles",
            memberContact = "contact",
            contactEmail = "email",
            contactPhones = "phones",
            limitMaxUsers = "maxUsers",
            limitStorageGb = "storageGb",
            limitFlags = "flags",
            inlineCompact = "compact",
            inlineLabels = "labels",
        )

        private val GO_FIELD_NAMES = ComplexFieldNames(
            workspaceId = "WorkspaceID",
            state = "State",
            members = "Members",
            limitsByRegion = "LimitsByRegion",
            auditTrail = "AuditTrail",
            extra = "Extra",
            inlineSettings = "InlineSettings",
            memberId = "ID",
            memberDisplayName = "DisplayName",
            memberRoles = "Roles",
            memberContact = "Contact",
            contactEmail = "Email",
            contactPhones = "Phones",
            limitMaxUsers = "MaxUsers",
            limitStorageGb = "StorageGb",
            limitFlags = "Flags",
            inlineCompact = "Compact",
            inlineLabels = "Labels",
        )

        private val COMPLEX_TYPE_TO_JSON_CASES by lazy {
            listOf(
                ComplexTypeToJsonCase(
                    language = SupportedLanguage.KOTLIN,
                sourceCode = KOTLIN_COMPLEX_WORKSPACE_SOURCE,
                expectedRootDeclarationKind = TypeDeclarationKind.CLASS,
                fieldNames = LOWER_CAMEL_FIELD_NAMES,
                expectedMemberIdPrimitiveKind = TypePrimitiveKind.INTEGER,
                expectedLimitMaxUsersPrimitiveKind = TypePrimitiveKind.INTEGER,
                expectedLimitStorageGbPrimitiveKind = TypePrimitiveKind.DECIMAL,
            ),
            ComplexTypeToJsonCase(
                language = SupportedLanguage.JAVA,
                sourceCode = JAVA_COMPLEX_WORKSPACE_SOURCE,
                expectedRootDeclarationKind = TypeDeclarationKind.CLASS,
                fieldNames = LOWER_CAMEL_FIELD_NAMES,
                expectedMemberIdPrimitiveKind = TypePrimitiveKind.INTEGER,
                expectedLimitMaxUsersPrimitiveKind = TypePrimitiveKind.INTEGER,
                expectedLimitStorageGbPrimitiveKind = TypePrimitiveKind.DECIMAL,
            ),
            ComplexTypeToJsonCase(
                language = SupportedLanguage.GO,
                sourceCode = GO_COMPLEX_WORKSPACE_SOURCE,
                expectedRootDeclarationKind = TypeDeclarationKind.STRUCT,
                fieldNames = GO_FIELD_NAMES,
                expectedMemberIdPrimitiveKind = TypePrimitiveKind.INTEGER,
                expectedLimitMaxUsersPrimitiveKind = TypePrimitiveKind.INTEGER,
                expectedLimitStorageGbPrimitiveKind = TypePrimitiveKind.DECIMAL,
            ),
            ComplexTypeToJsonCase(
                language = SupportedLanguage.TYPESCRIPT,
                sourceCode = TYPESCRIPT_COMPLEX_WORKSPACE_SOURCE,
                expectedRootDeclarationKind = TypeDeclarationKind.INTERFACE,
                fieldNames = LOWER_CAMEL_FIELD_NAMES,
                expectedMemberIdPrimitiveKind = TypePrimitiveKind.NUMBER,
                expectedLimitMaxUsersPrimitiveKind = TypePrimitiveKind.NUMBER,
                expectedLimitStorageGbPrimitiveKind = TypePrimitiveKind.NUMBER,
            ),
            )
        }

        private val COMPLEX_WORKSPACE_REPORT_JSON_TEXT = """
            {
              "workspace": {
                "id": "ws_001",
                "enabled": true,
                "limits": {
                  "maxUsers": 250,
                  "storageGb": 512.5
                },
                "owners": [
                  {
                    "id": 1,
                    "name": "Ada",
                    "email": "ada@example.com",
                    "roles": ["OWNER", "ADMIN"],
                    "preferences": {
                      "theme": "dark",
                      "notifications": {
                        "email": true,
                        "sms": false
                      }
                    }
                  },
                  {
                    "id": 2,
                    "name": "Grace",
                    "roles": ["REVIEWER"],
                    "preferences": {
                      "theme": null,
                      "notifications": {
                        "email": false
                      }
                    }
                  }
                ],
                "featureFlags": {
                  "search": {
                    "enabled": true,
                    "rollout": 0.75
                  },
                  "billing": {
                    "enabled": false,
                    "rollout": 0
                  }
                }
              },
              "audit": {
                "events": [
                  {
                    "name": "created",
                    "timestamp": "2026-04-29T00:00:00Z",
                    "metadata": {
                      "source": "api",
                      "retry": false
                    }
                  },
                  {
                    "name": "updated",
                    "timestamp": null,
                    "metadata": {
                      "source": "ui",
                      "attempt": 2
                    }
                  }
                ],
                "labels": ["prod", "critical"]
              },
              "nullableNote": null,
              "thresholds": [1, 2.5, null],
              "matrix": [[1, 2], [3, 4]]
            }
        """.trimIndent()

        private val KOTLIN_COMPLEX_WORKSPACE_SOURCE = """
            enum class PlanState {
                DRAFT,
                ACTIVE,
                ARCHIVED
            }

            data class Contact(
                val email: String?,
                val phones: List<String>
            )

            data class LimitProfile(
                val maxUsers: Int,
                val storageGb: Double,
                val flags: Map<String, Boolean>
            )

            data class InlineSettings(
                val compact: Boolean,
                val labels: List<String>
            )

            data class WorkspaceMember(
                val id: Long,
                val displayName: String,
                val roles: List<String>,
                val contact: Contact?
            )

            data class WorkspaceEnvelope(
                val workspaceId: String,
                val state: PlanState,
                val members: List<WorkspaceMember>,
                val limitsByRegion: Map<String, LimitProfile>,
                val auditTrail: List<Map<String, String>>,
                val extra: Map<String, List<String>>,
                val inlineSettings: InlineSettings
            )
        """.trimIndent()

        private val JAVA_COMPLEX_WORKSPACE_SOURCE = """
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;

            enum PlanState {
                DRAFT,
                ACTIVE,
                ARCHIVED
            }

            class Contact {
                Optional<String> email;
                List<String> phones;
            }

            class LimitProfile {
                int maxUsers;
                double storageGb;
                Map<String, Boolean> flags;
            }

            class InlineSettings {
                boolean compact;
                List<String> labels;
            }

            class WorkspaceMember {
                long id;
                String displayName;
                List<String> roles;
                Optional<Contact> contact;
            }

            class WorkspaceEnvelope {
                String workspaceId;
                PlanState state;
                List<WorkspaceMember> members;
                Map<String, LimitProfile> limitsByRegion;
                List<Map<String, String>> auditTrail;
                Map<String, List<String>> extra;
                InlineSettings inlineSettings;
            }
        """.trimIndent()

        private val GO_COMPLEX_WORKSPACE_SOURCE = """
            type PlanState string

            type Contact struct {
                Email *string
                Phones []string
            }

            type LimitProfile struct {
                MaxUsers int
                StorageGb float64
                Flags map[string]bool
            }

            type WorkspaceMember struct {
                ID int64
                DisplayName string
                Roles []string
                Contact *Contact
            }

            type WorkspaceEnvelope struct {
                WorkspaceID string
                State PlanState
                Members []WorkspaceMember
                LimitsByRegion map[string]LimitProfile
                AuditTrail []map[string]string
                Extra map[string][]string
                InlineSettings struct {
                    Compact bool
                    Labels []string
                }
            }
        """.trimIndent()

        private val TYPESCRIPT_COMPLEX_WORKSPACE_SOURCE = """
            enum PlanState {
              DRAFT = "DRAFT",
              ACTIVE = "ACTIVE",
              ARCHIVED = "ARCHIVED",
            }

            type AuditTrail = Array<Record<string, string>>;

            interface Contact {
              email?: string | null;
              phones: string[];
            }

            interface LimitProfile {
              maxUsers: number;
              storageGb: number;
              flags: Record<string, boolean>;
            }

            interface WorkspaceMember {
              id: number;
              displayName: string;
              roles: string[];
              contact?: Contact | null;
            }

            interface WorkspaceEnvelope {
              workspaceId: string;
              state: PlanState;
              members: WorkspaceMember[];
              limitsByRegion: Record<string, LimitProfile>;
              auditTrail: AuditTrail;
              extra: Record<string, string[]>;
              inlineSettings: {
                compact: boolean;
                labels?: string[];
              };
            }
        """.trimIndent()
    }
}
