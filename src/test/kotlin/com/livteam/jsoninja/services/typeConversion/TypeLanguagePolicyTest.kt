package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.ObjectMapper
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRegistry
import com.livteam.jsoninja.services.typeConversion.languages.c.CEnumValues
import com.livteam.jsoninja.services.typeConversion.languages.cpp.CppEnumValues
import com.livteam.jsoninja.services.typeConversion.languages.csharp.CSharpEnumValues
import org.junit.Assert.*
import org.junit.Test

class TypeLanguagePolicyTest {
    @Test fun integerEnumLiteralsRejectInvalidSuffixesSeparatorsExpressionsAndPrecisionLoss() {
        for (resolve in listOf(CEnumValues::resolve, CppEnumValues::resolve, CSharpEnumValues::resolve)) {
            assertEquals(listOf(TypeEnumValue.NumberValue(16.0), TypeEnumValue.NumberValue(17.0)), resolve(listOf("A" to "0x10UL", "B" to null)))
            for (literal in listOf("1UU", "1ULLL", "1 << 3", "9007199254740992")) {
                assertTrue(literal, resolve(listOf("A" to literal)).single() is TypeEnumValue.Unresolved)
            }
            assertTrue(resolve(listOf("A" to "Unknown", "B" to null)).all { it is TypeEnumValue.Unresolved })
        }
        assertEquals(listOf(TypeEnumValue.NumberValue(1000.0)), CppEnumValues.resolve(listOf("A" to "1'000")))
        assertTrue(CppEnumValues.resolve(listOf("A" to "1''000")).single() is TypeEnumValue.Unresolved)
        assertTrue(CSharpEnumValues.resolve(listOf("A" to "1_U")).single() is TypeEnumValue.Unresolved)
        assertEquals(listOf(TypeEnumValue.NumberValue(255.0)), CSharpEnumValues.resolve(listOf("A" to "0x_FF")))
    }

    @Test fun languageSpecificDeclarationNamesDoNotShadowTypesUsedByTheGeneratedCode() {
        val cases = listOf(
            Triple(SupportedLanguage.RUST, "String", "StringModel"),
            Triple(SupportedLanguage.RUST, "Self", "SelfModel"),
            Triple(SupportedLanguage.PYTHON, "TypedDict", "TypedDictModel"),
            Triple(SupportedLanguage.SCALA, "Option", "OptionModel"),
            Triple(SupportedLanguage.CSHARP, "List", "ListModel"),
            Triple(SupportedLanguage.CSHARP, "System", "SystemModel"),
            Triple(SupportedLanguage.JSDOC, "Object", "ObjectModel"),
        )
        for ((language, root, expected) in cases) {
            val inferred = JsonToTypeInferenceContext(language, JsonToTypeConversionOptions(rootTypeName = root, namingConvention = language.defaultNamingConvention, annotationStyle = language.defaultAnnotationStyle)).infer(ObjectMapper().readTree("{\"name\":\"x\",\"items\":[1]}"))
            assertEquals(language.name, expected, inferred.declarations.last().name)
        }
        val names = mutableSetOf<String>()
        for (key in listOf("type", "type", "self")) names += JsonToTypeNamingSupport.toFieldName(key, NamingConvention.SNAKE_CASE, SupportedLanguage.RUST, names)
        assertEquals(setOf("r#type", "r#type2", "selfValue"), names)
    }

    @Test fun csharpAliasQualifiesNestedImportsAndRetainsNullableElements() {
        val reference = TypeReference.ListReference(TypeReference.ListReference(TypeReference.Nullable(TypeReference.Primitive(TypePrimitiveKind.STRING))))
        val declaration = TypeDeclaration("Root", TypeDeclarationKind.TYPE_ALIAS, aliasedTypeReference = reference)
        val source = TypeLanguageRegistry.forLanguage(SupportedLanguage.CSHARP).renderer.renderDeclaration(declaration, JsonToTypeConversionOptions(namingConvention = NamingConvention.PASCAL_CASE, annotationStyle = JsonToTypeAnnotationStyle.NONE))
        assertEquals("using Root = System.Collections.Generic.List<System.Collections.Generic.List<System.String?>>;", source)
    }

    @Test fun rustUnknownHelperAvoidsCollisionWithTheRequestedRootName() {
        val declarations = listOf(TypeDeclaration("JsoninjaValue", TypeDeclarationKind.STRUCT, fields = listOf(TypeField("value", TypeReference.AnyValue))))
        val source = TypeLanguageRegistry.forLanguage(SupportedLanguage.RUST).renderer.render(declarations, JsonToTypeConversionOptions(namingConvention = NamingConvention.SNAKE_CASE, annotationStyle = JsonToTypeAnnotationStyle.NONE), emptyList())
        assertTrue(source, source.contains("pub enum JsoninjaValue2"))
        assertTrue(source, source.contains("pub value: JsoninjaValue2"))
        assertTrue(source, source.contains("Array(Vec<JsoninjaValue2>)"))
    }

    @Test fun csharpPropertyNamesEscapeUnicodeLineTerminators() {
        val declaration = TypeDeclaration("Root", TypeDeclarationKind.CLASS, fields = listOf(TypeField("Name", TypeReference.Primitive(TypePrimitiveKind.STRING), sourceName = "a\u0085b\u2028c")))
        val source = TypeLanguageRegistry.forLanguage(SupportedLanguage.CSHARP).renderer.renderDeclaration(declaration,
            JsonToTypeConversionOptions(namingConvention = NamingConvention.PASCAL_CASE, annotationStyle = JsonToTypeAnnotationStyle.CSHARP_JSON_PROPERTY_NAME))
        assertTrue(source, source.contains("[JsonPropertyName(\"a\\u0085b\\u2028c\")]"))
    }
}
