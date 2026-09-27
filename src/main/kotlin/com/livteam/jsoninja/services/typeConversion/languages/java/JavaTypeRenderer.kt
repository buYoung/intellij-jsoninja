package com.livteam.jsoninja.services.typeConversion.languages.java

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.*
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRenderer

internal class JavaTypeRenderer : TypeLanguageRenderer {
    override fun formatWarning(message: String): String = "// Warning: $message"

    override fun collectImports(declarations: List<TypeDeclaration>, options: JsonToTypeConversionOptions): String {
        val imports = linkedSetOf<String>()
        declarations.forEach { declaration ->
            declaration.aliasedTypeReference?.let { collectTypeImports(it, imports) }
            declaration.fields.forEach { field ->
                collectTypeImports(field.typeReference, imports)
                if (field.sourceName != field.name || options.annotationStyle != JsonToTypeAnnotationStyle.NONE) {
                    when (options.annotationStyle) {
                        JsonToTypeAnnotationStyle.GSON_SERIALIZED_NAME -> imports += "import com.google.gson.annotations.SerializedName;"
                        JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> imports += "import com.fasterxml.jackson.annotation.JsonProperty;"
                        else -> Unit
                    }
                }
            }
            if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) {
                imports += "import java.util.ArrayList;"
                imports += "import java.util.LinkedHashMap;"
            }
        }
        return imports.joinToString("\n")
    }

    private fun collectTypeImports(type: TypeReference, imports: MutableSet<String>) {
        when (type) {
            TypeReference.AnyValue -> imports += "import java.util.Map;"
            is TypeReference.InlineObject -> type.fields.forEach { collectTypeImports(it.typeReference, imports) }
            is TypeReference.ListReference -> {
                imports += "import java.util.List;"
                collectTypeImports(type.elementType, imports)
            }
            is TypeReference.MapReference -> {
                imports += "import java.util.Map;"
                collectTypeImports(type.keyType, imports)
                collectTypeImports(type.valueType, imports)
            }
            is TypeReference.Nullable -> collectTypeImports(type.wrappedType, imports)
            is TypeReference.Union -> type.members.forEach { collectTypeImports(it, imports) }
            is TypeReference.Primitive, is TypeReference.Named -> Unit
        }
    }

    override fun renderDeclaration(
        declaration: TypeDeclaration,
        options: JsonToTypeConversionOptions,
    ): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) {
            val aliasedType = declaration.aliasedTypeReference ?: TypeReference.AnyValue
            return when (aliasedType) {
                is TypeReference.ListReference ->
                    "public class ${declaration.name} extends ArrayList<${renderJavaType(aliasedType.elementType, options)}> {}"
                is TypeReference.MapReference ->
                    "public class ${declaration.name} extends LinkedHashMap<${renderJavaType(aliasedType.keyType, options)}, ${renderJavaType(aliasedType.valueType, options)}> {}"
                else ->
                    "public class ${declaration.name} {\n    private ${renderJavaType(aliasedType, options)} value;\n}"
            }
        }

        val privateFieldBlocks = declaration.fields.joinToString("\n\n") { field ->
            val annotationText = renderJavaFieldAnnotation(field, options)
            val fieldType = renderJavaType(field.typeReference, options)
            listOfNotNull(
                annotationText,
                "    private $fieldType ${field.name};",
            ).joinToString("\n")
        }

        val publicMethodBlocks = declaration.fields.joinToString("\n\n") { field ->
            val fieldType = renderJavaType(field.typeReference, options)
            val capitalizedFieldName = field.name.replaceFirstChar(Char::titlecase)
            listOf(
                "    public $fieldType get$capitalizedFieldName() {\n        return ${field.name};\n    }",
                "    public void set$capitalizedFieldName($fieldType ${field.name}) {\n        this.${field.name} = ${field.name};\n    }",
            ).joinToString("\n")
        }

        val memberBlocks = listOfNotNull(
            privateFieldBlocks.takeIf(String::isNotBlank),
            publicMethodBlocks.takeIf(String::isNotBlank),
        )

        val bodyText = memberBlocks.joinToString("\n\n")
        return if (bodyText.isBlank()) {
            "public class ${declaration.name} {\n}"
        } else {
            "public class ${declaration.name} {\n$bodyText\n}"
        }
    }

    private fun renderJavaType(
        typeReference: TypeReference,
        options: JsonToTypeConversionOptions,
    ): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "Object"
            is TypeReference.InlineObject -> "Map<String, Object>"
            is TypeReference.ListReference -> "List<${renderJavaType(typeReference.elementType, options)}>"
            is TypeReference.MapReference -> {
                "Map<${renderJavaType(typeReference.keyType, options)}, ${renderJavaType(typeReference.valueType, options)}>"
            }
            is TypeReference.Named -> typeReference.name
            is TypeReference.Nullable -> renderJavaType(typeReference.wrappedType, options)
            is TypeReference.Primitive -> when (typeReference.primitiveKind) {
                TypePrimitiveKind.STRING -> "String"
                TypePrimitiveKind.INTEGER -> "Integer"
                TypePrimitiveKind.DECIMAL -> "Double"
                TypePrimitiveKind.NUMBER -> "Number"
                TypePrimitiveKind.BOOLEAN -> "Boolean"
            }
            is TypeReference.Union -> "Object"
        }
    }

    private fun renderJavaFieldAnnotation(
        field: TypeField,
        options: JsonToTypeConversionOptions,
    ): String? {
        return when (options.annotationStyle) {
            JsonToTypeAnnotationStyle.GSON_SERIALIZED_NAME -> "    @SerializedName(${JsonToTypeLiteralSupport.quote(field.sourceName, SupportedLanguage.JAVA)})"
            JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> "    @JsonProperty(${JsonToTypeLiteralSupport.quote(field.sourceName, SupportedLanguage.JAVA)})"
            else -> null
        }
    }
}
