package com.livteam.jsoninja.services.treesitter

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectMapper
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisDiagnostic
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisResult
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisSeverity
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeField
import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import com.livteam.jsoninja.model.typeConversion.TypeReference
import com.livteam.jsoninja.model.typeConversion.TypeEnumValue
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguageRegistry

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
    val enumLiteralValues: List<TypeEnumValue> = emptyList(),
) {
    fun toDomainModel(): TypeDeclaration {
        return TypeDeclaration(
            name = name,
            declarationKind = kind.toDeclarationKind(),
            fields = fields.map(WasmField::toDomainModel),
            superTypeNames = superTypes.mapNotNull(WasmTypeReference::toSuperTypeName),
            enumValues = enumValues.map(WasmEnumValue::name),
            aliasedTypeReference = aliasedType?.toDomainModel(),
            enumLiteralValues = enumLiteralValues,
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
    val valueText: String? = null,
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
    // WASM emits strict RFC-8259 JSON; streaming directly skips Json5InputNormalizer and JsonNode DOM allocation.
    private val strictJsonFactory = JsonFactory()

    fun parse(
        jsonText: String,
        objectMapper: ObjectMapper,
    ): AnalysisOutput {
        return strictJsonFactory.createParser(jsonText).use { parser ->
            parseFromStream(parser, objectMapper)
        }
    }

    internal fun parseUtf8Bytes(
        jsonBytes: ByteArray,
        enumValueMapper: ObjectMapper,
    ): AnalysisOutput {
        return strictJsonFactory.createParser(jsonBytes).use { parser ->
            parseFromStream(parser, enumValueMapper)
        }
    }

    private fun parseFromStream(
        parser: JsonParser,
        enumValueMapper: ObjectMapper,
    ): AnalysisOutput {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            return AnalysisOutput(language = "", declarations = emptyList(), diagnostics = emptyList())
        }

        var language = ""
        val diagnostics = mutableListOf<WasmDiagnostic>()
        val rawDeclarations = mutableListOf<WasmDeclaration>()

        while (true) {
            val fieldToken = parser.nextToken()
            if (fieldToken == null || fieldToken == JsonToken.END_OBJECT) break
            val fieldName = parser.currentName()
            val valueToken = parser.nextToken() ?: break
            when (fieldName) {
                "language" -> language = readScalarString(parser, valueToken, "")
                "diagnostics" -> {
                    if (valueToken == JsonToken.START_ARRAY) {
                        while (true) {
                            val itemToken = parser.nextToken()
                            if (itemToken == null || itemToken == JsonToken.END_ARRAY) break
                            diagnostics += parseDiagnostic(parser)
                        }
                    } else {
                        parser.skipChildren()
                    }
                }
                "declarations" -> {
                    if (valueToken == JsonToken.START_ARRAY) {
                        while (true) {
                            val itemToken = parser.nextToken()
                            if (itemToken == null || itemToken == JsonToken.END_ARRAY) break
                            rawDeclarations += parseDeclaration(parser)
                        }
                    } else {
                        parser.skipChildren()
                    }
                }
                else -> parser.skipChildren()
            }
        }

        val declarations = rawDeclarations.map { declaration ->
            resolveEnumDeclaration(declaration, language, diagnostics, enumValueMapper)
        }
        return AnalysisOutput(
            language = language,
            declarations = declarations,
            diagnostics = diagnostics,
        )
    }

    private fun resolveEnumDeclaration(
        declaration: WasmDeclaration,
        language: String,
        diagnostics: MutableList<WasmDiagnostic>,
        enumValueMapper: ObjectMapper,
    ): WasmDeclaration {
        if (declaration.kind != "enum") return declaration
        if (language != "typescript") {
            val selectedLanguage = SupportedLanguage.entries.firstOrNull { it.resourceKey == language }
                ?: return declaration
            val values = TypeLanguageRegistry.forLanguage(selectedLanguage).policy.resolveEnumValues(
                declaration.enumValues.map { it.name to it.valueText },
            ) ?: return declaration
            values.forEachIndexed { index, value ->
                if (value is TypeEnumValue.Unresolved) {
                    diagnostics += WasmDiagnostic(
                        "unsupported_enum_value",
                        "error",
                        LocalizationBundle.message(
                            "validation.type.to.json.enum.value.unsupported",
                            "${declaration.name}.${declaration.enumValues[index].name}",
                        ),
                        declaration.name,
                    )
                }
            }
            return declaration.copy(enumLiteralValues = values)
        }

        val literalValues = TypeScriptEnumValueResolver.resolve(declaration.enumValues, enumValueMapper) { member ->
            diagnostics.add(
                WasmDiagnostic(
                    code = "unsupported_enum_value",
                    severity = "error",
                    message = LocalizationBundle.message(
                        "validation.type.to.json.enum.value.unsupported",
                        "${declaration.name}.${member.name}",
                    ),
                    declarationName = declaration.name,
                ),
            )
        }
        return declaration.copy(enumLiteralValues = literalValues)
    }

    private fun parseDeclaration(parser: JsonParser): WasmDeclaration {
        if (parser.currentToken != JsonToken.START_OBJECT) {
            parser.skipChildren()
            return WasmDeclaration("", "class", emptyList(), emptyList(), emptyList(), null)
        }

        var name = ""
        var kind = "class"
        var superTypes: List<WasmTypeReference> = emptyList()
        var fields: List<WasmField> = emptyList()
        var enumValues: List<WasmEnumValue> = emptyList()
        var aliasedType: WasmTypeReference? = null

        while (true) {
            val fieldToken = parser.nextToken()
            if (fieldToken == null || fieldToken == JsonToken.END_OBJECT) break
            val fieldName = parser.currentName()
            val valueToken = parser.nextToken() ?: break
            when (fieldName) {
                "name" -> name = readScalarString(parser, valueToken, "")
                "kind" -> kind = readScalarString(parser, valueToken, "class")
                "super_types" -> {
                    if (valueToken == JsonToken.START_ARRAY) {
                        val list = mutableListOf<WasmTypeReference>()
                        while (true) {
                            val itemToken = parser.nextToken()
                            if (itemToken == null || itemToken == JsonToken.END_ARRAY) break
                            list += parseTypeReference(parser)
                        }
                        superTypes = list
                    } else {
                        parser.skipChildren()
                    }
                }
                "fields" -> {
                    if (valueToken == JsonToken.START_ARRAY) {
                        val list = mutableListOf<WasmField>()
                        while (true) {
                            val itemToken = parser.nextToken()
                            if (itemToken == null || itemToken == JsonToken.END_ARRAY) break
                            list += parseField(parser)
                        }
                        fields = list
                    } else {
                        parser.skipChildren()
                    }
                }
                "enum_values" -> {
                    if (valueToken == JsonToken.START_ARRAY) {
                        val list = mutableListOf<WasmEnumValue>()
                        while (true) {
                            val itemToken = parser.nextToken()
                            if (itemToken == null || itemToken == JsonToken.END_ARRAY) break
                            list += parseEnumValue(parser)
                        }
                        enumValues = list
                    } else {
                        parser.skipChildren()
                    }
                }
                "aliased_type" -> {
                    aliasedType = if (valueToken == JsonToken.VALUE_NULL) null else parseTypeReference(parser)
                }
                else -> parser.skipChildren()
            }
        }

        return WasmDeclaration(
            name = name,
            kind = kind,
            superTypes = superTypes,
            fields = fields,
            enumValues = enumValues,
            aliasedType = aliasedType,
        )
    }

    private fun parseEnumValue(parser: JsonParser): WasmEnumValue {
        if (parser.currentToken != JsonToken.START_OBJECT) {
            parser.skipChildren()
            return WasmEnumValue("")
        }

        var name = ""
        var valueText: String? = null
        while (true) {
            val fieldToken = parser.nextToken()
            if (fieldToken == null || fieldToken == JsonToken.END_OBJECT) break
            val fieldName = parser.currentName()
            val valueToken = parser.nextToken() ?: break
            when (fieldName) {
                "name" -> name = readScalarString(parser, valueToken, "")
                "value_text" -> {
                    valueText = if (valueToken == JsonToken.VALUE_STRING) {
                        parser.text
                    } else {
                        parser.skipChildren()
                        null
                    }
                }
                else -> parser.skipChildren()
            }
        }
        return WasmEnumValue(name = name, valueText = valueText)
    }

    private fun parseField(parser: JsonParser): WasmField {
        if (parser.currentToken != JsonToken.START_OBJECT) {
            parser.skipChildren()
            return WasmField("", "", false, WasmUnknownTypeReference(""))
        }

        var name = ""
        var sourceName: String? = null
        var optional = false
        var typeReference: WasmTypeReference = WasmUnknownTypeReference("")

        while (true) {
            val fieldToken = parser.nextToken()
            if (fieldToken == null || fieldToken == JsonToken.END_OBJECT) break
            val fieldName = parser.currentName()
            val valueToken = parser.nextToken() ?: break
            when (fieldName) {
                "name" -> name = readScalarString(parser, valueToken, "")
                "source_name" -> sourceName = readNullableScalarString(parser, valueToken)
                "optional" -> optional = readBooleanValue(parser, valueToken)
                "type" -> typeReference = parseTypeReference(parser)
                else -> parser.skipChildren()
            }
        }

        return WasmField(
            name = name,
            sourceName = sourceName ?: name,
            optional = optional,
            typeReference = typeReference,
        )
    }

    private fun parseDiagnostic(parser: JsonParser): WasmDiagnostic {
        if (parser.currentToken != JsonToken.START_OBJECT) {
            parser.skipChildren()
            return WasmDiagnostic("unknown", "error", "Unknown WASM analysis error", null)
        }

        var code = "unknown"
        var severity = "error"
        var message = "Unknown WASM analysis error"
        var declarationName: String? = null

        while (true) {
            val fieldToken = parser.nextToken()
            if (fieldToken == null || fieldToken == JsonToken.END_OBJECT) break
            val fieldName = parser.currentName()
            val valueToken = parser.nextToken() ?: break
            when (fieldName) {
                "code" -> code = readScalarString(parser, valueToken, "unknown")
                "severity" -> severity = readScalarString(parser, valueToken, "error")
                "message" -> message = readScalarString(parser, valueToken, "Unknown WASM analysis error")
                "declaration_name" -> declarationName = readNullableScalarString(parser, valueToken)
                else -> parser.skipChildren()
            }
        }

        return WasmDiagnostic(
            code = code,
            severity = severity,
            message = message,
            declarationName = declarationName,
        )
    }

    private fun parseTypeReference(parser: JsonParser): WasmTypeReference {
        if (parser.currentToken != JsonToken.START_OBJECT) {
            parser.skipChildren()
            return WasmUnknownTypeReference("")
        }

        var kind = "unknown"
        var primitive = "number"
        var name = "Any"
        var elementType: WasmTypeReference? = null
        var keyType: WasmTypeReference? = null
        var valueType: WasmTypeReference? = null
        var wrappedType: WasmTypeReference? = null
        var members: List<WasmTypeReference> = emptyList()
        var fields: List<WasmField> = emptyList()
        var rawText = ""

        while (true) {
            val fieldToken = parser.nextToken()
            if (fieldToken == null || fieldToken == JsonToken.END_OBJECT) break
            val fieldName = parser.currentName()
            val valueToken = parser.nextToken() ?: break
            when (fieldName) {
                "kind" -> kind = readScalarString(parser, valueToken, "unknown")
                "primitive" -> primitive = readScalarString(parser, valueToken, "number")
                "name" -> name = readScalarString(parser, valueToken, "Any")
                "element_type" -> elementType = parseTypeReference(parser)
                "key_type" -> keyType = parseTypeReference(parser)
                "value_type" -> valueType = parseTypeReference(parser)
                "wrapped_type" -> wrappedType = parseTypeReference(parser)
                "members" -> {
                    if (valueToken == JsonToken.START_ARRAY) {
                        val list = mutableListOf<WasmTypeReference>()
                        while (true) {
                            val itemToken = parser.nextToken()
                            if (itemToken == null || itemToken == JsonToken.END_ARRAY) break
                            list += parseTypeReference(parser)
                        }
                        members = list
                    } else {
                        parser.skipChildren()
                    }
                }
                "fields" -> {
                    if (valueToken == JsonToken.START_ARRAY) {
                        val list = mutableListOf<WasmField>()
                        while (true) {
                            val itemToken = parser.nextToken()
                            if (itemToken == null || itemToken == JsonToken.END_ARRAY) break
                            list += parseField(parser)
                        }
                        fields = list
                    } else {
                        parser.skipChildren()
                    }
                }
                "raw_text" -> rawText = readScalarString(parser, valueToken, "")
                else -> parser.skipChildren()
            }
        }

        return when (kind) {
            "primitive" -> WasmPrimitiveTypeReference(primitive = primitive)
            "named" -> WasmNamedTypeReference(name = name)
            "list" -> WasmListTypeReference(elementType = elementType ?: WasmUnknownTypeReference(""))
            "map" -> WasmMapTypeReference(
                keyType = keyType ?: WasmUnknownTypeReference(""),
                valueType = valueType ?: WasmUnknownTypeReference(""),
            )
            "nullable" -> WasmNullableTypeReference(wrappedType = wrappedType ?: WasmUnknownTypeReference(""))
            "union" -> WasmUnionTypeReference(members = members)
            "inline_object" -> WasmInlineObjectTypeReference(fields = fields)
            else -> WasmUnknownTypeReference(rawText = rawText)
        }
    }

    private fun readScalarString(parser: JsonParser, token: JsonToken, defaultValue: String): String {
        if (token.isStructStart) {
            parser.skipChildren()
            return defaultValue
        }
        return if (token == JsonToken.VALUE_NULL) defaultValue else (parser.valueAsString ?: defaultValue)
    }

    private fun readNullableScalarString(parser: JsonParser, token: JsonToken): String? {
        if (token.isStructStart) {
            parser.skipChildren()
            return null
        }
        return if (token == JsonToken.VALUE_NULL) null else parser.valueAsString
    }

    private fun readBooleanValue(parser: JsonParser, token: JsonToken): Boolean {
        if (token.isStructStart) {
            parser.skipChildren()
            return false
        }
        return parser.valueAsBoolean
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
