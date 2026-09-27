package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.ObjectMapper
import com.livteam.jsoninja.model.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRegistry

class TypeLanguageRendererGoldenTest {
    @Test
    fun originalLanguageSourcesMatchReviewedGoldens() {
        SupportedLanguage.entries
            .forEach { language ->
                val options = JsonToTypeConversionOptions(
                    namingConvention = language.defaultNamingConvention,
                    annotationStyle = language.defaultAnnotationStyle,
                )
                val inferred = JsonToTypeInferenceContext(language, options).infer(
                    ObjectMapper().readTree(fixture("object.json")),
                )
                assertEquals(emptyList<Any>(), inferred.warnings)
                assertEquals(
                    language.name,
                    fixture("object.${language.fileExtension}").trimEnd(),
                    JsonToTypeRenderer().render(inferred.declarations, language, options,
                        TypeLanguageRegistry.forLanguage(language).renderer.collectWarnings(inferred.declarations, options).map { it.message }),
                )
            }
    }

    private fun fixture(name: String): String = checkNotNull(
        javaClass.getResource("/typeConversion/golden/$name"),
    ) { "Missing golden fixture: $name" }.readText()
}
