package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeField
import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import com.livteam.jsoninja.model.typeConversion.TypeReference

internal object JsonToTypeGoJsonSupport {
    fun exportedName(field: TypeField): String = JsonToTypeNamingSupport.toFieldName(
        field.name, NamingConvention.PASCAL_CASE, SupportedLanguage.GO,
    )

    fun requiresCodec(declaration: TypeDeclaration, options: JsonToTypeConversionOptions): Boolean {
        if (declaration.declarationKind == TypeDeclarationKind.TYPE_ALIAS) return false
        return declaration.fields.any { field ->
            if (options.annotationStyle == JsonToTypeAnnotationStyle.GO_JSON_TAG) {
                !isValidTagName(field.sourceName)
            } else field.sourceName != exportedName(field)
        }
    }

    // encoding/json rejects these names even when the Go string literal itself is valid.
    private fun isValidTagName(name: String): Boolean = name.isNotEmpty() && name != "-" && name.all {
        it.isLetterOrDigit() || it in "!#$%&()*+-./:;<=>?@[]^_{|}~ "
    }

    fun renderCodec(declaration: TypeDeclaration, options: JsonToTypeConversionOptions): String {
        val marshalFields = declaration.fields.joinToString("\n") { field ->
            val sourceName = JsonToTypeLiteralSupport.quote(field.sourceName, SupportedLanguage.GO)
            val access = "value.${exportedName(field)}"
            val assignment = "fields[$sourceName] = $access"
            val condition = if (field.isOptional && options.annotationStyle == JsonToTypeAnnotationStyle.GO_JSON_TAG) {
                nonEmptyCondition(field.typeReference, access)
            } else null
            if (condition == null) "    $assignment" else "    if $condition {\n        $assignment\n    }"
        }
        val unmarshalFields = declaration.fields.joinToString("\n") { field ->
            val sourceName = JsonToTypeLiteralSupport.quote(field.sourceName, SupportedLanguage.GO)
            """
                |    if raw, ok := fields[$sourceName]; ok {
                |        if err := json.Unmarshal(raw, &value.${exportedName(field)}); err != nil {
                |            return err
                |        }
                |    }
            """.trimMargin()
        }
        return """
            |func (value ${declaration.name}) MarshalJSON() ([]byte, error) {
            |    fields := make(map[string]any)
            |$marshalFields
            |    return json.Marshal(fields)
            |}
            |
            |func (value *${declaration.name}) UnmarshalJSON(data []byte) error {
            |    var fields map[string]json.RawMessage
            |    if err := json.Unmarshal(data, &fields); err != nil {
            |        return err
            |    }
            |$unmarshalFields
            |    return nil
            |}
        """.trimMargin()
    }

    private fun nonEmptyCondition(type: TypeReference, access: String): String? = when (type) {
        is TypeReference.Named -> null
        is TypeReference.ListReference, is TypeReference.MapReference, is TypeReference.InlineObject -> "len($access) > 0"
        is TypeReference.Primitive -> when (type.primitiveKind) {
            TypePrimitiveKind.STRING -> "$access != \"\""
            TypePrimitiveKind.BOOLEAN -> access
            else -> "$access != 0"
        }
        else -> "$access != nil"
    }
}
