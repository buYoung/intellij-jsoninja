package com.livteam.jsoninja.services.typeConversion

import com.livteam.jsoninja.model.typeConversion.TypeField
import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import com.livteam.jsoninja.model.typeConversion.TypeReference

object JsonToTypeSupport {
    fun mergeTypeReferences(
        left: TypeReference,
        right: TypeReference,
        allowsNullableFields: Boolean,
        usesExperimentalGoUnionTypes: Boolean,
    ): TypeReference {
        if (left == right) {
            return left
        }

        if (left.isNullPlaceholder()) {
            return if (allowsNullableFields) {
                TypeReference.Nullable(right.unwrapNullable())
            } else {
                right
            }
        }
        if (right.isNullPlaceholder()) {
            return if (allowsNullableFields) {
                TypeReference.Nullable(left.unwrapNullable())
            } else {
                left
            }
        }

        if (left is TypeReference.Nullable && right is TypeReference.Nullable) {
            return TypeReference.Nullable(
                mergeTypeReferences(
                    left.wrappedType,
                    right.wrappedType,
                    allowsNullableFields = allowsNullableFields,
                    usesExperimentalGoUnionTypes = usesExperimentalGoUnionTypes,
                ),
            )
        }

        if (left is TypeReference.Nullable) {
            return TypeReference.Nullable(
                mergeTypeReferences(
                    left.wrappedType,
                    right,
                    allowsNullableFields = allowsNullableFields,
                    usesExperimentalGoUnionTypes = usesExperimentalGoUnionTypes,
                ),
            )
        }
        if (right is TypeReference.Nullable) {
            return TypeReference.Nullable(
                mergeTypeReferences(
                    left,
                    right.wrappedType,
                    allowsNullableFields = allowsNullableFields,
                    usesExperimentalGoUnionTypes = usesExperimentalGoUnionTypes,
                ),
            )
        }

        if (left is TypeReference.Primitive && right is TypeReference.Primitive) {
            return TypeReference.Primitive(mergePrimitiveKinds(left.primitiveKind, right.primitiveKind))
        }
        if (left is TypeReference.ListReference && right is TypeReference.ListReference) {
            return TypeReference.ListReference(
                mergeTypeReferences(
                    left.elementType,
                    right.elementType,
                    allowsNullableFields = allowsNullableFields,
                    usesExperimentalGoUnionTypes = usesExperimentalGoUnionTypes,
                ),
            )
        }
        if (left is TypeReference.MapReference && right is TypeReference.MapReference) {
            return TypeReference.MapReference(
                keyType = mergeTypeReferences(
                    left.keyType,
                    right.keyType,
                    allowsNullableFields = allowsNullableFields,
                    usesExperimentalGoUnionTypes = usesExperimentalGoUnionTypes,
                ),
                valueType = mergeTypeReferences(
                    left.valueType,
                    right.valueType,
                    allowsNullableFields = allowsNullableFields,
                    usesExperimentalGoUnionTypes = usesExperimentalGoUnionTypes,
                ),
            )
        }
        if (left is TypeReference.Named && right is TypeReference.Named && left.name == right.name) {
            return left
        }
        if (left is TypeReference.InlineObject && right is TypeReference.InlineObject) {
            return TypeReference.InlineObject(mergeFields(left.fields, right.fields, allowsNullableFields))
        }

        val unionMembers = linkedMapOf<String, TypeReference>()
        flattenUnion(left).forEach { unionMembers[buildTypeSignature(it)] = it }
        flattenUnion(right).forEach { unionMembers[buildTypeSignature(it)] = it }

        if (unionMembers.size == 1) {
            return unionMembers.values.first()
        }
        if (!usesExperimentalGoUnionTypes && unionMembers.values.all { it is TypeReference.Primitive }) {
            return TypeReference.AnyValue
        }
        return TypeReference.Union(unionMembers.values.toList())
    }

    fun buildTypeSignature(typeReference: TypeReference): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "any"
            is TypeReference.InlineObject -> {
                typeReference.fields.joinToString(prefix = "{", postfix = "}") {
                    "${it.name}:${buildTypeSignature(it.typeReference)}:${it.isOptional}"
                }
            }
            is TypeReference.ListReference -> "list:${buildTypeSignature(typeReference.elementType)}"
            is TypeReference.MapReference -> {
                "map:${buildTypeSignature(typeReference.keyType)}:${buildTypeSignature(typeReference.valueType)}"
            }
            is TypeReference.Named -> "named:${typeReference.name}"
            is TypeReference.Nullable -> "nullable:${buildTypeSignature(typeReference.wrappedType)}"
            is TypeReference.Primitive -> "primitive:${typeReference.primitiveKind.name}"
            is TypeReference.Union -> typeReference.members.joinToString(prefix = "union(", postfix = ")") {
                buildTypeSignature(it)
            }
        }
    }

    fun nullPlaceholder(): TypeReference {
        return TypeReference.Nullable(TypeReference.AnyValue)
    }

    private fun flattenUnion(typeReference: TypeReference): List<TypeReference> {
        return if (typeReference is TypeReference.Union) {
            typeReference.members.flatMap(::flattenUnion)
        } else {
            listOf(typeReference)
        }
    }

    private fun mergePrimitiveKinds(
        left: TypePrimitiveKind,
        right: TypePrimitiveKind,
    ): TypePrimitiveKind {
        if (left == right) {
            return left
        }
        return when {
            setOf(left, right) == setOf(TypePrimitiveKind.INTEGER, TypePrimitiveKind.DECIMAL) -> TypePrimitiveKind.NUMBER
            setOf(left, right) == setOf(TypePrimitiveKind.INTEGER, TypePrimitiveKind.NUMBER) -> TypePrimitiveKind.NUMBER
            setOf(left, right) == setOf(TypePrimitiveKind.DECIMAL, TypePrimitiveKind.NUMBER) -> TypePrimitiveKind.NUMBER
            else -> TypePrimitiveKind.STRING
        }
    }

    private fun mergeFields(
        leftFields: List<TypeField>,
        rightFields: List<TypeField>,
        allowsNullableFields: Boolean,
    ): List<TypeField> {
        val mergedFields = linkedMapOf<String, TypeField>()
        (leftFields + rightFields).forEach { field ->
            val existingField = mergedFields[field.name]
            if (existingField == null) {
                mergedFields[field.name] = field
            } else {
                mergedFields[field.name] = existingField.copy(
                    typeReference = mergeTypeReferences(
                        existingField.typeReference,
                        field.typeReference,
                        allowsNullableFields = allowsNullableFields,
                        usesExperimentalGoUnionTypes = true,
                    ),
                    isOptional = existingField.isOptional || field.isOptional,
                )
            }
        }
        return mergedFields.values.toList()
    }

    private fun TypeReference.unwrapNullable(): TypeReference {
        return if (this is TypeReference.Nullable) wrappedType else this
    }

    private fun TypeReference.isNullPlaceholder(): Boolean {
        return this is TypeReference.Nullable && this.wrappedType == TypeReference.AnyValue
    }
}
