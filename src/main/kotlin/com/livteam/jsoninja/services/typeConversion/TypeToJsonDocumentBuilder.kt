package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
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
        val rootDeclaration = rootTypeName
            ?.let(declarationsByName::get)
            ?: declarations.firstOrNull()
            ?: error("No type declarations found.")

        if (options.outputCount <= 1) {
            return buildSingleDocument(rootDeclaration, declarationsByName, options)
        }

        val arrayNode = objectMapper.createArrayNode()
        repeat(options.outputCount.coerceIn(1, 100)) {
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
}
