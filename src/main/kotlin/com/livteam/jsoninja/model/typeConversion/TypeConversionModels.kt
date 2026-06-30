package com.livteam.jsoninja.model.typeConversion

sealed interface TypeReference {
    data class Primitive(
        val primitiveKind: TypePrimitiveKind,
    ) : TypeReference

    data class Named(
        val name: String,
    ) : TypeReference

    data class ListReference(
        val elementType: TypeReference,
    ) : TypeReference

    data class MapReference(
        val keyType: TypeReference,
        val valueType: TypeReference,
    ) : TypeReference

    data class Nullable(
        val wrappedType: TypeReference,
    ) : TypeReference

    data class InlineObject(
        val fields: List<TypeField>,
    ) : TypeReference

    data class Union(
        val members: List<TypeReference>,
    ) : TypeReference

    data object AnyValue : TypeReference
}

enum class TypePrimitiveKind {
    STRING,
    INTEGER,
    DECIMAL,
    NUMBER,
    BOOLEAN,
}

data class TypeDeclaration(
    val name: String,
    val declarationKind: TypeDeclarationKind,
    val fields: List<TypeField> = emptyList(),
    val superTypeNames: List<String> = emptyList(),
    val enumValues: List<String> = emptyList(),
    val aliasedTypeReference: TypeReference? = null,
)

enum class TypeDeclarationKind {
    CLASS,
    INTERFACE,
    RECORD,
    STRUCT,
    ENUM,
    TYPE_ALIAS,
    OBJECT,
}

data class TypeField(
    val name: String,
    val typeReference: TypeReference,
    val isOptional: Boolean = false,
    val sourceName: String = name,
)

data class TypeConversionWarning(
    val message: String,
)

data class TypeAnalysisResult(
    val declarations: List<TypeDeclaration>,
    val diagnostics: List<TypeAnalysisDiagnostic> = emptyList(),
)

data class TypeAnalysisDiagnostic(
    val code: String,
    val severity: TypeAnalysisSeverity,
    val message: String,
    val declarationName: String? = null,
)

enum class TypeAnalysisSeverity {
    ERROR,
    WARNING,
}

data class JsonToTypeConversionResult(
    val sourceCode: String,
    val warnings: List<TypeConversionWarning> = emptyList(),
    val declarations: List<TypeDeclaration> = emptyList(),
)
