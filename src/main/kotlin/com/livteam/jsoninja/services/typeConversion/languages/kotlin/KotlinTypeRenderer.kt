package com.livteam.jsoninja.services.typeConversion.languages.kotlin

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class KotlinTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String): String = "// Warning: $message"

    override fun collectImports(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): String {
        val imports = linkedSetOf<String>()
        declarations.forEach { declaration ->
            declaration.fields.forEach { field ->
                if (field.sourceName != field.name || options.annotationStyle != JsonToTypeAnnotationStyle.NONE) {
                    when (options.annotationStyle) {
                        JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> imports += "import com.fasterxml.jackson.annotation.JsonProperty"
                        JsonToTypeAnnotationStyle.KOTLIN_SERIAL_NAME -> imports += "import kotlinx.serialization.SerialName"
                        else -> Unit
                    }
                }
            }
        }
        return imports.joinToString("\n")
    }

    override fun renderDeclaration(
        declaration: TypeDeclaration,
        options: JsonToTypeConversionOptions,
    ): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) {
            return "typealias ${declaration.name} = ${renderKotlinType(declaration.aliasedTypeReference ?: TypeReference.AnyValue, options)}"
        }
        if (declaration.fields.isEmpty()) return "class ${declaration.name}"
        val constructorFields = declaration.fields.joinToString(",\n") { field ->
            val annotationText = renderKotlinFieldAnnotation(field, options)
            val typeText = renderKotlinType(field.typeReference, options)
            val prefix = annotationText?.let { "    $it\n    " } ?: "    "
            "$prefix${if (field.isOptional) "val" else "val"} ${field.name}: $typeText"
        }
        return "data class ${declaration.name}(\n$constructorFields\n)"
    }

    private fun renderKotlinType(
        typeReference: TypeReference,
        options: JsonToTypeConversionOptions,
    ): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "Any"
            is TypeReference.InlineObject -> "Map<String, Any>"
            is TypeReference.ListReference -> "List<${renderKotlinType(typeReference.elementType, options)}>"
            is TypeReference.MapReference -> {
                "Map<${renderKotlinType(typeReference.keyType, options)}, ${renderKotlinType(typeReference.valueType, options)}>"
            }
            is TypeReference.Named -> typeReference.name
            is TypeReference.Nullable -> "${renderKotlinType(typeReference.wrappedType, options)}?"
            is TypeReference.Primitive -> when (typeReference.primitiveKind) {
                TypePrimitiveKind.STRING -> "String"
                TypePrimitiveKind.INTEGER -> "Long"
                TypePrimitiveKind.DECIMAL, TypePrimitiveKind.NUMBER -> "Double"
                TypePrimitiveKind.BOOLEAN -> "Boolean"
            }
            is TypeReference.Union -> "Any"
        }
    }

    private fun renderKotlinFieldAnnotation(
        field: TypeField,
        options: JsonToTypeConversionOptions,
    ): String? {
        return when (options.annotationStyle) {
            JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> "@JsonProperty(${JsonToTypeLiteralSupport.quote(field.sourceName, SupportedLanguage.KOTLIN)})"
            JsonToTypeAnnotationStyle.KOTLIN_SERIAL_NAME -> "@SerialName(${JsonToTypeLiteralSupport.quote(field.sourceName, SupportedLanguage.KOTLIN)})"
            else -> null
        }
    }
}
