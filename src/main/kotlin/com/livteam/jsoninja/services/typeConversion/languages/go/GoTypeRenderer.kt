package com.livteam.jsoninja.services.typeConversion.languages.go

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class GoTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String): String = "// Warning: $message"

    override fun collectImports(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): String =
        if (declarations.any { GoJsonSupport.requiresCodec(it, options) }) "import \"encoding/json\"" else ""

    override fun renderDeclaration(
        declaration: TypeDeclaration,
        options: JsonToTypeConversionOptions,
    ): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) {
            return "type ${declaration.name} = ${renderGoType(declaration.aliasedTypeReference ?: TypeReference.AnyValue, options)}"
        }
        val fieldsText = declaration.fields.joinToString("\n") { field ->
            val exportedName = GoJsonSupport.exportedName(field)
            val tag = if (options.annotationStyle == JsonToTypeAnnotationStyle.GO_JSON_TAG) {
                " " + GoJsonSupport.jsonTag(field.sourceName, field.isOptional)
            } else {
                ""
            }
            "    $exportedName ${renderGoType(field.typeReference, options)}$tag"
        }
        val declarationText = "type ${declaration.name} struct {\n$fieldsText\n}"
        return if (GoJsonSupport.requiresCodec(declaration, options)) {
            declarationText + "\n\n" + GoJsonSupport.renderCodec(declaration, options)
        } else declarationText
    }

    private fun renderGoType(
        typeReference: TypeReference,
        options: JsonToTypeConversionOptions,
    ): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "any"
            is TypeReference.InlineObject -> "map[string]any"
            is TypeReference.ListReference -> "[]${renderGoType(typeReference.elementType, options)}"
            is TypeReference.MapReference -> "map[${renderGoType(typeReference.keyType, options)}]${renderGoType(typeReference.valueType, options)}"
            is TypeReference.Named -> typeReference.name
            is TypeReference.Nullable -> "*${renderGoType(typeReference.wrappedType, options)}"
            is TypeReference.Primitive -> when (typeReference.primitiveKind) {
                TypePrimitiveKind.STRING -> "string"
                TypePrimitiveKind.INTEGER -> "int"
                TypePrimitiveKind.DECIMAL, TypePrimitiveKind.NUMBER -> "float64"
                TypePrimitiveKind.BOOLEAN -> "bool"
            }
            is TypeReference.Union -> if (options.usesExperimentalGoUnionTypes) {
                typeReference.members.joinToString(" | ") { renderGoType(it, options) }
            } else {
                "any"
            }
        }
    }
}
