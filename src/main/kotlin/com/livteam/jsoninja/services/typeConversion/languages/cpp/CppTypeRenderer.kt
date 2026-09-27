package com.livteam.jsoninja.services.typeConversion.languages.cpp

import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionOptions
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class CppTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String): String = "/* Warning: ${message.replace("*/", "* /").replace('\n', ' ').replace('\r', ' ')} */"

    override fun collectWarnings(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): List<TypeConversionWarning> {
        val warnings = linkedSetOf<String>()
        fun inspect(type: TypeReference) {
            when (type) {
                TypeReference.AnyValue, is TypeReference.InlineObject -> warnings += "C++ std::any does not retain a concrete JSON value type."
                is TypeReference.Union -> { warnings += "C++ variant JSON samples select the first supported alternative."; type.members.forEach(::inspect) }
                is TypeReference.Nullable -> inspect(type.wrappedType)
                is TypeReference.ListReference -> inspect(type.elementType)
                is TypeReference.MapReference -> { inspect(type.keyType); inspect(type.valueType) }
                else -> Unit
            }
        }
        declarations.forEach { declaration ->
            declaration.aliasedTypeReference?.let(::inspect)
            declaration.fields.forEach { field ->
                if (field.name != field.sourceName) warnings += "C++ field '${field.name}' cannot retain JSON key '${field.sourceName}' without a serializer mapping."
                if (field.isOptional) warnings += "C++ optional storage does not distinguish an absent JSON member from null; JSONinja comments retain optionality."
                inspect(field.typeReference)
            }
        }
        return warnings.map(::TypeConversionWarning)
    }

    override fun collectImports(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): String {
        val headers = sortedSetOf<String>()
        fun visit(type: TypeReference) {
            when (type) {
                TypeReference.AnyValue -> headers += "any"
                is TypeReference.InlineObject -> headers.addAll(listOf("map", "string", "any"))
                is TypeReference.ListReference -> { headers += "vector"; visit(type.elementType) }
                is TypeReference.MapReference -> { headers += "map"; visit(type.keyType); visit(type.valueType) }
                is TypeReference.Nullable -> { headers += "optional"; visit(type.wrappedType) }
                is TypeReference.Union -> { headers += "variant"; type.members.forEach(::visit) }
                is TypeReference.Primitive -> when (type.primitiveKind) {
                    TypePrimitiveKind.STRING -> headers += "string"
                    TypePrimitiveKind.INTEGER -> headers += "cstdint"
                    else -> Unit
                }
                else -> Unit
            }
        }
        declarations.forEach { declaration ->
            declaration.aliasedTypeReference?.let(::visit)
            declaration.fields.forEach { visit(it.typeReference); if (it.isOptional) headers += "optional" }
        }
        return headers.joinToString("\n") { "#include <$it>" }
    }

    override fun renderDeclaration(declaration: TypeDeclaration, options: JsonToTypeConversionOptions): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) return "using ${declaration.name} = ${renderType(declaration.aliasedTypeReference ?: TypeReference.AnyValue)};"
        val fields = declaration.fields.joinToString("\n") { field ->
            val type = renderType(field.typeReference).let { if (field.isOptional && field.typeReference !is TypeReference.Nullable) "std::optional<$it>" else it }
            val marker = if (field.isOptional) "    // JSONinja optional v1\n" else ""
            "$marker    $type ${field.name};"
        }
        return "struct ${declaration.name} {\n$fields\n};"
    }

    private fun renderType(type: TypeReference): String = when (type) {
        TypeReference.AnyValue -> "std::any"
        is TypeReference.InlineObject -> "std::map<std::string, std::any>"
        is TypeReference.Named -> type.name
        is TypeReference.ListReference -> "std::vector<${renderType(type.elementType)}>"
        is TypeReference.MapReference -> "std::map<${renderType(type.keyType)}, ${renderType(type.valueType)}>"
        is TypeReference.Nullable -> "std::optional<${renderType(type.wrappedType)}>"
        is TypeReference.Union -> "std::variant<${type.members.joinToString(", ", transform = ::renderType)}>"
        is TypeReference.Primitive -> when (type.primitiveKind) {
            TypePrimitiveKind.STRING -> "std::string"
            TypePrimitiveKind.INTEGER -> "std::int64_t"
            TypePrimitiveKind.BOOLEAN -> "bool"
            else -> "double"
        }
    }
}
