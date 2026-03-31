package com.livteam.jsoninja.model.typeConversion

enum class TypeConversionLanguage(
    val displayName: String,
    val resourceDirectoryName: String,
) {
    JAVA(displayName = "Java", resourceDirectoryName = "java"),
    KOTLIN(displayName = "Kotlin", resourceDirectoryName = "kotlin"),
    TYPESCRIPT(displayName = "TypeScript", resourceDirectoryName = "typescript"),
    GO(displayName = "Go", resourceDirectoryName = "go"),
}

enum class TypeDeclarationKind {
    CLASS,
    INTERFACE,
    RECORD,
    STRUCT,
    ENUM,
}

enum class TypePrimitiveKind {
    STRING,
    INTEGER,
    DECIMAL,
    NUMBER,
    BOOLEAN,
}

data class TypeAnalysisResult(
    val language: TypeConversionLanguage,
    val queryResourcePath: String,
    val parserResourcePath: String?,
    val rootTypeName: String,
    val typeDeclarations: Map<String, TypeDeclaration>,
) {
    val rootType: TypeDeclaration
        get() = typeDeclarations[rootTypeName]
            ?: throw IllegalStateException("Root type not found: $rootTypeName")
}

data class TypeDeclaration(
    val name: String,
    val declarationKind: TypeDeclarationKind,
    val fields: List<TypeField>,
    val superTypeNames: List<String> = emptyList(),
    val enumValues: List<String> = emptyList(),
)

data class TypeField(
    val name: String,
    val typeReference: TypeReference,
    val isOptional: Boolean = false,
    val sourceName: String = name,
)

sealed interface TypeReference {
    data class Primitive(val primitiveKind: TypePrimitiveKind) : TypeReference
    data class Named(val name: String) : TypeReference
    data class ListReference(val elementType: TypeReference) : TypeReference
    data class MapReference(
        val keyType: TypeReference,
        val valueType: TypeReference,
    ) : TypeReference

    data class Nullable(val wrappedType: TypeReference) : TypeReference
    data class InlineObject(val fields: List<TypeField>) : TypeReference

    object AnyValue : TypeReference
}
