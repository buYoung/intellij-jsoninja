package com.livteam.jsoninja.services.typeConversion.languages.python

import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionOptions
import com.livteam.jsoninja.services.typeConversion.JsonToTypeLiteralSupport
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class PythonTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String): String = "# Warning: ${message.replace('\n', ' ').replace('\r', ' ')}"
    override fun collectImports(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions) = "from typing import Any, NotRequired, TypedDict"

    override fun collectWarnings(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): List<TypeConversionWarning> {
        val warnings = linkedSetOf<String>()
        fun visit(type: TypeReference) {
            when (type) {
                TypeReference.AnyValue -> warnings += "Python Any does not retain a concrete JSON value type."
                is TypeReference.Union -> { warnings += "JSON samples select the first supported union alternative."; type.members.forEach(::visit) }
                is TypeReference.Nullable -> visit(type.wrappedType)
                is TypeReference.ListReference -> visit(type.elementType)
                is TypeReference.MapReference -> { visit(type.keyType); visit(type.valueType) }
                is TypeReference.InlineObject -> type.fields.forEach { visit(it.typeReference) }
                else -> Unit
            }
        }
        declarations.forEach { d -> d.aliasedTypeReference?.let(::visit); d.fields.forEach { visit(it.typeReference) } }
        return warnings.map(::TypeConversionWarning)
    }

    override fun renderDeclaration(declaration: TypeDeclaration, options: JsonToTypeConversionOptions): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) return "${declaration.name} = ${renderType(declaration.aliasedTypeReference ?: TypeReference.AnyValue)}"
        val isClassForm = declaration.fields.all { it.sourceName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && it.sourceName !in PythonTypePolicy.reservedFieldNames && !it.sourceName.startsWith("__") }
        fun fieldType(field: TypeField): String = renderType(field.typeReference).let { if (field.isOptional) "NotRequired[$it]" else it }
        if (isClassForm) {
            val body = declaration.fields.joinToString("\n") { "    ${it.sourceName}: ${fieldType(it)}" }.ifEmpty { "    pass" }
            return "class ${declaration.name}(TypedDict):\n$body"
        }
        val fields = declaration.fields.joinToString(",\n") { "    ${quote(it.sourceName)}: ${fieldType(it)}" }
        return "${declaration.name} = TypedDict(${quote(declaration.name)}, {\n$fields\n})"
    }

    private fun renderType(type: TypeReference): String = when (type) {
        TypeReference.AnyValue -> "Any"
        is TypeReference.Named -> type.name
        is TypeReference.ListReference -> "list[${renderType(type.elementType)}]"
        is TypeReference.MapReference -> "dict[${renderType(type.keyType)}, ${renderType(type.valueType)}]"
        is TypeReference.InlineObject -> "dict[str, Any]"
        is TypeReference.Nullable -> "${renderType(type.wrappedType)} | None"
        is TypeReference.Union -> type.members.joinToString(" | ", transform = ::renderType)
        is TypeReference.Primitive -> when (type.primitiveKind) {
            TypePrimitiveKind.STRING -> "str"
            TypePrimitiveKind.INTEGER -> "int"
            TypePrimitiveKind.BOOLEAN -> "bool"
            else -> "float"
        }
    }
    private fun quote(value: String) = JsonToTypeLiteralSupport.quote(value, PythonTypePolicy)
}
