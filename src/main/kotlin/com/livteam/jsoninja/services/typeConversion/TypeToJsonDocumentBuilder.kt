package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeReference

class TypeToJsonDocumentBuilder(
    private val objectMapper: ObjectMapper,
    private val nodeGenerator: TypeToJsonNodeGenerator = TypeToJsonNodeGenerator(objectMapper),
) {
    fun buildDocument(
        declarations: List<TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
        rootTypeName: String? = null,
    ): JsonNode {
        val declarationsByName = declarations.associateBy(TypeDeclaration::name)
        val rootDeclaration = selectRootDeclaration(
            declarations = declarations,
            declarationsByName = declarationsByName,
            rootTypeName = rootTypeName,
        )
        val outputCount = options.outputCount.coerceIn(1, 100)
        val rootDocument = buildSingleDocument(rootDeclaration, declarationsByName, options)

        if (outputCount <= 1) {
            return rootDocument
        }

        if (rootDocument is ArrayNode) {
            val mergedArrayNode = objectMapper.createArrayNode()
            mergedArrayNode.addAll(rootDocument)

            // Keep array roots flat by appending generated elements into the same array.
            repeat(outputCount - 1) {
                val generatedDocument = buildSingleDocument(rootDeclaration, declarationsByName, options)
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
            arrayNode.add(buildSingleDocument(rootDeclaration, declarationsByName, options))
        }
        return arrayNode
    }

    private fun buildSingleDocument(
        rootDeclaration: TypeDeclaration,
        declarationsByName: Map<String, TypeDeclaration>,
        options: TypeToJsonGenerationOptions,
    ): JsonNode {
        val rootTypeReference = rootDeclaration.aliasedTypeReference ?: TypeReference.Named(rootDeclaration.name)
        return nodeGenerator.generateNode(rootTypeReference, declarationsByName, options)
    }

    private fun selectRootDeclaration(
        declarations: List<TypeDeclaration>,
        declarationsByName: Map<String, TypeDeclaration>,
        rootTypeName: String?,
    ): TypeDeclaration {
        rootTypeName?.let(declarationsByName::get)?.let { return it }

        declarations.firstOrNull(::isContainerAliasDeclaration)?.let { return it }
        return declarations.firstOrNull() ?: error("No type declarations found.")
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
