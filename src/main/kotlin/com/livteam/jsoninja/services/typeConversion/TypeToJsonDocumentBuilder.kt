package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeReference
import com.livteam.jsoninja.ui.dialog.generateJson.model.SchemaPropertyGenerationMode
import java.util.IdentityHashMap

class TypeToJsonDocumentBuilder(
    private val objectMapper: ObjectMapper,
    private val nodeGenerator: TypeToJsonNodeGenerator = TypeToJsonNodeGenerator(objectMapper),
) {
    fun buildDocument(
        declarations: List<TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        rootTypeName: String? = null,
    ): JsonNode = buildDocument(declarations, options, rootTypeName, null)

    fun buildCommentedDocument(
        declarations: List<TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        rootTypeName: String?,
    ): String {
        val optionalFields = IdentityHashMap<ObjectNode, MutableSet<String>>()
        val document = buildDocument(
            declarations,
            options.copy(propertyGenerationMode = SchemaPropertyGenerationMode.REQUIRED_AND_OPTIONAL),
            rootTypeName,
        ) { node, name -> optionalFields.getOrPut(node) { mutableSetOf() }.add(name) }
        return TypeToJsonCommentRenderer(objectMapper).render(document, optionalFields, options.formatState)
    }

    private fun buildDocument(
        declarations: List<TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        rootTypeName: String?,
        onOptionalField: ((ObjectNode, String) -> Unit)?,
    ): JsonNode {
        val declarationsByName = declarations.associateBy(TypeDeclaration::name)
        val rootDeclaration = selectRootDeclaration(
            declarations = declarations,
            declarationsByName = declarationsByName,
            rootTypeName = rootTypeName,
        )
        val outputCount = options.outputCount.coerceIn(1, 100)
        val rootDocument = buildSingleDocument(rootDeclaration, declarationsByName, options, onOptionalField)

        if (outputCount <= 1) {
            return rootDocument
        }

        if (rootDocument is ArrayNode) {
            val mergedArrayNode = objectMapper.createArrayNode()
            mergedArrayNode.addAll(rootDocument)

            // Keep array roots flat by appending generated elements into the same array.
            repeat(outputCount - 1) {
                val generatedDocument = buildSingleDocument(rootDeclaration, declarationsByName, options, onOptionalField)
                if (generatedDocument is ArrayNode) {
                    mergedArrayNode.addAll(generatedDocument)
                } else {
                    mergedArrayNode.add(generatedDocument)
                }
            }
            return mergedArrayNode
        }

        val arrayNode = objectMapper.createArrayNode()
        arrayNode.add(rootDocument)
        repeat(outputCount - 1) {
            arrayNode.add(buildSingleDocument(rootDeclaration, declarationsByName, options, onOptionalField))
        }
        return arrayNode
    }

    private fun buildSingleDocument(
        rootDeclaration: TypeDeclaration,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        onOptionalField: ((ObjectNode, String) -> Unit)?,
    ): JsonNode {
        val rootTypeReference = rootDeclaration.aliasedTypeReference ?: TypeReference.Named(rootDeclaration.name)
        return nodeGenerator.generateNodeWithOptionalFields(rootTypeReference, declarationsByName, options, onOptionalField = onOptionalField)
    }

    private fun selectRootDeclaration(
        declarations: List<TypeDeclaration>,
        declarationsByName: Map<String, TypeDeclaration>,
        rootTypeName: String?,
    ): TypeDeclaration {
        rootTypeName?.let(declarationsByName::get)?.let { return it }

        declarations.firstOrNull(::isContainerAliasDeclaration)?.let { return it }
        selectUniqueUnreferencedDeclaration(declarations)?.let { return it }
        return declarations.firstOrNull() ?: error("No type declarations found.")
    }

    private fun selectUniqueUnreferencedDeclaration(declarations: List<TypeDeclaration>): TypeDeclaration? {
        val referencedTypeNames = declarations.flatMap(::collectReferencedTypeNames).toSet()
        val rootCandidates = declarations.filter { it.name !in referencedTypeNames }
        return rootCandidates.singleOrNull()
    }

    private fun collectReferencedTypeNames(declaration: TypeDeclaration): List<String> {
        return declaration.superTypeNames +
            declaration.fields.flatMap { collectReferencedTypeNames(it.typeReference) } +
            declaration.aliasedTypeReference?.let(::collectReferencedTypeNames).orEmpty()
    }

    private fun collectReferencedTypeNames(typeReference: TypeReference): List<String> {
        return when (typeReference) {
            TypeReference.AnyValue -> emptyList()
            is TypeReference.InlineObject -> typeReference.fields.flatMap { collectReferencedTypeNames(it.typeReference) }
            is TypeReference.ListReference -> collectReferencedTypeNames(typeReference.elementType)
            is TypeReference.MapReference -> {
                collectReferencedTypeNames(typeReference.keyType) + collectReferencedTypeNames(typeReference.valueType)
            }
            is TypeReference.Named -> listOf(typeReference.name)
            is TypeReference.Nullable -> collectReferencedTypeNames(typeReference.wrappedType)
            is TypeReference.Primitive -> emptyList()
            is TypeReference.Union -> typeReference.members.flatMap(::collectReferencedTypeNames)
        }
    }

    private fun isContainerAliasDeclaration(declaration: TypeDeclaration): Boolean {
        if (declaration.declarationKind != TypeDeclarationKind.TYPE_ALIAS) {
            return false
        }

        return when (declaration.aliasedTypeReference) {
            is TypeReference.ListReference, is TypeReference.MapReference -> true
            else -> false
        }
    }
}
