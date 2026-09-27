package com.livteam.jsoninja.services.typeConversion.languages.jsdoc

import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionOptions
import com.livteam.jsoninja.services.typeConversion.JsonToTypeLiteralSupport
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class JsDocTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String) = "// Warning: ${message.replace('\n', ' ').replace('\r', ' ').replace('\u2028', ' ').replace('\u2029', ' ')}"
    override fun collectWarnings(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): List<TypeConversionWarning> {
        val warnings = linkedSetOf<String>()
        fun visit(type: TypeReference) {
            when (type) {
                TypeReference.AnyValue -> warnings += "JSDoc wildcard does not retain a concrete JSON value type."
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
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) return "/**\n * @typedef {${renderType(declaration.aliasedTypeReference ?: TypeReference.AnyValue)}} ${declaration.name}\n */"
        val fields = declaration.fields.joinToString("\n") { field ->
            val name = propertyName(field.sourceName).let { if (field.isOptional) "[$it]" else it }
            " * @property {${renderType(field.typeReference)}} $name"
        }
        return listOf("/**", " * @typedef {Object} ${declaration.name}", fields, " */").filter(String::isNotBlank).joinToString("\n")
    }
    private fun renderType(type: TypeReference): String = when (type) {
        TypeReference.AnyValue -> "*"
        is TypeReference.Named -> type.name
        is TypeReference.ListReference -> "Array<${renderType(type.elementType)}>"
        is TypeReference.MapReference -> "Object<${renderType(type.keyType)}, ${renderType(type.valueType)}>"
        is TypeReference.Nullable -> "(${renderType(type.wrappedType)}|null)"
        is TypeReference.Union -> "(${type.members.joinToString("|", transform = ::renderType)})"
        is TypeReference.InlineObject -> "{${type.fields.joinToString(", ") { "${propertyName(it.sourceName)}: ${renderType(it.typeReference)}" }}}"
        is TypeReference.Primitive -> when (type.primitiveKind) {
            TypePrimitiveKind.STRING -> "string"
            TypePrimitiveKind.BOOLEAN -> "boolean"
            else -> "number"
        }
    }
    private fun propertyName(name: String): String = if (name.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) name else
        JsonToTypeLiteralSupport.quote(name, JsDocTypePolicy).replace("*/", "*\\u002f").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
}
