package com.livteam.jsoninja.services.typeConversion.languages.c

import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionOptions
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class CTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String): String =
        "/* Warning: ${message.replace("*/", "* /").replace('\n', ' ').replace('\r', ' ')} */"

    override fun collectWarnings(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): List<TypeConversionWarning> {
        val warnings = linkedSetOf<String>()
        fun inspect(type: TypeReference) {
            when (type) {
                TypeReference.AnyValue, is TypeReference.Union -> warnings += "C uses void pointers for unconstrained or union values; their JSON shape is not retained."
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
                if (field.sourceName != field.name) warnings += "C field '${field.name}' cannot retain JSON key '${field.sourceName}' without a serializer mapping."
                if (field.isOptional) warnings += "C does not encode member absence; JSONinja comments retain optionality only for reverse conversion."
                inspect(field.typeReference)
            }
        }
        return warnings.map(::TypeConversionWarning)
    }

    override fun render(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions, warnings: List<String>): String {
        val context = RenderContext(declarations)
        val blocks = declarations.map(context::renderDeclaration)
        return (warnings.map(::formatWarning) + listOf(
            "#include <stdbool.h>\n#include <stdint.h>\n#include <stddef.h>",
        ) + blocks).joinToString("\n\n").trim()
    }

    override fun renderDeclaration(declaration: TypeDeclaration, options: JsonToTypeConversionOptions): String =
        RenderContext(listOf(declaration)).renderDeclaration(declaration)

    private class RenderContext(declarations: List<TypeDeclaration>) {
        private val usedNames = declarations.mapTo(mutableSetOf()) { it.name }
        private val collectionNames = mutableMapOf<TypeReference, String>()
        private val pendingHelpers = mutableListOf<String>()

        fun renderDeclaration(declaration: TypeDeclaration): String {
            val body = if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) {
                "typedef ${renderType(declaration.aliasedTypeReference ?: TypeReference.AnyValue)} ${declaration.name};"
            } else {
                renderStruct(declaration.name, declaration.fields)
            }
            val result = (pendingHelpers + body).joinToString("\n\n")
            pendingHelpers.clear()
            return result
        }

        private fun renderStruct(name: String, fields: List<TypeField>): String {
            if (fields.isEmpty()) return "/* JSONinja empty object v1 */\ntypedef struct $name {\n    unsigned char jsoninja_empty;\n} $name;"
            val body = fields.joinToString("\n") { field ->
                val optional = if (field.isOptional) "    /* JSONinja optional v1 */\n" else ""
                "$optional    ${renderType(field.typeReference)} ${field.name};"
            }
            return "typedef struct $name {\n$body\n} $name;"
        }

        private fun renderType(type: TypeReference): String = when (type) {
            TypeReference.AnyValue, is TypeReference.Union -> "void *"
            is TypeReference.Primitive -> when (type.primitiveKind) {
                TypePrimitiveKind.BOOLEAN -> "bool"
                TypePrimitiveKind.INTEGER -> "int64_t"
                TypePrimitiveKind.DECIMAL, TypePrimitiveKind.NUMBER -> "double"
                TypePrimitiveKind.STRING -> "char *"
            }
            is TypeReference.Named -> type.name
            is TypeReference.Nullable -> "${renderType(type.wrappedType)} *"
            is TypeReference.ListReference -> collectionNames.getOrPut(type) {
                val element = renderType(type.elementType)
                allocateName("JsoninjaArray").also { name ->
                    pendingHelpers += "/* JSONinja collection: array v1 */\ntypedef struct $name {\n    size_t length;\n    $element *data;\n} $name;"
                }
            }
            is TypeReference.MapReference -> collectionNames.getOrPut(type) {
                val key = renderType(type.keyType)
                val value = renderType(type.valueType)
                val entry = allocateName("JsoninjaEntry")
                pendingHelpers += "/* JSONinja collection: entry v1 */\ntypedef struct $entry {\n    $key key;\n    $value value;\n} $entry;"
                allocateName("JsoninjaMap").also { name ->
                    pendingHelpers += "/* JSONinja collection: map v1 */\ntypedef struct $name {\n    size_t length;\n    $entry *data;\n} $name;"
                }
            }
            is TypeReference.InlineObject -> allocateName("JsoninjaObject").also { name ->
                val declaration = renderStruct(name, type.fields)
                pendingHelpers += declaration
            }
        }

        private fun allocateName(prefix: String): String {
            var index = 1
            while (!usedNames.add("$prefix$index")) index++
            return "$prefix$index"
        }
    }
}
