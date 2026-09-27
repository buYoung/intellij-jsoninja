package com.livteam.jsoninja.services.typeConversion.languages.typescript

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class TypescriptTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String): String = "// Warning: $message"


    override fun renderDeclaration(
        declaration: TypeDeclaration,
        options: JsonToTypeConversionOptions,
    ): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) {
            return "export type ${declaration.name} = ${renderTypescriptType(declaration.aliasedTypeReference ?: TypeReference.AnyValue, options)}"
        }
        if (declaration.declarationKind == TypeDeclarationKind.ENUM) {
            val enumBody = declaration.enumValues.joinToString(",\n") { value ->
                "  $value = \"$value\""
            }
            return "export enum ${declaration.name} {\n$enumBody\n}"
        }
        val fieldsText = declaration.fields.joinToString("\n") { field ->
            "  ${renderTypescriptFieldName(field.sourceName)}${if (field.isOptional) "?" else ""}: ${renderTypescriptType(field.typeReference, options)};"
        }
        return "export interface ${declaration.name} {\n$fieldsText\n}"
    }

    private fun renderTypescriptType(
        typeReference: TypeReference,
        options: JsonToTypeConversionOptions,
    ): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "any"
            is TypeReference.InlineObject -> "{ " + typeReference.fields.joinToString("; ") {
                "${renderTypescriptFieldName(it.sourceName)}${if (it.isOptional) "?" else ""}: ${renderTypescriptType(it.typeReference, options)}"
            } + " }"
            is TypeReference.ListReference -> {
                val elementType = renderTypescriptType(typeReference.elementType, options)
                if (typeReference.elementType is TypeReference.Nullable || typeReference.elementType is TypeReference.Union) {
                    "($elementType)[]"
                } else {
                    "$elementType[]"
                }
            }
            is TypeReference.MapReference -> "{ [key: ${renderTypescriptType(typeReference.keyType, options)}]: ${renderTypescriptType(typeReference.valueType, options)} }"
            is TypeReference.Named -> typeReference.name
            is TypeReference.Nullable -> "${renderTypescriptType(typeReference.wrappedType, options)} | null"
            is TypeReference.Primitive -> when (typeReference.primitiveKind) {
                TypePrimitiveKind.STRING -> "string"
                TypePrimitiveKind.INTEGER, TypePrimitiveKind.DECIMAL, TypePrimitiveKind.NUMBER -> "number"
                TypePrimitiveKind.BOOLEAN -> "boolean"
            }
            is TypeReference.Union -> typeReference.members.joinToString(" | ") { renderTypescriptType(it, options) }
        }
    }

    private fun renderTypescriptFieldName(sourceName: String): String =
        if (sourceName.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) sourceName
        else JsonToTypeLiteralSupport.quote(sourceName, SupportedLanguage.TYPESCRIPT)
}
