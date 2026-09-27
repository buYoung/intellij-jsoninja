package com.livteam.jsoninja.services.typeConversion.languages.scala

import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.JsonToTypeConversionOptions
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class ScalaTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String) = "// Warning: ${message.replace('\n', ' ').replace('\r', ' ')}"
    override fun collectWarnings(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): List<TypeConversionWarning> {
        val warnings = linkedSetOf<String>()
        fun visit(type: TypeReference) {
            when (type) {
                TypeReference.AnyValue, is TypeReference.Union, is TypeReference.InlineObject -> warnings += "Scala Any does not retain a concrete JSON value type; a null sample is generated."
                is TypeReference.Nullable -> visit(type.wrappedType)
                is TypeReference.ListReference -> visit(type.elementType)
                is TypeReference.MapReference -> { visit(type.keyType); visit(type.valueType) }
                else -> Unit
            }
        }
        declarations.forEach { declaration ->
            declaration.aliasedTypeReference?.let(::visit)
            declaration.fields.forEach { field ->
                if (field.name != field.sourceName) warnings += "Scala field '${field.name}' cannot retain JSON key '${field.sourceName}' without a serializer mapping."
                if (field.isOptional) warnings += "Scala Option storage does not distinguish an absent JSON member from null; JSONinja comments retain optionality."
                visit(field.typeReference)
            }
        }
        return warnings.map(::TypeConversionWarning)
    }
    override fun render(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions, warnings: List<String>): String {
        var body = declarations.joinToString("\n\n") { renderDeclaration(it, options) }
        if (declarations.any { it.declarationKind == TypeDeclarationKind.TYPE_ALIAS }) {
            val used = declarations.map { it.name }.toSet()
            var scope = "JsoninjaTypes"
            var suffix = 2
            while (scope in used) scope = "JsoninjaTypes${suffix++}"
            body = "// JSONinja alias scope v1\nobject $scope {\n${body.prependIndent("    ")}\n}"
        }
        return listOf(warnings.joinToString("\n", transform = ::formatWarning), body).filter(String::isNotBlank).joinToString("\n\n")
    }
    override fun renderDeclaration(declaration: TypeDeclaration, options: JsonToTypeConversionOptions): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) return "type ${declaration.name} = ${renderType(declaration.aliasedTypeReference ?: TypeReference.AnyValue)}"
        if (declaration.fields.isEmpty()) return "case class ${declaration.name}()"
        val fields = declaration.fields.joinToString(",\n") { field ->
            val marker = if (field.isOptional) "/* JSONinja optional v1 */ " else ""
            val type = renderType(field.typeReference).let { if (field.isOptional && field.typeReference !is TypeReference.Nullable) "Option[$it]" else it }
            "    $marker${field.name}: $type"
        }
        return "case class ${declaration.name}(\n$fields\n)"
    }
    private fun renderType(type: TypeReference): String = when (type) {
        TypeReference.AnyValue, is TypeReference.Union, is TypeReference.InlineObject -> "Any"
        is TypeReference.Named -> type.name
        is TypeReference.ListReference -> "List[${renderType(type.elementType)}]"
        is TypeReference.MapReference -> "Map[${renderType(type.keyType)}, ${renderType(type.valueType)}]"
        is TypeReference.Nullable -> "Option[${renderType(type.wrappedType)}]"
        is TypeReference.Primitive -> when (type.primitiveKind) {
            TypePrimitiveKind.STRING -> "String"
            TypePrimitiveKind.INTEGER -> "Long"
            TypePrimitiveKind.BOOLEAN -> "Boolean"
            else -> "Double"
        }
    }
}
