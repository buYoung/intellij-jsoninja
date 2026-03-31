package com.livteam.jsoninja.services.typeConversion

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.livteam.jsoninja.model.NamingConvention
import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeField
import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import com.livteam.jsoninja.model.typeConversion.TypeReference
import com.livteam.jsoninja.services.JsonObjectMapperService

@Service(Service.Level.PROJECT)
class JsonToTypeConversionService {
    private val objectMapper = service<JsonObjectMapperService>().objectMapper

    fun generateTypeDeclaration(
        language: SupportedLanguage,
        sourceJson: String,
        options: JsonToTypeConversionOptions = JsonToTypeConversionOptions(),
    ): String {
        require(sourceJson.isNotBlank()) { "JSON input is empty." }

        val rootNode = objectMapper.readTree(sourceJson)
        val rootTypeName = sanitizeTypeName(
            candidateName = options.rootTypeName,
            language = language,
            fallbackName = "Root",
        )
        val inferenceContext = JsonToTypeInferenceContext(language, options)
        val rootTypeReference = inferenceContext.inferRootTypeReference(rootNode, rootTypeName)

        return JsonToTypeRenderer(
            language = language,
            options = options,
            typeDeclarationsByName = inferenceContext.typeDeclarationsByName,
            warningMessages = inferenceContext.warningMessages.toList(),
        ).render(rootTypeReference, rootTypeName)
    }
}

private class JsonToTypeInferenceContext(
    private val language: SupportedLanguage,
    private val options: JsonToTypeConversionOptions,
) {
    val typeDeclarationsByName = linkedMapOf<String, TypeDeclaration>()
    val warningMessages = linkedSetOf<String>()

    private val typeNamesBySignature = linkedMapOf<String, String>()
    private val usedTypeNames = linkedSetOf<String>()

    fun inferRootTypeReference(
        rootNode: JsonNode,
        rootTypeName: String,
    ): TypeReference {
        return when {
            rootNode.isObject -> {
                val fields = buildObjectFields(
                    objectNodes = listOf(rootNode as ObjectNode),
                    suggestedTypeName = rootTypeName,
                    path = "$",
                    depth = 0,
                )
                registerDeclaration(rootTypeName, fields)
                TypeReference.Named(rootTypeName)
            }

            else -> inferTypeReference(
                node = rootNode,
                suggestedTypeName = rootTypeName,
                path = "$",
                depth = 0,
            )
        }
    }

    private fun inferTypeReference(
        node: JsonNode,
        suggestedTypeName: String,
        path: String,
        depth: Int,
    ): TypeReference {
        if (depth > options.maximumDepth) {
            warningMessages += "Depth limit exceeded at $path. Falling back to Any."
            return TypeReference.AnyValue
        }

        return when {
            node.isNull -> {
                if (options.allowsNullableFields) {
                    TypeReference.Nullable(TypeReference.AnyValue)
                } else {
                    TypeReference.AnyValue
                }
            }

            node.isObject -> inferObjectTypeReference(
                objectNode = node as ObjectNode,
                suggestedTypeName = suggestedTypeName,
                path = path,
                depth = depth,
            )

            node.isArray -> inferArrayTypeReference(
                arrayNode = node as ArrayNode,
                suggestedTypeName = suggestedTypeName,
                path = path,
                depth = depth,
            )

            node.isBoolean -> TypeReference.Primitive(TypePrimitiveKind.BOOLEAN)
            node.isIntegralNumber -> TypeReference.Primitive(TypePrimitiveKind.INTEGER)
            node.isFloatingPointNumber -> TypeReference.Primitive(TypePrimitiveKind.DECIMAL)
            node.isNumber -> TypeReference.Primitive(TypePrimitiveKind.NUMBER)
            node.isTextual -> {
                if (looksLikeDateString(node.asText())) {
                    warningMessages += "Date-like string detected at $path. The generated type remains String."
                }
                TypeReference.Primitive(TypePrimitiveKind.STRING)
            }

            else -> TypeReference.AnyValue
        }
    }

    private fun inferObjectTypeReference(
        objectNode: ObjectNode,
        suggestedTypeName: String,
        path: String,
        depth: Int,
    ): TypeReference {
        val fields = buildObjectFields(
            objectNodes = listOf(objectNode),
            suggestedTypeName = suggestedTypeName,
            path = path,
            depth = depth,
        )

        return registerOrReuseDeclaration(
            fields = fields,
            suggestedTypeName = suggestedTypeName,
        )
    }

    private fun inferArrayTypeReference(
        arrayNode: ArrayNode,
        suggestedTypeName: String,
        path: String,
        depth: Int,
    ): TypeReference {
        if (arrayNode.isEmpty) {
            warningMessages += "Empty array detected at $path. Falling back to Any."
            return TypeReference.ListReference(TypeReference.AnyValue)
        }

        val nonNullElements = mutableListOf<JsonNode>()
        var containsNullElement = false

        arrayNode.forEach { elementNode ->
            if (elementNode.isNull) {
                containsNullElement = true
            } else {
                nonNullElements.add(elementNode)
            }
        }

        if (nonNullElements.isEmpty()) {
            warningMessages += "Array contains only null values at $path. Falling back to Any."
            return TypeReference.ListReference(TypeReference.AnyValue)
        }

        val elementType = if (nonNullElements.all { it.isObject }) {
            inferMergedObjectArrayTypeReference(
                objectNodes = nonNullElements.map { it as ObjectNode },
                suggestedTypeName = buildSuggestedTypeName(suggestedTypeName, "item"),
                path = "$path[]",
                depth = depth,
            )
        } else {
            nonNullElements
                .map { elementNode ->
                    inferTypeReference(
                        node = elementNode,
                        suggestedTypeName = buildSuggestedTypeName(suggestedTypeName, "item"),
                        path = "$path[]",
                        depth = depth + 1,
                    )
                }
                .reduce { currentTypeReference, nextTypeReference ->
                    mergeTypeReferences(
                        leftTypeReference = currentTypeReference,
                        rightTypeReference = nextTypeReference,
                        path = path,
                    )
                }
        }

        val resolvedElementType = if (containsNullElement && options.allowsNullableFields) {
            makeNullable(elementType)
        } else {
            elementType
        }

        return TypeReference.ListReference(resolvedElementType)
    }

    private fun inferMergedObjectArrayTypeReference(
        objectNodes: List<ObjectNode>,
        suggestedTypeName: String,
        path: String,
        depth: Int,
    ): TypeReference {
        val fields = buildObjectFields(
            objectNodes = objectNodes,
            suggestedTypeName = suggestedTypeName,
            path = path,
            depth = depth,
        )

        return registerOrReuseDeclaration(
            fields = fields,
            suggestedTypeName = suggestedTypeName,
        )
    }

    private fun buildObjectFields(
        objectNodes: List<ObjectNode>,
        suggestedTypeName: String,
        path: String,
        depth: Int,
    ): List<TypeField> {
        val sourceFieldNames = linkedSetOf<String>()
        objectNodes.forEach { objectNode ->
            val fieldNameIterator = objectNode.fieldNames()
            while (fieldNameIterator.hasNext()) {
                sourceFieldNames += fieldNameIterator.next()
            }
        }

        if (sourceFieldNames.isEmpty()) {
            warningMessages += "Empty object detected at $path."
        }

        val usedFieldNames = linkedSetOf<String>()
        return sourceFieldNames.map { sourceFieldName ->
            var isOptional = false
            var containsExplicitNull = false
            var resolvedTypeReference: TypeReference? = null

            objectNodes.forEach { objectNode ->
                if (!objectNode.has(sourceFieldName)) {
                    isOptional = true
                    return@forEach
                }

                val fieldNode = objectNode.get(sourceFieldName)
                if (fieldNode == null || fieldNode.isNull) {
                    containsExplicitNull = true
                    return@forEach
                }

                val fieldTypeReference = inferTypeReference(
                    node = fieldNode,
                    suggestedTypeName = buildSuggestedTypeName(suggestedTypeName, sourceFieldName),
                    path = buildChildPath(path, sourceFieldName),
                    depth = depth + 1,
                )
                val currentTypeReference = resolvedTypeReference
                resolvedTypeReference = if (currentTypeReference == null) {
                    fieldTypeReference
                } else {
                    mergeTypeReferences(
                        leftTypeReference = currentTypeReference,
                        rightTypeReference = fieldTypeReference,
                        path = buildChildPath(path, sourceFieldName),
                    )
                }
            }

            val resolvedNonNullTypeReference = resolvedTypeReference
            val finalizedTypeReference = when {
                resolvedNonNullTypeReference == null && options.allowsNullableFields -> TypeReference.Nullable(TypeReference.AnyValue)
                resolvedNonNullTypeReference == null -> TypeReference.AnyValue
                (containsExplicitNull || isOptional) && options.allowsNullableFields -> makeNullable(resolvedNonNullTypeReference)
                else -> resolvedNonNullTypeReference
            }

            TypeField(
                name = ensureUniqueFieldName(
                    candidateName = sanitizeFieldName(sourceFieldName, options.namingConvention, language),
                    usedFieldNames = usedFieldNames,
                ),
                typeReference = finalizedTypeReference,
                isOptional = isOptional,
                sourceName = sourceFieldName,
            )
        }
    }

    private fun registerOrReuseDeclaration(
        fields: List<TypeField>,
        suggestedTypeName: String,
    ): TypeReference {
        val declarationSignature = createDeclarationSignature(fields)
        val existingTypeName = typeNamesBySignature[declarationSignature]
        if (existingTypeName != null) {
            return TypeReference.Named(existingTypeName)
        }

        val typeName = createUniqueTypeName(suggestedTypeName)
        typeNamesBySignature[declarationSignature] = typeName
        registerDeclaration(typeName, fields)
        return TypeReference.Named(typeName)
    }

    private fun registerDeclaration(
        typeName: String,
        fields: List<TypeField>,
    ) {
        usedTypeNames += typeName
        typeDeclarationsByName[typeName] = TypeDeclaration(
            name = typeName,
            declarationKind = when (language) {
                SupportedLanguage.JAVA,
                SupportedLanguage.KOTLIN -> TypeDeclarationKind.CLASS
                SupportedLanguage.TYPESCRIPT -> TypeDeclarationKind.INTERFACE
                SupportedLanguage.GO -> TypeDeclarationKind.STRUCT
            },
            fields = fields,
        )
    }

    private fun createUniqueTypeName(suggestedTypeName: String): String {
        val baseName = sanitizeTypeName(
            candidateName = suggestedTypeName,
            language = language,
            fallbackName = "GeneratedType",
        )
        if (usedTypeNames.add(baseName)) {
            return baseName
        }

        var suffixNumber = 2
        while (true) {
            val candidateName = "$baseName$suffixNumber"
            if (usedTypeNames.add(candidateName)) {
                return candidateName
            }
            suffixNumber += 1
        }
    }

    private fun createDeclarationSignature(fields: List<TypeField>): String {
        return fields
            .sortedBy { it.sourceName }
            .joinToString(separator = "|") { field ->
                "${field.sourceName}:${field.isOptional}:${createTypeSignature(field.typeReference)}"
            }
    }

    private fun createTypeSignature(typeReference: TypeReference): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "any"
            is TypeReference.InlineObject -> "inline{${createDeclarationSignature(typeReference.fields)}}"
            is TypeReference.ListReference -> "list<${createTypeSignature(typeReference.elementType)}>"
            is TypeReference.MapReference -> "map<${createTypeSignature(typeReference.keyType)},${createTypeSignature(typeReference.valueType)}>"
            is TypeReference.Named -> "named:${typeReference.name}"
            is TypeReference.Nullable -> "nullable<${createTypeSignature(typeReference.wrappedType)}>"
            is TypeReference.Primitive -> "primitive:${typeReference.primitiveKind.name}"
        }
    }

    private fun mergeTypeReferences(
        leftTypeReference: TypeReference,
        rightTypeReference: TypeReference,
        path: String,
    ): TypeReference {
        if (leftTypeReference == rightTypeReference) {
            return leftTypeReference
        }

        if (leftTypeReference is TypeReference.Nullable || rightTypeReference is TypeReference.Nullable) {
            val mergedWrappedType = mergeTypeReferences(
                leftTypeReference = unwrapNullable(leftTypeReference),
                rightTypeReference = unwrapNullable(rightTypeReference),
                path = path,
            )
            return if (options.allowsNullableFields) {
                makeNullable(mergedWrappedType)
            } else {
                mergedWrappedType
            }
        }

        if (leftTypeReference is TypeReference.Primitive && rightTypeReference is TypeReference.Primitive) {
            return mergePrimitiveTypeReferences(leftTypeReference, rightTypeReference, path)
        }

        if (leftTypeReference is TypeReference.ListReference && rightTypeReference is TypeReference.ListReference) {
            return TypeReference.ListReference(
                mergeTypeReferences(
                    leftTypeReference = leftTypeReference.elementType,
                    rightTypeReference = rightTypeReference.elementType,
                    path = "$path[]",
                )
            )
        }

        if (leftTypeReference is TypeReference.Named && rightTypeReference is TypeReference.Named) {
            if (leftTypeReference.name == rightTypeReference.name) {
                return leftTypeReference
            }
        }

        warningMessages += "Mixed types detected at $path. Falling back to Any."
        return TypeReference.AnyValue
    }

    private fun mergePrimitiveTypeReferences(
        leftTypeReference: TypeReference.Primitive,
        rightTypeReference: TypeReference.Primitive,
        path: String,
    ): TypeReference {
        val leftPrimitiveKind = leftTypeReference.primitiveKind
        val rightPrimitiveKind = rightTypeReference.primitiveKind
        if (leftPrimitiveKind == rightPrimitiveKind) {
            return leftTypeReference
        }

        if (leftPrimitiveKind.isNumeric() && rightPrimitiveKind.isNumeric()) {
            return if (
                leftPrimitiveKind == TypePrimitiveKind.DECIMAL ||
                rightPrimitiveKind == TypePrimitiveKind.DECIMAL
            ) {
                TypeReference.Primitive(TypePrimitiveKind.DECIMAL)
            } else {
                TypeReference.Primitive(TypePrimitiveKind.NUMBER)
            }
        }

        warningMessages += "Mixed primitive types detected at $path. Falling back to Any."
        return TypeReference.AnyValue
    }
}

private class JsonToTypeRenderer(
    private val language: SupportedLanguage,
    private val options: JsonToTypeConversionOptions,
    private val typeDeclarationsByName: Map<String, TypeDeclaration>,
    private val warningMessages: List<String>,
) {
    fun render(
        rootTypeReference: TypeReference,
        rootTypeName: String,
    ): String {
        val headerText = renderWarningHeader()
        val importText = renderImportBlock(rootTypeReference)
        val declarationText = renderDeclarations(rootTypeReference, rootTypeName)

        return listOf(headerText, importText, declarationText)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
    }

    private fun renderWarningHeader(): String {
        if (warningMessages.isEmpty()) {
            return ""
        }

        return warningMessages.joinToString(separator = "\n") { warningMessage ->
            "// $warningMessage"
        }
    }

    private fun renderImportBlock(rootTypeReference: TypeReference): String {
        val importStatements = when (language) {
            SupportedLanguage.JAVA -> buildJavaImportStatements(rootTypeReference)
            SupportedLanguage.KOTLIN -> buildKotlinImportStatements()
            SupportedLanguage.TYPESCRIPT,
            SupportedLanguage.GO -> emptyList()
        }

        return importStatements.joinToString(separator = "\n")
    }

    private fun buildJavaImportStatements(
        rootTypeReference: TypeReference,
    ): List<String> {
        val importStatements = linkedSetOf<String>()
        val containsListReference = usesListReference(rootTypeReference) ||
            typeDeclarationsByName.values.any { declaration ->
                declaration.fields.any { field -> usesListReference(field.typeReference) }
            }

        if (containsListReference) {
            importStatements += "import java.util.List;"
        }

        if (rootTypeReference is TypeReference.ListReference) {
            importStatements += "import java.util.ArrayList;"
        }

        if (options.allowsNullableFields && typeDeclarationsByName.values.any { declaration ->
                declaration.fields.any { field -> field.isOptional || field.typeReference is TypeReference.Nullable }
            }
        ) {
            importStatements += "import org.jetbrains.annotations.Nullable;"
        }

        val requiresNameAnnotation = typeDeclarationsByName.values.any { declaration ->
            declaration.fields.any(::usesNameAnnotation)
        }
        if (requiresNameAnnotation) {
            when (options.annotationStyle) {
                JsonToTypeAnnotationStyle.GSON_SERIALIZED_NAME -> {
                    importStatements += "import com.google.gson.annotations.SerializedName;"
                }

                JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> {
                    importStatements += "import com.fasterxml.jackson.annotation.JsonProperty;"
                }

                else -> Unit
            }
        }

        return importStatements.toList()
    }

    private fun buildKotlinImportStatements(): List<String> {
        val importStatements = linkedSetOf<String>()
        if (typeDeclarationsByName.values.any { declaration ->
                declaration.fields.any(::usesNameAnnotation)
            }
        ) {
            when (options.annotationStyle) {
                JsonToTypeAnnotationStyle.KOTLIN_SERIAL_NAME -> {
                    importStatements += "import kotlinx.serialization.SerialName"
                }

                JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> {
                    importStatements += "import com.fasterxml.jackson.annotation.JsonProperty"
                }

                else -> Unit
            }
        }
        return importStatements.toList()
    }

    private fun renderDeclarations(
        rootTypeReference: TypeReference,
        rootTypeName: String,
    ): String {
        val additionalDeclarations = typeDeclarationsByName.values
            .filterNot { declaration -> declaration.name == rootTypeName }
            .map { declaration -> renderObjectDeclaration(declaration) }

        val rootDeclarationText = if (rootTypeReference is TypeReference.Named && typeDeclarationsByName.containsKey(rootTypeReference.name)) {
            renderObjectDeclaration(typeDeclarationsByName.getValue(rootTypeReference.name))
        } else {
            renderRootAlias(rootTypeReference, rootTypeName)
        }

        return listOf(rootDeclarationText)
            .plus(additionalDeclarations)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
    }

    private fun renderObjectDeclaration(declaration: TypeDeclaration): String {
        return when (language) {
            SupportedLanguage.JAVA -> renderJavaDeclaration(declaration)
            SupportedLanguage.KOTLIN -> renderKotlinDeclaration(declaration)
            SupportedLanguage.TYPESCRIPT -> renderTypeScriptDeclaration(declaration)
            SupportedLanguage.GO -> renderGoDeclaration(declaration)
        }
    }

    private fun renderRootAlias(
        rootTypeReference: TypeReference,
        rootTypeName: String,
    ): String {
        return when (language) {
            SupportedLanguage.JAVA -> {
                if (rootTypeReference is TypeReference.ListReference) {
                    "public class $rootTypeName extends ArrayList<${renderJavaType(rootTypeReference.elementType, true)}> {\n}"
                } else {
                    "public class $rootTypeName {\n    public ${renderJavaType(rootTypeReference)} value;\n}"
                }
            }

            SupportedLanguage.KOTLIN -> "typealias $rootTypeName = ${renderKotlinType(rootTypeReference)}"
            SupportedLanguage.TYPESCRIPT -> "export type $rootTypeName = ${renderTypeScriptType(rootTypeReference)}"
            SupportedLanguage.GO -> "type $rootTypeName ${renderGoType(rootTypeReference)}"
        }
    }

    private fun renderJavaDeclaration(declaration: TypeDeclaration): String {
        val fieldsText = declaration.fields.joinToString(separator = "\n\n") { field ->
            buildString {
                renderJavaFieldAnnotation(field)?.let { annotationText ->
                    append("    ")
                    append(annotationText)
                    append('\n')
                }
                if (options.allowsNullableFields && (field.isOptional || field.typeReference is TypeReference.Nullable)) {
                    append("    @Nullable\n")
                }
                append("    public ")
                append(renderJavaType(field.typeReference))
                append(' ')
                append(field.name)
                append(';')
            }
        }

        return if (fieldsText.isBlank()) {
            "public class ${declaration.name} {\n}"
        } else {
            "public class ${declaration.name} {\n$fieldsText\n}"
        }
    }

    private fun renderJavaFieldAnnotation(field: TypeField): String? {
        return when (options.annotationStyle) {
            JsonToTypeAnnotationStyle.GSON_SERIALIZED_NAME -> {
                if (usesNameAnnotation(field)) "@SerializedName(\"${field.sourceName}\")" else null
            }

            JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> {
                if (usesNameAnnotation(field)) "@JsonProperty(\"${field.sourceName}\")" else null
            }

            else -> null
        }
    }

    private fun renderKotlinDeclaration(declaration: TypeDeclaration): String {
        val propertiesText = declaration.fields.joinToString(separator = ",\n") { field ->
            buildString {
                renderKotlinFieldAnnotation(field)?.let { annotationText ->
                    append("    ")
                    append(annotationText)
                    append('\n')
                }
                append("    val ")
                append(field.name)
                append(": ")
                append(renderKotlinType(field.typeReference))
            }
        }

        return if (propertiesText.isBlank()) {
            "data class ${declaration.name}()"
        } else {
            "data class ${declaration.name}(\n$propertiesText,\n)"
        }
    }

    private fun renderKotlinFieldAnnotation(field: TypeField): String? {
        return when (options.annotationStyle) {
            JsonToTypeAnnotationStyle.KOTLIN_SERIAL_NAME -> {
                if (usesNameAnnotation(field)) "@SerialName(\"${field.sourceName}\")" else null
            }

            JsonToTypeAnnotationStyle.JACKSON_JSON_PROPERTY -> {
                if (usesNameAnnotation(field)) "@JsonProperty(\"${field.sourceName}\")" else null
            }

            else -> null
        }
    }

    private fun renderTypeScriptDeclaration(declaration: TypeDeclaration): String {
        val fieldsText = declaration.fields.joinToString(separator = "\n") { field ->
            val optionalSuffix = if (field.isOptional) "?" else ""
            "  ${field.name}$optionalSuffix: ${renderTypeScriptType(field.typeReference)};"
        }

        return if (fieldsText.isBlank()) {
            "export interface ${declaration.name} {}"
        } else {
            "export interface ${declaration.name} {\n$fieldsText\n}"
        }
    }

    private fun renderGoDeclaration(declaration: TypeDeclaration): String {
        val fieldsText = declaration.fields.joinToString(separator = "\n") { field ->
            val structTagText = if (options.annotationStyle == JsonToTypeAnnotationStyle.GO_JSON_TAG) {
                " `json:\"${field.sourceName}${if (field.isOptional) ",omitempty" else ""}\"`"
            } else {
                ""
            }

            "    ${field.name} ${renderGoType(field.typeReference)}$structTagText"
        }

        return if (fieldsText.isBlank()) {
            "type ${declaration.name} struct{}"
        } else {
            "type ${declaration.name} struct {\n$fieldsText\n}"
        }
    }

    private fun renderJavaType(
        typeReference: TypeReference,
        usesBoxedPrimitive: Boolean = false,
    ): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "Object"
            is TypeReference.InlineObject -> "Object"
            is TypeReference.ListReference -> "List<${renderJavaType(typeReference.elementType, true)}>"
            is TypeReference.MapReference -> "Object"
            is TypeReference.Named -> typeReference.name
            is TypeReference.Nullable -> renderJavaType(typeReference.wrappedType, true)
            is TypeReference.Primitive -> when (typeReference.primitiveKind) {
                TypePrimitiveKind.STRING -> "String"
                TypePrimitiveKind.INTEGER -> if (usesBoxedPrimitive) "Integer" else "int"
                TypePrimitiveKind.DECIMAL,
                TypePrimitiveKind.NUMBER -> if (usesBoxedPrimitive) "Double" else "double"
                TypePrimitiveKind.BOOLEAN -> if (usesBoxedPrimitive) "Boolean" else "boolean"
            }
        }
    }

    private fun renderKotlinType(typeReference: TypeReference): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "Any"
            is TypeReference.InlineObject -> "Any"
            is TypeReference.ListReference -> "List<${renderKotlinType(typeReference.elementType)}>"
            is TypeReference.MapReference -> "Map<${renderKotlinType(typeReference.keyType)}, ${renderKotlinType(typeReference.valueType)}>"
            is TypeReference.Named -> typeReference.name
            is TypeReference.Nullable -> "${renderKotlinType(typeReference.wrappedType)}?"
            is TypeReference.Primitive -> when (typeReference.primitiveKind) {
                TypePrimitiveKind.STRING -> "String"
                TypePrimitiveKind.INTEGER -> "Int"
                TypePrimitiveKind.DECIMAL,
                TypePrimitiveKind.NUMBER -> "Double"
                TypePrimitiveKind.BOOLEAN -> "Boolean"
            }
        }
    }

    private fun renderTypeScriptType(typeReference: TypeReference): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "any"
            is TypeReference.InlineObject -> "Record<string, any>"
            is TypeReference.ListReference -> {
                val renderedElementType = renderTypeScriptType(typeReference.elementType)
                if (renderedElementType.contains('|')) {
                    "Array<$renderedElementType>"
                } else {
                    "$renderedElementType[]"
                }
            }

            is TypeReference.MapReference -> "Record<string, ${renderTypeScriptType(typeReference.valueType)}>"
            is TypeReference.Named -> typeReference.name
            is TypeReference.Nullable -> "${renderTypeScriptType(typeReference.wrappedType)} | null"
            is TypeReference.Primitive -> when (typeReference.primitiveKind) {
                TypePrimitiveKind.STRING -> "string"
                TypePrimitiveKind.INTEGER,
                TypePrimitiveKind.DECIMAL,
                TypePrimitiveKind.NUMBER -> "number"
                TypePrimitiveKind.BOOLEAN -> "boolean"
            }
        }
    }

    private fun renderGoType(typeReference: TypeReference): String {
        return when (typeReference) {
            TypeReference.AnyValue -> "any"
            is TypeReference.InlineObject -> "any"
            is TypeReference.ListReference -> "[]${renderGoType(typeReference.elementType)}"
            is TypeReference.MapReference -> "map[string]${renderGoType(typeReference.valueType)}"
            is TypeReference.Named -> typeReference.name
            is TypeReference.Nullable -> renderNullableGoType(typeReference.wrappedType)
            is TypeReference.Primitive -> when (typeReference.primitiveKind) {
                TypePrimitiveKind.STRING -> "string"
                TypePrimitiveKind.INTEGER -> "int"
                TypePrimitiveKind.DECIMAL,
                TypePrimitiveKind.NUMBER -> "float64"
                TypePrimitiveKind.BOOLEAN -> "bool"
            }
        }
    }

    private fun renderNullableGoType(typeReference: TypeReference): String {
        val renderedType = renderGoType(typeReference)
        return when {
            renderedType.startsWith("*") -> renderedType
            renderedType.startsWith("[]") -> renderedType
            renderedType.startsWith("map[") -> renderedType
            renderedType == "any" -> renderedType
            else -> "*${renderGoType(typeReference)}"
        }
    }

    private fun usesListReference(typeReference: TypeReference): Boolean {
        return when (typeReference) {
            is TypeReference.ListReference -> true
            is TypeReference.MapReference -> usesListReference(typeReference.keyType) || usesListReference(typeReference.valueType)
            is TypeReference.Nullable -> usesListReference(typeReference.wrappedType)
            else -> false
        }
    }

    private fun usesNameAnnotation(field: TypeField): Boolean {
        return when (options.annotationStyle) {
            JsonToTypeAnnotationStyle.NONE -> false
            JsonToTypeAnnotationStyle.GO_JSON_TAG -> true
            else -> field.name != field.sourceName
        }
    }
}

private fun makeNullable(typeReference: TypeReference): TypeReference {
    return if (typeReference is TypeReference.Nullable) {
        typeReference
    } else {
        TypeReference.Nullable(typeReference)
    }
}

private fun unwrapNullable(typeReference: TypeReference): TypeReference {
    return if (typeReference is TypeReference.Nullable) {
        typeReference.wrappedType
    } else {
        typeReference
    }
}

private fun TypePrimitiveKind.isNumeric(): Boolean {
    return this == TypePrimitiveKind.INTEGER ||
        this == TypePrimitiveKind.DECIMAL ||
        this == TypePrimitiveKind.NUMBER
}

private fun buildSuggestedTypeName(
    parentTypeName: String,
    sourceFieldName: String,
): String {
    return parentTypeName + toPascalCase(singularizeName(sourceFieldName))
}

private fun buildChildPath(
    currentPath: String,
    sourceFieldName: String,
): String {
    return if (currentPath == "$") {
        "$.$sourceFieldName"
    } else {
        "$currentPath.$sourceFieldName"
    }
}

private fun sanitizeTypeName(
    candidateName: String,
    language: SupportedLanguage,
    fallbackName: String,
): String {
    val normalizedName = candidateName
        .trim()
        .replace(Regex("[^A-Za-z0-9_]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(separator = "") { token -> token.replaceFirstChar(Char::uppercaseChar) }
        .ifBlank { fallbackName }

    val identifierText = normalizedName.replace(Regex("[^A-Za-z0-9_]"), "")
    val prefixedIdentifierText = if (identifierText.firstOrNull()?.isDigit() == true) {
        "${fallbackName.replaceFirstChar(Char::uppercaseChar)}$identifierText"
    } else {
        identifierText
    }
    return avoidReservedWord(prefixedIdentifierText, language, fallbackName.replaceFirstChar(Char::uppercaseChar))
}

private fun sanitizeFieldName(
    sourceFieldName: String,
    namingConvention: NamingConvention,
    language: SupportedLanguage,
): String {
    val words = splitWords(sourceFieldName)
    val transformedName = when (namingConvention) {
        NamingConvention.CAMEL_CASE -> toCamelCase(words)
        NamingConvention.PASCAL_CASE -> toPascalCase(words)
        NamingConvention.SNAKE_CASE -> toSnakeCase(words)
    }.ifBlank { "value" }

    val identifierText = transformedName.replace(Regex("[^A-Za-z0-9_]"), "")
    val prefixedIdentifierText = if (identifierText.firstOrNull()?.isDigit() == true) {
        "value$identifierText"
    } else {
        identifierText
    }
    return avoidReservedWord(prefixedIdentifierText, language, "value")
}

private fun ensureUniqueFieldName(
    candidateName: String,
    usedFieldNames: MutableSet<String>,
): String {
    if (usedFieldNames.add(candidateName)) {
        return candidateName
    }

    var suffixNumber = 2
    while (true) {
        val resolvedName = "$candidateName$suffixNumber"
        if (usedFieldNames.add(resolvedName)) {
            return resolvedName
        }
        suffixNumber += 1
    }
}

private fun singularizeName(value: String): String {
    return if (value.endsWith("s", ignoreCase = true) && value.length > 1) {
        value.dropLast(1)
    } else {
        value
    }
}

private fun splitWords(value: String): List<String> {
    return value
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace(Regex("[^A-Za-z0-9]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .map { token -> token.lowercase() }
}

private fun toCamelCase(words: List<String>): String {
    if (words.isEmpty()) {
        return ""
    }

    return words.first() + words.drop(1).joinToString(separator = "") { word ->
        word.replaceFirstChar(Char::uppercaseChar)
    }
}

private fun toPascalCase(words: List<String>): String {
    return words.joinToString(separator = "") { word ->
        word.replaceFirstChar(Char::uppercaseChar)
    }
}

private fun toPascalCase(value: String): String {
    return toPascalCase(splitWords(value))
}

private fun toSnakeCase(words: List<String>): String {
    return words.joinToString(separator = "_")
}

private fun avoidReservedWord(
    identifierText: String,
    language: SupportedLanguage,
    fallbackName: String,
): String {
    val normalizedIdentifierText = if (identifierText.isBlank()) {
        fallbackName
    } else {
        identifierText
    }

    val reservedWords = when (language) {
        SupportedLanguage.JAVA -> setOf("class", "interface", "enum", "package", "public", "private")
        SupportedLanguage.KOTLIN -> setOf("class", "object", "interface", "typealias", "when", "val", "var")
        SupportedLanguage.TYPESCRIPT -> setOf("class", "interface", "type", "enum", "extends", "readonly")
        SupportedLanguage.GO -> setOf("type", "struct", "interface", "func", "map", "package")
    }

    return if (normalizedIdentifierText.lowercase() in reservedWords) {
        "${normalizedIdentifierText}Value"
    } else {
        normalizedIdentifierText
    }
}

private fun looksLikeDateString(value: String): Boolean {
    return value.matches(Regex("""\d{4}-\d{2}-\d{2}([Tt ][0-9:.+-Zz]*)?"""))
}
