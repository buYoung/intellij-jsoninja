package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeField

object TypeDeclarationFieldResolver {
    fun resolveFields(
        declaration: TypeDeclaration,
        declarationsByName: Map<String, TypeDeclaration>,
        visitedTypeNames: Set<String> = emptySet(),
    ): List<TypeField> {
        if (declaration.name in visitedTypeNames) {
            return emptyList()
        }

        val nextVisitedTypeNames = visitedTypeNames + declaration.name
        val resolvedFields = linkedMapOf<String, TypeField>()

        declaration.superTypeNames.forEach { superTypeName ->
            declarationsByName[superTypeName]?.let { superTypeDeclaration ->
                resolveFields(superTypeDeclaration, declarationsByName, nextVisitedTypeNames).forEach { field ->
                    resolvedFields[field.name] = field
                }
            }
        }

        declaration.fields.forEach { field ->
            resolvedFields[field.name] = field
        }
        return resolvedFields.values.toList()
    }
}
