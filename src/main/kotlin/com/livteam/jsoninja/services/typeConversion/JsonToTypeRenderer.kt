package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRegistry

class JsonToTypeRenderer {
    fun render(
        declarations: List<TypeDeclaration>,
        language: SupportedLanguage,
        options: JsonToTypeConversionOptions,
        warningMessages: List<String> = emptyList(),
    ): String = TypeLanguageRegistry.forLanguage(language).renderer.render(declarations, options, warningMessages)
}
