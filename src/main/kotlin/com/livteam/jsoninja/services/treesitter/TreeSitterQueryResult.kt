package com.livteam.jsoninja.services.treesitter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisDiagnostic
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisResult
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisSeverity
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeField
import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import com.livteam.jsoninja.model.typeConversion.TypeReference

data class AnalysisOutput(
    val language: String,
    val declarations: List<WasmDeclaration>,
    val diagnostics: List<WasmDiagnostic>,
) {
    fun toTypeAnalysisResult(): TypeAnalysisResult {
        return TypeAnalysisResult(
            declarations = declarations.map(WasmDeclaration::toDomainModel),
            diagnostics = diagnostics.map(WasmDiagnostic::toDomainModel),
        )
    }
}

data class WasmDeclaration(
    val name: String,
    val kind: String,
    val superTypes: List<WasmTypeReference>,
    val fields: List<WasmField>,
    val enumValues: List<WasmEnumValue>,
    val aliasedType: WasmTypeReference?,
) {
    fun toDomainModel(): TypeDeclaration {
        return TypeDeclaration(
            name = name,
            declarationKind = kind.toDeclarationKind(),
            fields = fields.map(WasmField::toDomainModel),
            superTypeNames = superTypes.mapNotNull(WasmTypeReference::toSuperTypeName),
            enumValues = enumValues.map(WasmEnumValue::name),
            aliasedTypeReference = aliasedType?.toDomainModel(),
        )
    }
}

data class WasmField(
    val name: String,
    val sourceName: String,
    val optional: Boolean,
    val typeReference: WasmTypeReference,
) {
    fun toDomainModel(): TypeField {
        return TypeField(
            name = name,
            typeReference = typeReference.toDomainModel(),
            isOptional = optional,
            sourceName = sourceName,
        )
    }
}

data class WasmEnumValue(
    val name: String,
)

data class WasmDiagnostic(
    val code: String,
    val severity: String,
    val message: String,
    val declarationName: String?,
) {
    fun toDomainModel(): TypeAnalysisDiagnostic {
        return TypeAnalysisDiagnostic(
            code = code,
            severity = if (severity.equals("warning", ignoreCase = true)) {
                TypeAnalysisSeverity.WARNING
            } else {
                TypeAnalysisSeverity.ERROR
            },
            message = message,
            declarationName = declarationName,
        )
    }
}

sealed interface WasmTypeReference {
    fun toDomainModel(): TypeReference

    fun toSuperTypeName(): String? {
        return (toDomainModel() as? TypeReference.Named)?.name
    }
}

data class WasmPrimitiveTypeReference(
    val primitive: String,
) : WasmTypeReference {
    override fun toDomainModel(): TypeReference {
        return TypeReference.Primitive(
            when (primitive.lowercase()) {
                "string" -> TypePrimitiveKind.STRING
                "integer" -> TypePrimitiveKind.INTEGER
                "decimal" -> TypePrimitiveKind.DECIMAL
                "boolean" -> TypePrimitiveKind.BOOLEAN
                else -> TypePrimitiveKind.NUMBER
            },
        )
    }
}

data class WasmNamedTypeReference(
    val name: String,
) : WasmTypeReference {
    override fun toDomainModel(): TypeReference = TypeReference.Named(name)
}

data class WasmListTypeReference(
    val elementType: WasmTypeReference,
) : WasmTypeReference {
    override fun toDomainModel(): TypeReference = TypeReference.ListReference(elementType.toDomainModel())
}

data class WasmMapTypeReference(
    val keyType: WasmTypeReference,
    val valueType: WasmTypeReference,
) : WasmTypeReference {
    override fun toDomainModel(): TypeReference {
        return TypeReference.MapReference(
            keyType = keyType.toDomainModel(),
            valueType = valueType.toDomainModel(),
        )
    }
}

data class WasmNullableTypeReference(
    val wrappedType: WasmTypeReference,
) : WasmTypeReference {
    override fun toDomainModel(): TypeReference = TypeReference.Nullable(wrappedType.toDomainModel())
}

data class WasmUnionTypeReference(
    val members: List<WasmTypeReference>,
) : WasmTypeReference {
    override fun toDomainModel(): TypeReference = TypeReference.Union(members.map(WasmTypeReference::toDomainModel))
}

data class WasmInlineObjectTypeReference(
    val fields: List<WasmField>,
) : WasmTypeReference {
    override fun toDomainModel(): TypeReference = TypeReference.InlineObject(fields.map(WasmField::toDomainModel))
}

data class WasmUnknownTypeReference(
    val rawText: String,
) : WasmTypeReference {
    override fun toDomainModel(): TypeReference = TypeReference.AnyValue
}

object TreeSitterQueryResult {
    fun parse(
        jsonText: String,
        objectMapper: ObjectMapper,
    ): AnalysisOutput {
        val rootNode = objectMapper.readTree(jsonText)
        return AnalysisOutput(
            language = rootNode.path("language").asText(""),
            declarations = rootNode.path("declarations").map(::parseDeclaration),
            diagnostics = rootNode.path("diagnostics").map(::parseDiagnostic),
        )
    }

    private fun parseDeclaration(node: JsonNode): WasmDeclaration {
        return WasmDeclaration(
            name = node.path("name").asText(""),
            kind = node.path("kind").asText("class"),
            superTypes = node.path("super_types").map(::parseTypeReference),
            fields = node.path("fields").map(::parseField),
            enumValues = node.path("enum_values").map { WasmEnumValue(name = it.path("name").asText("")) },
            aliasedType = node.path("aliased_type").takeUnless(JsonNode::isNull)?.let(::parseTypeReference),
        )
    }

    private fun parseField(node: JsonNode): WasmField {
        return WasmField(
            name = node.path("name").asText(""),
            sourceName = node.path("source_name").asText(node.path("name").asText("")),
            optional = node.path("optional").asBoolean(false),
            typeReference = parseTypeReference(node.path("type")),
        )
    }

    private fun parseDiagnostic(node: JsonNode): WasmDiagnostic {
        return WasmDiagnostic(
            code = node.path("code").asText("unknown"),
            severity = node.path("severity").asText("error"),
            message = node.path("message").asText("Unknown WASM analysis error"),
            declarationName = node.path("declaration_name").takeUnless(JsonNode::isNull)?.asText(),
        )
    }

    private fun parseTypeReference(node: JsonNode): WasmTypeReference {
        return when (node.path("kind").asText("unknown")) {
            "primitive" -> WasmPrimitiveTypeReference(primitive = node.path("primitive").asText("number"))
            "named" -> WasmNamedTypeReference(name = node.path("name").asText("Any"))
            "list" -> WasmListTypeReference(elementType = parseTypeReference(node.path("element_type")))
            "map" -> WasmMapTypeReference(
                keyType = parseTypeReference(node.path("key_type")),
                valueType = parseTypeReference(node.path("value_type")),
            )
            "nullable" -> WasmNullableTypeReference(wrappedType = parseTypeReference(node.path("wrapped_type")))
            "union" -> WasmUnionTypeReference(members = node.path("members").map(::parseTypeReference))
            "inline_object" -> WasmInlineObjectTypeReference(fields = node.path("fields").map(::parseField))
            else -> WasmUnknownTypeReference(rawText = node.path("raw_text").asText(""))
        }
    }

}

private fun String.toDeclarationKind(): TypeDeclarationKind {
    return when (lowercase()) {
        "class" -> TypeDeclarationKind.CLASS
        "interface" -> TypeDeclarationKind.INTERFACE
        "record" -> TypeDeclarationKind.RECORD
        "struct" -> TypeDeclarationKind.STRUCT
        "enum" -> TypeDeclarationKind.ENUM
        "object" -> TypeDeclarationKind.OBJECT
        else -> TypeDeclarationKind.TYPE_ALIAS
    }
}
