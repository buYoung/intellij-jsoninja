package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeField
import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import com.livteam.jsoninja.model.typeConversion.TypeReference
import java.util.Locale

class JsonToTypeRenderer {
    fun render(
        declarations: List<TypeDeclaration>,
        language: SupportedLanguage,
        options: JsonToTypeConversionOptions,
        warningMessages: List<String> = emptyList(),
    ): String {
        val importLines = collectImports(declarations, language, options)
        val declarationBlocks = declarations.joinToString("\n\n") { declaration ->
            renderDeclaration(declaration, language, options)
        }
        val header = warningMessages.joinToString("\n") { "// Warning: $it" }.takeIf(String::isNotBlank)

        return listOfNotNull(
            header,
            importLines.takeIf(String::isNotBlank),
            declarationBlocks,
        ).joinToString("\n\n").trim()
    }

    private fun collectImports(
        declarations: List<TypeDeclaration>,
        language: SupportedLanguage,
        options: JsonToTypeConversionOptions,
    ): String {
        val importStatements = linkedSetOf<String>()
        declarations.forEach { declaration ->
            declaration.fields.forEach { field ->
                collectImports(field.typeReference, language, importStatements)
                if (field.sourceName != field.name || options.annotationStyle != JsonToTypeAnnotationStyle.NONE) {
                    when (language) {
                        SupportedLanguage.JAVA -> when (options.annotationStyle) {
                            JsonToTypeAnnotationStyle.GSON_SERIALIZED_NAME ->
                                importStatements += "import com.google.gson.annotations.SerializedName;"
                            JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY ->
                                importStatements += "import com.fasterxml.jackson.annotation.JsonProperty;"
                            else -> Unit
                        }
                        SupportedLanguage.KOTLIN -> when (options.annotationStyle) {
                            JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY ->
                                importStatements += "import com.fasterxml.jackson.annotation.JsonProperty"
                            JsonToTypeAnnotationStyle.KOTLIN_SERIAL_NAME ->
                                importStatements += "import kotlinx.serialization.SerialName"
                            else -> Unit
                        }
                        else -> Unit
                    }
                }
            }
            if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS && language == SupportedLanguage.JAVA) {
                importStatements += "import java.util.ArrayList;"
                importStatements += "import java.util.LinkedHashMap;"
            }
        }
        return importStatements.joinToString("\n")
    }

    private fun collectImports(
        typeReference: TypeReference,
        language: SupportedLanguage,
        importStatements: MutableSet<String>,
    ) {
        when (typeReference) {
            TypeReference.AnyValue -> when (language) {
                SupportedLanguage.JAVA -> importStatements += "import java.util.Map;"
                else -> Unit
            }
            is TypeReference.InlineObject -> typeReference.fields.forEach { collectImports(it.typeReference, language, importStatements) }
            is TypeReference.ListReference -> {
                when (language) {
                    SupportedLanguage.JAVA -> importStatements += "import java.util.List;"
                    SupportedLanguage.KOTLIN -> Unit
                    SupportedLanguage.GO -> Unit
                    SupportedLanguage.TYPESCRIPT -> Unit
                }
                collectImports(typeReference.elementType, language, importStatements)
            }
            is TypeReference.MapReference -> {
                when (language) {
                    SupportedLanguage.JAVA -> importStatements += "import java.util.Map;"
                    else -> Unit
                }
                collectImports(typeReference.keyType, language, importStatements)
                collectImports(typeReference.valueType, language, importStatements)
            }
            is TypeReference.Nullable -> collectImports(typeReference.wrappedType, language, importStatements)
            is TypeReference.Primitive -> Unit
            is TypeReference.Named -> Unit
            is TypeReference.Union -> typeReference.members.forEach { collectImports(it, language, importStatements) }
        }
    }

    private fun renderDeclaration(
        declaration: TypeDeclaration,
        language: SupportedLanguage,
        options: JsonToTypeConversionOptions,
    ): String {
        return when (language) {
            SupportedLanguage.TYPESCRIPT -> renderTypescriptDeclaration(declaration, options)
            SupportedLanguage.GO -> renderGoDeclaration(declaration, options)
            SupportedLanguage.JAVA -> renderJavaDeclaration(declaration, options)
            SupportedLanguage.KOTLIN -> renderKotlinDeclaration(declaration, options)
        }
    }

    private fun renderTypescriptDeclaration(
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
            "  ${field.name}${if (field.isOptional) "?" else ""}: ${renderTypescriptType(field.typeReference, options)};"
        }
        return "export interface ${declaration.name} {\n$fieldsText\n}"
    }

    private fun renderGoDeclaration(
        declaration: TypeDeclaration,
        options: JsonToTypeConversionOptions,
    ): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) {
            return "type ${declaration.name} = ${renderGoType(declaration.aliasedTypeReference ?: TypeReference.AnyValue, options)}"
        }
        val fieldsText = declaration.fields.joinToString("\n") { field ->
            val exportedName = JsonToTypeNamingSupport.toFieldName(
                rawName = field.name,
                namingConvention = NamingConvention.PASCAL_CASE,
                language = SupportedLanguage.GO,
            )
            val tag = if (options.annotationStyle == JsonToTypeAnnotationStyle.GO_JSON_TAG) {
                val omitemptySuffix = if (field.isOptional) ",omitempty" else ""
                " `json:\"${field.sourceName}$omitemptySuffix\"`"
            } else {
                ""
            }
            "    $exportedName ${renderGoType(field.typeReference, options)}$tag"
        }
        return "type ${declaration.name} struct {\n$fieldsText\n}"
    }

    private fun renderJavaDeclaration(
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

        val fieldsText = declaration.fields.joinToString("\n\n") { field ->
            val annotationText = renderJavaFieldAnnotation(field, options)
            val fieldType = renderJavaType(field.typeReference, options)
            val capitalizedFieldName = field.name.replaceFirstChar(Char::titlecase)
            listOfNotNull(
                annotationText,
                "    private $fieldType ${field.name};",
                "    public $fieldType get$capitalizedFieldName() {\n        return ${field.name};\n    }",
                "    public void set$capitalizedFieldName($fieldType ${field.name}) {\n        this.${field.name} = ${field.name};\n    }",
            ).joinToString("\n")
        }

        return "public class ${declaration.name} {\n$fieldsText\n}"
    }

    private fun renderKotlinDeclaration(
        declaration: TypeDeclaration,
        options: JsonToTypeConversionOptions,
    ): String {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) {
            return "typealias ${declaration.name} = ${renderKotlinType(declaration.aliasedTypeReference ?: TypeReference.AnyValue, options)}"
        }
        val constructorFields = declaration.fields.joinToString(",\n") { field ->
            val annotationText = renderKotlinFieldAnnotation(field, options)
            val typeText = renderKotlinType(field.typeReference, options)
            val prefix = annotationText?.let { "    $it\n    " } ?: "    "
            "$prefix${if (field.isOptional) "val" else "val"} ${field.name}: $typeText"
        }
        return "data class ${declaration.name}(\n$constructorFields\n)"
    }

    private fun renderTypescriptType(
        typeReference: TypeReference,
        options: JsonToTypeConversionOptions,
    ): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "any"
            is TypeReference.InlineObject -> "{ " + typeReference.fields.joinToString("; ") {
                "${it.name}${if (it.isOptional) "?" else ""}: ${renderTypescriptType(it.typeReference, options)}"
            } + " }"
            is TypeReference.ListReference -> "${renderTypescriptType(typeReference.elementType, options)}[]"
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

    private fun renderGoType(
        typeReference: TypeReference,
        options: JsonToTypeConversionOptions,
    ): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "any"
            is TypeReference.InlineObject -> "map[string]any"
            is TypeReference.ListReference -> "[]${renderGoType(typeReference.elementType, options)}"
            is TypeReference.MapReference -> "map[${renderGoType(typeReference.keyType, options)}]${renderGoType(typeReference.valueType, options)}"
            is TypeReference.Named -> typeReference.name
            is TypeReference.Nullable -> "*${renderGoType(typeReference.wrappedType, options)}"
            is TypeReference.Primitive -> when (typeReference.primitiveKind) {
                TypePrimitiveKind.STRING -> "string"
                TypePrimitiveKind.INTEGER -> "int"
                TypePrimitiveKind.DECIMAL, TypePrimitiveKind.NUMBER -> "float64"
                TypePrimitiveKind.BOOLEAN -> "bool"
            }
            is TypeReference.Union -> if (options.usesExperimentalGoUnionTypes) {
                typeReference.members.joinToString(" | ") { renderGoType(it, options) }
            } else {
                "any"
            }
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

    private fun renderJavaFieldAnnotation(
        field: TypeField,
        options: JsonToTypeConversionOptions,
    ): String? {
        return when (options.annotationStyle) {
            JsonToTypeAnnotationStyle.GSON_SERIALIZED_NAME -> "    @SerializedName(\"${field.sourceName}\")"
            JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> "    @JsonProperty(\"${field.sourceName}\")"
            else -> null
        }
    }

    private fun renderKotlinFieldAnnotation(
        field: TypeField,
        options: JsonToTypeConversionOptions,
    ): String? {
        return when (options.annotationStyle) {
            JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> "@JsonProperty(\"${field.sourceName}\")"
            JsonToTypeAnnotationStyle.KOTLIN_SERIAL_NAME -> "@SerialName(\"${field.sourceName}\")"
            else -> null
        }
    }
}
