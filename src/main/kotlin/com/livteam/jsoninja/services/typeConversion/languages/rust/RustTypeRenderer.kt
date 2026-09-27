package com.livteam.jsoninja.services.typeConversion.languages.rust

import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionOptions
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class RustTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String) = "// Warning: ${message.replace('\n', ' ').replace('\r', ' ')}"

    override fun collectWarnings(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): List<TypeConversionWarning> {
        val warnings = linkedSetOf<String>()
        declarations.forEach { declaration -> declaration.fields.forEach { field ->
            if (field.name.removePrefix("r#") != field.sourceName) warnings += "Rust field '${field.name}' cannot retain JSON key '${field.sourceName}' without a serializer mapping."
            if (field.isOptional) warnings += "Rust Option storage does not distinguish an absent JSON member from null; JSONinja comments retain optionality."
        } }
        visitTypes(declarations) { if (needsValueHelper(it)) warnings += "Rust JsoninjaValue is an owned value model without an automatic serializer; unknown or heterogeneous values generate null samples." }
        return warnings.map(::TypeConversionWarning)
    }

    override fun render(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions, warnings: List<String>): String {
        val usedNames = declarations.map { it.name }.toSet()
        var helperName = "JsoninjaValue"
        var suffix = 2
        while (helperName in usedNames) helperName = "JsoninjaValue${suffix++}"
        var hasHelper = false
        var hasMap = false
        visitTypes(declarations) { type -> hasHelper = hasHelper || needsValueHelper(type); hasMap = hasMap || type is TypeReference.MapReference }
        val helper = if (hasHelper) """
            // JSONinja value v1
            #[derive(Debug, Clone)]
            pub enum $helperName {
                Null,
                Bool(bool),
                Integer(i64),
                Number(f64),
                String(String),
                Array(Vec<$helperName>),
                Object(HashMap<String, $helperName>),
            }
        """.trimIndent() else ""
        return listOf(warnings.joinToString("\n", transform = ::formatWarning), if (hasMap || hasHelper) "use std::collections::HashMap;" else "", helper,
            declarations.joinToString("\n\n") { renderDeclaration(it, helperName) }).filter(String::isNotBlank).joinToString("\n\n")
    }

    override fun renderDeclaration(declaration: TypeDeclaration, options: JsonToTypeConversionOptions) = renderDeclaration(declaration, "JsoninjaValue")

    private fun renderDeclaration(declaration: TypeDeclaration, helperName: String): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) return "pub type ${declaration.name} = ${renderType(declaration.aliasedTypeReference ?: TypeReference.AnyValue, helperName)};"
        val fields = declaration.fields.joinToString("\n") { field ->
            val type = renderType(field.typeReference, helperName).let { if (field.isOptional && field.typeReference !is TypeReference.Nullable) "Option<$it>" else it }
            val marker = if (field.isOptional) "    // JSONinja optional v1\n" else ""
            "${marker}    pub ${field.name}: $type,"
        }
        return "#[derive(Debug, Clone)]\npub struct ${declaration.name} {\n$fields\n}"
    }

    private fun renderType(type: TypeReference, helper: String): String = when (type) {
        TypeReference.AnyValue, is TypeReference.Union, is TypeReference.InlineObject -> helper
        is TypeReference.Named -> type.name
        is TypeReference.Nullable -> "Option<${renderType(type.wrappedType, helper)}>"
        is TypeReference.ListReference -> "Vec<${renderType(type.elementType, helper)}>"
        is TypeReference.MapReference -> "HashMap<${renderType(type.keyType, helper)}, ${renderType(type.valueType, helper)}>"
        is TypeReference.Primitive -> when (type.primitiveKind) {
            TypePrimitiveKind.STRING -> "String"
            TypePrimitiveKind.INTEGER -> "i64"
            TypePrimitiveKind.BOOLEAN -> "bool"
            else -> "f64"
        }
    }

    private fun needsValueHelper(type: TypeReference) = type == TypeReference.AnyValue || type is TypeReference.Union || type is TypeReference.InlineObject
    private fun visitTypes(declarations: List<TypeDeclaration>, visit: (TypeReference) -> Unit) {
        fun walk(type: TypeReference) {
            visit(type)
            when (type) {
                is TypeReference.Nullable -> walk(type.wrappedType)
                is TypeReference.ListReference -> walk(type.elementType)
                is TypeReference.MapReference -> { walk(type.keyType); walk(type.valueType) }
                is TypeReference.Union -> type.members.forEach(::walk)
                is TypeReference.InlineObject -> type.fields.forEach { walk(it.typeReference) }
                else -> Unit
            }
        }
        declarations.forEach { d -> d.aliasedTypeReference?.let(::walk); d.fields.forEach { walk(it.typeReference) } }
    }
}
