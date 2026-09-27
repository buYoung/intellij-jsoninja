package com.livteam.jsoninja.services.typeConversion.languages.csharp

import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.JsonToTypeAnnotationStyle
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionOptions
import com.livteam.jsoninja.services.typeConversion.JsonToTypeLiteralSupport
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class CSharpTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String): String = "/* Warning: ${message.replace("*/", "* /").replace('\n', ' ').replace('\r', ' ')} */"

    override fun collectWarnings(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): List<TypeConversionWarning> {
        val warnings = linkedSetOf<String>()
        fun inspect(type: TypeReference) {
            when (type) {
                TypeReference.AnyValue, is TypeReference.Union -> warnings += "C# object does not retain an unconstrained or union JSON value type."
                is TypeReference.Nullable -> inspect(type.wrappedType)
                is TypeReference.ListReference -> inspect(type.elementType)
                is TypeReference.MapReference -> { inspect(type.keyType); inspect(type.valueType) }
                is TypeReference.InlineObject -> type.fields.forEach { inspect(it.typeReference) }
                else -> Unit
            }
        }
        declarations.forEach { declaration ->
            declaration.aliasedTypeReference?.let(::inspect)
            declaration.fields.forEach { field ->
                if (field.name != field.sourceName && options.annotationStyle != JsonToTypeAnnotationStyle.CSHARP_JSON_PROPERTY_NAME)
                    warnings += "C# property '${field.name}' cannot retain JSON key '${field.sourceName}' without JsonPropertyName."
                if (field.isOptional) warnings += "Nullable C# properties do not encode member absence; JSONinja comments retain optionality."
                inspect(field.typeReference)
            }
        }
        return warnings.map(::TypeConversionWarning)
    }

    override fun render(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions, warnings: List<String>): String {
        val imports = mutableListOf("#nullable enable", "using System.Collections.Generic;")
        if (options.annotationStyle == JsonToTypeAnnotationStyle.CSHARP_JSON_PROPERTY_NAME) imports += "using System.Text.Json.Serialization;"
        val ordered = declarations.filter { it.declarationKind == TypeDeclarationKind.TYPE_ALIAS } + declarations.filter { it.declarationKind != TypeDeclarationKind.TYPE_ALIAS }
        return (warnings.map(::formatWarning) + imports.joinToString("\n") + ordered.map { renderDeclaration(it, options) }).joinToString("\n\n").trim()
    }

    override fun renderDeclaration(declaration: TypeDeclaration, options: JsonToTypeConversionOptions): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) return "using ${declaration.name} = ${renderType(declaration.aliasedTypeReference ?: TypeReference.AnyValue, true)};"
        val fields = declaration.fields.joinToString("\n") { field ->
            val annotation = if (options.annotationStyle == JsonToTypeAnnotationStyle.CSHARP_JSON_PROPERTY_NAME) "    [JsonPropertyName(${quote(field.sourceName)})]\n" else ""
            val optional = if (field.isOptional) "    // JSONinja optional v1\n" else ""
            "$optional$annotation    public ${renderType(field.typeReference)} ${field.name} { get; set; } = default!;"
        }
        return "public class ${declaration.name}\n{\n$fields\n}"
    }

    private fun renderType(type: TypeReference, isAlias: Boolean = false, isAliasRoot: Boolean = isAlias): String = when (type) {
        TypeReference.AnyValue, is TypeReference.Union -> if (isAlias) "System.Object" else "object"
        is TypeReference.Named -> type.name
        is TypeReference.InlineObject -> if (isAlias) "System.Collections.Generic.Dictionary<System.String, System.Object>" else "Dictionary<string, object>"
        is TypeReference.ListReference -> "${if (isAlias) "System.Collections.Generic." else ""}List<${renderType(type.elementType, isAlias, false)}>"
        is TypeReference.MapReference -> "${if (isAlias) "System.Collections.Generic." else ""}Dictionary<${renderType(type.keyType, isAlias, false)}, ${renderType(type.valueType, isAlias, false)}>"
        is TypeReference.Nullable -> if (!isAliasRoot) "${renderType(type.wrappedType, isAlias, false)}?" else {
            if (type.wrappedType is TypeReference.Primitive && type.wrappedType.primitiveKind != TypePrimitiveKind.STRING)
                "System.Nullable<${renderType(type.wrappedType, true, false)}>" else renderType(type.wrappedType, true)
        }
        is TypeReference.Primitive -> when (type.primitiveKind) {
            TypePrimitiveKind.STRING -> if (isAlias) "System.String" else "string"
            TypePrimitiveKind.INTEGER -> if (isAlias) "System.Int64" else "long"
            TypePrimitiveKind.BOOLEAN -> if (isAlias) "System.Boolean" else "bool"
            else -> if (isAlias) "System.Double" else "double"
        }
    }

    private fun quote(value: String): String = JsonToTypeLiteralSupport.quote(value, CSharpTypePolicy)
}
