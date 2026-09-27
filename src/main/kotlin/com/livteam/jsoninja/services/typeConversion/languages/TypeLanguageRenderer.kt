package com.livteam.jsoninja.services.typeConversion.languages

import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeConversionWarning
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionOptions

internal interface TypeLanguageRenderer {
    fun collectWarnings(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): List<TypeConversionWarning> = emptyList()
    fun collectImports(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): String = ""
    fun renderDeclaration(declaration: TypeDeclaration, options: JsonToTypeConversionOptions): String
    fun formatWarning(message: String): String

    fun render(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions, warnings: List<String>): String =
        listOfNotNull(
            warnings.joinToString("\n", transform = ::formatWarning).takeIf(String::isNotBlank),
            collectImports(declarations, options).takeIf(String::isNotBlank),
            declarations.joinToString("\n\n") { renderDeclaration(it, options) },
        ).joinToString("\n\n").trim()
}
