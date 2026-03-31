package com.livteam.jsoninja.services.typeConversion

import com.intellij.openapi.components.Service
import com.livteam.jsoninja.model.typeConversion.TypeAnalysisResult
import com.livteam.jsoninja.model.typeConversion.TypeConversionLanguage
import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.model.typeConversion.TypeField
import com.livteam.jsoninja.model.typeConversion.TypePrimitiveKind
import com.livteam.jsoninja.model.typeConversion.TypeReference

@Service(Service.Level.PROJECT)
class TypeDeclarationAnalyzerService(
    private val treeSitterAssetRegistryService: TreeSitterAssetRegistryService,
) {
    private val analyzersByLanguage: Map<TypeConversionLanguage, LanguageTypeDeclarationAnalyzer> = mapOf(
        TypeConversionLanguage.JAVA to JavaTypeDeclarationAnalyzer(),
        TypeConversionLanguage.KOTLIN to KotlinTypeDeclarationAnalyzer(),
        TypeConversionLanguage.TYPESCRIPT to TypeScriptTypeDeclarationAnalyzer(),
        TypeConversionLanguage.GO to GoTypeDeclarationAnalyzer(),
    )

    fun analyzeTypeDeclaration(
        language: TypeConversionLanguage,
        sourceCode: String,
        rootTypeName: String? = null,
    ): TypeAnalysisResult {
        require(sourceCode.isNotBlank()) { "Type declaration source is empty." }

        val asset = treeSitterAssetRegistryService.getAsset(language)
        val analyzer = analyzersByLanguage[language]
            ?: throw IllegalArgumentException("Unsupported type declaration language: $language")
        val typeDeclarations = analyzer.analyze(sourceCode)

        if (typeDeclarations.isEmpty()) {
            throw IllegalArgumentException("No supported ${language.displayName} type declaration found.")
        }

        val resolvedRootTypeName = when {
            rootTypeName == null -> typeDeclarations.keys.first()
            typeDeclarations.containsKey(rootTypeName) -> rootTypeName
            else -> throw IllegalArgumentException("Root type not found: $rootTypeName")
        }

        return TypeAnalysisResult(
            language = language,
            queryResourcePath = asset.queryResourcePath,
            parserResourcePath = asset.parserResourcePath,
            rootTypeName = resolvedRootTypeName,
            typeDeclarations = typeDeclarations,
        )
    }
}

private interface LanguageTypeDeclarationAnalyzer {
    fun analyze(sourceCode: String): LinkedHashMap<String, TypeDeclaration>
}

private class JavaTypeDeclarationAnalyzer : LanguageTypeDeclarationAnalyzer {
    private val declarationPattern = Regex("""\b(class|record|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b""")

    override fun analyze(sourceCode: String): LinkedHashMap<String, TypeDeclaration> {
        val declarations = linkedMapOf<String, TypeDeclaration>()
        val sanitizedSourceCode = sanitizeSourceCode(sourceCode)
        var searchStartIndex = 0

        while (true) {
            val matchResult = declarationPattern.find(sanitizedSourceCode, searchStartIndex) ?: break
            val declarationKeyword = matchResult.groupValues[1]
            val typeName = matchResult.groupValues[2]
            val declarationHeaderText = sanitizedSourceCode.substring(
                matchResult.range.last + 1,
                sanitizedSourceCode.indexOfAny(charArrayOf('{', '('), matchResult.range.last + 1)
                    .takeIf { it != -1 } ?: sanitizedSourceCode.length
            )

            if (declarationKeyword == "record") {
                val openParenthesisIndex = sanitizedSourceCode.indexOf('(', matchResult.range.last + 1)
                if (openParenthesisIndex == -1) {
                    searchStartIndex = matchResult.range.last + 1
                    continue
                }
                val closeParenthesisIndex = findMatchingDelimiter(
                    text = sanitizedSourceCode,
                    openIndex = openParenthesisIndex,
                    openCharacter = '(',
                    closeCharacter = ')',
                )
                declarations[typeName] = TypeDeclaration(
                    name = typeName,
                    declarationKind = TypeDeclarationKind.RECORD,
                    fields = parseJavaRecordComponents(
                        componentsText = sanitizedSourceCode.substring(openParenthesisIndex + 1, closeParenthesisIndex),
                    ),
                    superTypeNames = resolveJavaSuperTypeNames(declarationHeaderText),
                )
                searchStartIndex = closeParenthesisIndex + 1
                continue
            }

            val openBraceIndex = sanitizedSourceCode.indexOf('{', matchResult.range.last + 1)
            if (openBraceIndex == -1) {
                searchStartIndex = matchResult.range.last + 1
                continue
            }
            val closeBraceIndex = findMatchingDelimiter(
                text = sanitizedSourceCode,
                openIndex = openBraceIndex,
                openCharacter = '{',
                closeCharacter = '}',
            )
            val declarationBodyText = sanitizedSourceCode.substring(openBraceIndex + 1, closeBraceIndex)
            declarations[typeName] = TypeDeclaration(
                name = typeName,
                declarationKind = if (declarationKeyword == "enum") TypeDeclarationKind.ENUM else TypeDeclarationKind.CLASS,
                fields = if (declarationKeyword == "enum") emptyList() else parseJavaFieldDeclarations(declarationBodyText),
                superTypeNames = if (declarationKeyword == "enum") emptyList() else resolveJavaSuperTypeNames(declarationHeaderText),
                enumValues = if (declarationKeyword == "enum") parseEnumValues(declarationBodyText) else emptyList(),
            )
            searchStartIndex = closeBraceIndex + 1
        }

        return declarations
    }
}

private class KotlinTypeDeclarationAnalyzer : LanguageTypeDeclarationAnalyzer {
    private val declarationPattern = Regex("""\b((?:data\s+)?class|enum\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)\b""")

    override fun analyze(sourceCode: String): LinkedHashMap<String, TypeDeclaration> {
        val declarations = linkedMapOf<String, TypeDeclaration>()
        val sanitizedSourceCode = sanitizeSourceCode(sourceCode)
        var searchStartIndex = 0

        while (true) {
            val matchResult = declarationPattern.find(sanitizedSourceCode, searchStartIndex) ?: break
            val declarationKeyword = matchResult.groupValues[1]
            val typeName = matchResult.groupValues[2]
            val declarationStartIndex = matchResult.range.last + 1

            val openBraceIndex = sanitizedSourceCode.indexOf('{', declarationStartIndex)
            val openParenthesisIndex = sanitizedSourceCode.indexOf('(', declarationStartIndex)
            val constructorText = if (
                openParenthesisIndex != -1 &&
                (openBraceIndex == -1 || openParenthesisIndex < openBraceIndex)
            ) {
                val closeParenthesisIndex = findMatchingDelimiter(
                    text = sanitizedSourceCode,
                    openIndex = openParenthesisIndex,
                    openCharacter = '(',
                    closeCharacter = ')',
                )
                sanitizedSourceCode.substring(openParenthesisIndex + 1, closeParenthesisIndex)
            } else {
                null
            }

            val bodyText = if (openBraceIndex != -1) {
                val closeBraceIndex = findMatchingDelimiter(
                    text = sanitizedSourceCode,
                    openIndex = openBraceIndex,
                    openCharacter = '{',
                    closeCharacter = '}',
                )
                searchStartIndex = closeBraceIndex + 1
                sanitizedSourceCode.substring(openBraceIndex + 1, closeBraceIndex)
            } else {
                searchStartIndex = matchResult.range.last + 1
                ""
            }

            val fields = if (declarationKeyword.startsWith("enum")) {
                emptyList()
            } else {
                buildList {
                    constructorText?.let { addAll(parseKotlinConstructorProperties(it)) }
                    addAll(parseKotlinBodyProperties(bodyText))
                }.distinctBy { it.name }
            }

            declarations[typeName] = TypeDeclaration(
                name = typeName,
                declarationKind = if (declarationKeyword.startsWith("enum")) TypeDeclarationKind.ENUM else TypeDeclarationKind.CLASS,
                fields = fields,
                superTypeNames = if (declarationKeyword.startsWith("enum")) emptyList() else resolveKotlinSuperTypeNames(
                    sanitizedSourceCode.substring(declarationStartIndex, openBraceIndex.takeIf { it != -1 } ?: sanitizedSourceCode.length)
                ),
                enumValues = if (declarationKeyword.startsWith("enum")) parseEnumValues(bodyText) else emptyList(),
            )
        }

        return declarations
    }
}

private class TypeScriptTypeDeclarationAnalyzer : LanguageTypeDeclarationAnalyzer {
    private val declarationPattern = Regex("""\b(interface|type|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b""")

    override fun analyze(sourceCode: String): LinkedHashMap<String, TypeDeclaration> {
        val declarations = linkedMapOf<String, TypeDeclaration>()
        val sanitizedSourceCode = sanitizeSourceCode(sourceCode)
        var searchStartIndex = 0

        while (true) {
            val matchResult = declarationPattern.find(sanitizedSourceCode, searchStartIndex) ?: break
            val declarationKeyword = matchResult.groupValues[1]
            val typeName = matchResult.groupValues[2]
            val declarationHeaderText = sanitizedSourceCode.substring(
                matchResult.range.last + 1,
                sanitizedSourceCode.indexOf('{', matchResult.range.last + 1).takeIf { it != -1 } ?: sanitizedSourceCode.length
            )

            val openBraceIndex = when (declarationKeyword) {
                "interface" -> sanitizedSourceCode.indexOf('{', matchResult.range.last + 1)
                "type" -> {
                    val equalSignIndex = sanitizedSourceCode.indexOf('=', matchResult.range.last + 1)
                    if (equalSignIndex == -1) {
                        searchStartIndex = matchResult.range.last + 1
                        continue
                    }
                    sanitizedSourceCode.indexOf('{', equalSignIndex + 1)
                }

                else -> -1
            }

            if (openBraceIndex == -1) {
                searchStartIndex = matchResult.range.last + 1
                continue
            }

            val closeBraceIndex = findMatchingDelimiter(
                text = sanitizedSourceCode,
                openIndex = openBraceIndex,
                openCharacter = '{',
                closeCharacter = '}',
            )

            declarations[typeName] = TypeDeclaration(
                name = typeName,
                declarationKind = if (declarationKeyword == "enum") TypeDeclarationKind.ENUM else TypeDeclarationKind.INTERFACE,
                fields = if (declarationKeyword == "enum") emptyList() else parseTypeScriptFieldDeclarations(
                    bodyText = sanitizedSourceCode.substring(openBraceIndex + 1, closeBraceIndex),
                ),
                superTypeNames = if (declarationKeyword == "interface") {
                    resolveTypeScriptSuperTypeNames(declarationHeaderText)
                } else {
                    emptyList()
                },
                enumValues = if (declarationKeyword == "enum") {
                    parseEnumValues(sanitizedSourceCode.substring(openBraceIndex + 1, closeBraceIndex))
                } else {
                    emptyList()
                },
            )
            searchStartIndex = closeBraceIndex + 1
        }

        return declarations
    }
}

private class GoTypeDeclarationAnalyzer : LanguageTypeDeclarationAnalyzer {
    private val declarationPattern = Regex("""\btype\s+([A-Za-z_][A-Za-z0-9_]*)\s+struct\s*\{""")

    override fun analyze(sourceCode: String): LinkedHashMap<String, TypeDeclaration> {
        val declarations = linkedMapOf<String, TypeDeclaration>()
        val sanitizedSourceCode = sanitizeSourceCode(sourceCode)
        var searchStartIndex = 0

        while (true) {
            val matchResult = declarationPattern.find(sanitizedSourceCode, searchStartIndex) ?: break
            val typeName = matchResult.groupValues[1]
            val openBraceIndex = sanitizedSourceCode.indexOf('{', matchResult.range.last)
            if (openBraceIndex == -1) {
                searchStartIndex = matchResult.range.last + 1
                continue
            }
            val closeBraceIndex = findMatchingDelimiter(
                text = sanitizedSourceCode,
                openIndex = openBraceIndex,
                openCharacter = '{',
                closeCharacter = '}',
            )
            declarations[typeName] = TypeDeclaration(
                name = typeName,
                declarationKind = TypeDeclarationKind.STRUCT,
                fields = parseGoFieldDeclarations(
                    bodyText = sanitizedSourceCode.substring(openBraceIndex + 1, closeBraceIndex),
                ),
            )
            searchStartIndex = closeBraceIndex + 1
        }

        return declarations
    }
}

private fun parseJavaRecordComponents(componentsText: String): List<TypeField> {
    return splitTopLevel(componentsText, ',')
        .mapNotNull { componentText ->
            val normalizedComponentText = normalizeWhitespace(removeAnnotations(componentText))
                .substringBefore('=')
                .trim()
            if (normalizedComponentText.isBlank()) {
                return@mapNotNull null
            }
            val fieldName = normalizedComponentText.substringAfterLast(' ', "")
            var fieldTypeText = normalizedComponentText.substringBeforeLast(' ', "")
            if (fieldName.isBlank() || fieldTypeText.isBlank()) {
                return@mapNotNull null
            }
            if (fieldName.endsWith("[]")) {
                fieldTypeText += "[]"
            }
            TypeField(
                name = fieldName.removeSuffix("[]"),
                typeReference = parseTypeReference(fieldTypeText, TypeConversionLanguage.JAVA),
            )
        }
}

private fun parseJavaFieldDeclarations(bodyText: String): List<TypeField> {
    return splitTopLevelStatements(bodyText, setOf(';'))
        .mapNotNull { statementText ->
            val normalizedStatementText = normalizeWhitespace(removeAnnotations(statementText))
                .substringBefore('=')
                .trim()
            if (normalizedStatementText.isBlank() || normalizedStatementText.contains('(')) {
                return@mapNotNull null
            }
            if (normalizedStatementText.split(' ').contains("static")) {
                return@mapNotNull null
            }

            val candidateText = stripLeadingKeywords(normalizedStatementText, JAVA_FIELD_KEYWORDS)
            val fieldName = candidateText.substringAfterLast(' ', "")
            var fieldTypeText = candidateText.substringBeforeLast(' ', "")
            if (fieldName.isBlank() || fieldTypeText.isBlank()) {
                return@mapNotNull null
            }
            if (fieldName.endsWith("[]")) {
                fieldTypeText += "[]"
            }

            TypeField(
                name = fieldName.removeSuffix("[]"),
                typeReference = parseTypeReference(fieldTypeText, TypeConversionLanguage.JAVA),
            )
        }
}

private fun parseKotlinConstructorProperties(constructorText: String): List<TypeField> {
    return splitTopLevel(constructorText, ',')
        .mapNotNull { parameterText ->
            val normalizedParameterText = normalizeWhitespace(removeAnnotations(parameterText))
                .substringBefore('=')
                .trim()
            if (!normalizedParameterText.contains(" val ") && !normalizedParameterText.startsWith("val ") &&
                !normalizedParameterText.contains(" var ") && !normalizedParameterText.startsWith("var ")
            ) {
                return@mapNotNull null
            }

            val propertyText = stripLeadingKeywords(normalizedParameterText, KOTLIN_PARAMETER_KEYWORDS)
            val propertyName = propertyText.substringBefore(':').substringAfterLast(' ').trim()
            val propertyTypeText = propertyText.substringAfter(':', "").trim()
            if (propertyName.isBlank() || propertyTypeText.isBlank()) {
                return@mapNotNull null
            }

            TypeField(
                name = propertyName,
                typeReference = parseTypeReference(propertyTypeText, TypeConversionLanguage.KOTLIN),
            )
        }
}

private fun parseKotlinBodyProperties(bodyText: String): List<TypeField> {
    return splitTopLevelStatements(bodyText, setOf('\n', ';'))
        .mapNotNull { statementText ->
            val normalizedStatementText = normalizeWhitespace(removeAnnotations(statementText))
                .substringBefore('=')
                .trim()
            if (normalizedStatementText.isBlank() || normalizedStatementText.contains('(')) {
                return@mapNotNull null
            }
            if (!normalizedStatementText.contains(" val ") && !normalizedStatementText.startsWith("val ") &&
                !normalizedStatementText.contains(" var ") && !normalizedStatementText.startsWith("var ")
            ) {
                return@mapNotNull null
            }

            val propertyText = stripLeadingKeywords(normalizedStatementText, KOTLIN_BODY_KEYWORDS)
            val propertyName = propertyText.substringBefore(':').substringAfterLast(' ').trim()
            val propertyTypeText = propertyText.substringAfter(':', "").trim()
            if (propertyName.isBlank() || propertyTypeText.isBlank()) {
                return@mapNotNull null
            }

            TypeField(
                name = propertyName,
                typeReference = parseTypeReference(propertyTypeText, TypeConversionLanguage.KOTLIN),
            )
        }
}

private fun parseEnumValues(bodyText: String): List<String> {
    return splitTopLevelStatements(bodyText.substringBefore(';'), setOf(',', '\n'))
        .map { valueText ->
            valueText.substringBefore('(').substringBefore('=').trim()
        }
        .filter { enumValue ->
            enumValue.isNotBlank() &&
                enumValue.firstOrNull()?.isLetter() == true
        }
}

private fun parseTypeScriptFieldDeclarations(bodyText: String): List<TypeField> {
    return splitTopLevelStatements(bodyText, setOf('\n', ';'))
        .mapNotNull { statementText ->
            val normalizedStatementText = normalizeWhitespace(statementText)
                .removeSuffix(",")
                .trim()
            if (normalizedStatementText.isBlank() || normalizedStatementText.contains('(')) {
                return@mapNotNull null
            }

            val matchResult = TYPESCRIPT_FIELD_PATTERN.matchEntire(normalizedStatementText) ?: return@mapNotNull null
            val propertyName = matchResult.groupValues[1]
            val isOptional = matchResult.groupValues[2] == "?"
            val propertyTypeText = matchResult.groupValues[3].trim()

            TypeField(
                name = propertyName,
                typeReference = parseTypeReference(propertyTypeText, TypeConversionLanguage.TYPESCRIPT),
                isOptional = isOptional,
            )
        }
}

private fun parseGoFieldDeclarations(bodyText: String): List<TypeField> {
    return splitTopLevelStatements(bodyText, setOf('\n', ';'))
        .mapNotNull { statementText ->
            val normalizedStatementText = normalizeWhitespace(statementText).trim()
            if (normalizedStatementText.isBlank()) {
                return@mapNotNull null
            }

            val declarationText = normalizedStatementText.substringBefore('`').trim()
            val tagText = normalizedStatementText.substringAfter('`', "").substringBeforeLast('`', "")
            val declarationParts = declarationText.split(Regex("""\s+"""))
            if (declarationParts.size < 2) {
                return@mapNotNull null
            }

            val sourceFieldName = declarationParts.first()
            val propertyName = resolveGoJsonFieldName(sourceFieldName, tagText) ?: return@mapNotNull null
            val propertyTypeText = declarationParts.drop(1).joinToString(" ").trim()
            if (propertyTypeText.isBlank()) {
                return@mapNotNull null
            }

            TypeField(
                name = propertyName,
                typeReference = parseTypeReference(propertyTypeText, TypeConversionLanguage.GO),
            )
        }
}

private fun parseTypeReference(
    typeText: String,
    language: TypeConversionLanguage,
): TypeReference {
    val normalizedTypeText = normalizeTypeText(typeText)
    if (normalizedTypeText.isBlank()) {
        return TypeReference.AnyValue
    }

    return when (language) {
        TypeConversionLanguage.JAVA,
        TypeConversionLanguage.KOTLIN -> parseJvmTypeReference(normalizedTypeText, language)

        TypeConversionLanguage.TYPESCRIPT -> parseTypeScriptTypeReference(normalizedTypeText)
        TypeConversionLanguage.GO -> parseGoTypeReference(normalizedTypeText)
    }
}

private fun parseJvmTypeReference(
    typeText: String,
    language: TypeConversionLanguage,
): TypeReference {
    if (typeText.endsWith("?")) {
        return TypeReference.Nullable(parseJvmTypeReference(typeText.removeSuffix("?"), language))
    }
    if (typeText.endsWith("[]")) {
        return TypeReference.ListReference(
            elementType = parseJvmTypeReference(typeText.removeSuffix("[]"), language),
        )
    }

    val primitiveType = resolvePrimitiveType(typeText, language)
    if (primitiveType != null) {
        return TypeReference.Primitive(primitiveType)
    }

    if (typeText.contains('<') && typeText.endsWith('>')) {
        val genericStartIndex = typeText.indexOf('<')
        val rawTypeName = simpleTypeName(typeText.substring(0, genericStartIndex))
        val genericArguments = splitTopLevel(typeText.substring(genericStartIndex + 1, typeText.length - 1), ',')

        if (rawTypeName in COLLECTION_TYPE_NAMES && genericArguments.isNotEmpty()) {
            return TypeReference.ListReference(
                elementType = parseJvmTypeReference(genericArguments.first(), language),
            )
        }
        if (rawTypeName in MAP_TYPE_NAMES && genericArguments.size >= 2) {
            return TypeReference.MapReference(
                keyType = parseJvmTypeReference(genericArguments.first(), language),
                valueType = parseJvmTypeReference(genericArguments.last(), language),
            )
        }
        if (rawTypeName == "Optional" && genericArguments.isNotEmpty()) {
            return TypeReference.Nullable(parseJvmTypeReference(genericArguments.first(), language))
        }
    }

    if (typeText == "*" || typeText == "?") {
        return TypeReference.AnyValue
    }

    return TypeReference.Named(simpleTypeName(typeText))
}

private fun parseTypeScriptTypeReference(typeText: String): TypeReference {
    val unionTypes = splitTopLevel(typeText, '|')
    if (unionTypes.size > 1) {
        val nonNullableTypes = unionTypes.filterNot { unionType ->
            unionType == "null" || unionType == "undefined"
        }
        if (nonNullableTypes.size == 1 && nonNullableTypes.size != unionTypes.size) {
            return TypeReference.Nullable(parseTypeScriptTypeReference(nonNullableTypes.first()))
        }
        return TypeReference.AnyValue
    }

    if (typeText.endsWith("[]")) {
        return TypeReference.ListReference(
            elementType = parseTypeScriptTypeReference(typeText.removeSuffix("[]")),
        )
    }
    if (typeText.startsWith("Array<") && typeText.endsWith('>')) {
        val innerTypeText = typeText.substringAfter('<').dropLast(1)
        return TypeReference.ListReference(
            elementType = parseTypeScriptTypeReference(innerTypeText),
        )
    }
    if ((typeText.startsWith("Record<") || typeText.startsWith("Map<")) && typeText.endsWith('>')) {
        val innerTypeText = typeText.substringAfter('<').dropLast(1)
        val genericArguments = splitTopLevel(innerTypeText, ',')
        if (genericArguments.size >= 2) {
            return TypeReference.MapReference(
                keyType = parseTypeScriptTypeReference(genericArguments.first()),
                valueType = parseTypeScriptTypeReference(genericArguments.last()),
            )
        }
    }
    if (typeText.startsWith('{') && typeText.endsWith('}')) {
        return TypeReference.InlineObject(
            fields = parseTypeScriptFieldDeclarations(typeText.substring(1, typeText.length - 1)),
        )
    }

    val primitiveType = resolvePrimitiveType(typeText, TypeConversionLanguage.TYPESCRIPT)
    if (primitiveType != null) {
        return TypeReference.Primitive(primitiveType)
    }
    if (typeText == "any" || typeText == "unknown") {
        return TypeReference.AnyValue
    }

    return TypeReference.Named(simpleTypeName(typeText))
}

private fun parseGoTypeReference(typeText: String): TypeReference {
    if (typeText.startsWith("*")) {
        return TypeReference.Nullable(parseGoTypeReference(typeText.removePrefix("*").trim()))
    }
    if (typeText.startsWith("[]")) {
        return TypeReference.ListReference(
            elementType = parseGoTypeReference(typeText.removePrefix("[]").trim()),
        )
    }
    if (typeText.startsWith("map[") && typeText.contains(']')) {
        val closeBracketIndex = typeText.indexOf(']')
        val keyTypeText = typeText.substring(4, closeBracketIndex)
        val valueTypeText = typeText.substring(closeBracketIndex + 1)
        return TypeReference.MapReference(
            keyType = parseGoTypeReference(keyTypeText),
            valueType = parseGoTypeReference(valueTypeText),
        )
    }
    if (typeText.startsWith("struct {") && typeText.endsWith('}')) {
        return TypeReference.InlineObject(
            fields = parseGoFieldDeclarations(typeText.removePrefix("struct {").removeSuffix("}")),
        )
    }

    val primitiveType = resolvePrimitiveType(typeText, TypeConversionLanguage.GO)
    if (primitiveType != null) {
        return TypeReference.Primitive(primitiveType)
    }
    if (typeText == "interface{}" || typeText == "any") {
        return TypeReference.AnyValue
    }

    return TypeReference.Named(simpleTypeName(typeText))
}

private fun resolvePrimitiveType(
    typeText: String,
    language: TypeConversionLanguage,
): TypePrimitiveKind? {
    val simpleTypeName = simpleTypeName(typeText)

    return when (language) {
        TypeConversionLanguage.JAVA -> when (simpleTypeName) {
            in JAVA_STRING_TYPE_NAMES -> TypePrimitiveKind.STRING
            in JAVA_NUMBER_TYPE_NAMES -> TypePrimitiveKind.NUMBER
            in BOOLEAN_TYPE_NAMES -> TypePrimitiveKind.BOOLEAN
            else -> null
        }

        TypeConversionLanguage.KOTLIN -> when (simpleTypeName) {
            in KOTLIN_STRING_TYPE_NAMES -> TypePrimitiveKind.STRING
            in KOTLIN_NUMBER_TYPE_NAMES -> TypePrimitiveKind.NUMBER
            in BOOLEAN_TYPE_NAMES -> TypePrimitiveKind.BOOLEAN
            else -> null
        }

        TypeConversionLanguage.TYPESCRIPT -> when (simpleTypeName) {
            "string" -> TypePrimitiveKind.STRING
            "number" -> TypePrimitiveKind.NUMBER
            "boolean" -> TypePrimitiveKind.BOOLEAN
            else -> null
        }

        TypeConversionLanguage.GO -> when (simpleTypeName) {
            in GO_STRING_TYPE_NAMES -> TypePrimitiveKind.STRING
            in GO_NUMBER_TYPE_NAMES -> TypePrimitiveKind.NUMBER
            in GO_BOOLEAN_TYPE_NAMES -> TypePrimitiveKind.BOOLEAN
            else -> null
        }
    }
}

private fun resolveJavaSuperTypeNames(headerText: String): List<String> {
    val extendsText = headerText.substringAfter("extends", "").substringBefore("implements").trim()
    return extendsText.takeIf { it.isNotBlank() }?.let { listOf(simpleTypeName(it)) } ?: emptyList()
}

private fun resolveKotlinSuperTypeNames(headerText: String): List<String> {
    val inheritanceText = headerText.substringAfter(':', "").trim()
    if (inheritanceText.isBlank()) {
        return emptyList()
    }

    return splitTopLevel(inheritanceText, ',')
        .map { simpleTypeName(it.substringBefore('(').trim()) }
        .filter { it.isNotBlank() }
}

private fun resolveTypeScriptSuperTypeNames(headerText: String): List<String> {
    val extendsText = headerText.substringAfter("extends", "").trim()
    if (extendsText.isBlank()) {
        return emptyList()
    }

    return splitTopLevel(extendsText, ',')
        .map(::simpleTypeName)
        .filter { it.isNotBlank() }
}

private fun sanitizeSourceCode(sourceCode: String): String {
    val sourceCodeWithoutBlockComments = BLOCK_COMMENT_REGEX.replace(sourceCode, " ")
    return sourceCodeWithoutBlockComments.lines().joinToString("\n") { line ->
        line.substringBefore("//")
    }
}

private fun removeAnnotations(text: String): String {
    return ANNOTATION_REGEX.replace(text, " ")
}

private fun normalizeWhitespace(text: String): String {
    return WHITESPACE_REGEX.replace(text.trim(), " ")
}

private fun normalizeTypeText(typeText: String): String {
    return normalizeWhitespace(typeText)
        .removePrefix("readonly ")
        .removePrefix("const ")
        .trim()
}

private fun splitTopLevel(
    text: String,
    delimiter: Char,
): List<String> {
    val parts = mutableListOf<String>()
    var partStartIndex = 0
    var angleDepth = 0
    var parenthesisDepth = 0
    var braceDepth = 0
    var bracketDepth = 0

    text.forEachIndexed { index, character ->
        when (character) {
            '<' -> angleDepth++
            '>' -> if (angleDepth > 0) angleDepth--
            '(' -> parenthesisDepth++
            ')' -> if (parenthesisDepth > 0) parenthesisDepth--
            '{' -> braceDepth++
            '}' -> if (braceDepth > 0) braceDepth--
            '[' -> bracketDepth++
            ']' -> if (bracketDepth > 0) bracketDepth--
            delimiter -> if (
                angleDepth == 0 &&
                parenthesisDepth == 0 &&
                braceDepth == 0 &&
                bracketDepth == 0
            ) {
                parts += text.substring(partStartIndex, index).trim()
                partStartIndex = index + 1
            }
        }
    }

    parts += text.substring(partStartIndex).trim()
    return parts.filter { it.isNotBlank() }
}

private fun splitTopLevelStatements(
    text: String,
    delimiters: Set<Char>,
): List<String> {
    val parts = mutableListOf<String>()
    var partStartIndex = 0
    var angleDepth = 0
    var parenthesisDepth = 0
    var braceDepth = 0
    var bracketDepth = 0

    text.forEachIndexed { index, character ->
        when (character) {
            '<' -> angleDepth++
            '>' -> if (angleDepth > 0) angleDepth--
            '(' -> parenthesisDepth++
            ')' -> if (parenthesisDepth > 0) parenthesisDepth--
            '{' -> braceDepth++
            '}' -> if (braceDepth > 0) braceDepth--
            '[' -> bracketDepth++
            ']' -> if (bracketDepth > 0) bracketDepth--
            else -> if (
                character in delimiters &&
                angleDepth == 0 &&
                parenthesisDepth == 0 &&
                braceDepth == 0 &&
                bracketDepth == 0
            ) {
                parts += text.substring(partStartIndex, index).trim()
                partStartIndex = index + 1
            }
        }
    }

    parts += text.substring(partStartIndex).trim()
    return parts.filter { it.isNotBlank() }
}

private fun stripLeadingKeywords(
    text: String,
    keywords: Set<String>,
): String {
    val words = normalizeWhitespace(text).split(' ').toMutableList()
    while (words.isNotEmpty() && words.first() in keywords) {
        words.removeAt(0)
    }
    return words.joinToString(" ")
}

private fun simpleTypeName(typeText: String): String {
    return typeText
        .substringAfterLast('.')
        .substringAfterLast('/')
        .trim()
}

private fun findMatchingDelimiter(
    text: String,
    openIndex: Int,
    openCharacter: Char,
    closeCharacter: Char,
): Int {
    var depth = 0
    for (index in openIndex until text.length) {
        when (text[index]) {
            openCharacter -> depth++
            closeCharacter -> {
                depth--
                if (depth == 0) {
                    return index
                }
            }
        }
    }
    throw IllegalArgumentException("Matching delimiter not found for index $openIndex")
}

private fun resolveGoJsonFieldName(
    sourceFieldName: String,
    tagText: String,
): String? {
    if (tagText.isNotBlank()) {
        val matchResult = GO_JSON_TAG_REGEX.find(tagText)
        if (matchResult != null) {
            val tagName = matchResult.groupValues[1]
            if (tagName == "-") {
                return null
            }
            if (tagName.isNotBlank()) {
                return tagName
            }
        }
    }

    return when {
        sourceFieldName.isBlank() -> null
        sourceFieldName.length == 1 -> sourceFieldName.lowercase()
        sourceFieldName[0].isUpperCase() && sourceFieldName[1].isUpperCase() -> {
            val acronymLength = sourceFieldName
                .windowed(size = 2, step = 1, partialWindows = true)
                .takeWhile { window ->
                    window.length == 1 || (window[0].isUpperCase() && window[1].isUpperCase())
                }
                .count()
            sourceFieldName.substring(0, acronymLength).lowercase() + sourceFieldName.substring(acronymLength)
        }

        else -> sourceFieldName.replaceFirstChar { character ->
            character.lowercase()
        }
    }
}

private val BLOCK_COMMENT_REGEX = Regex("""/\*.*?\*/""", setOf(RegexOption.DOT_MATCHES_ALL))
private val ANNOTATION_REGEX = Regex("""@[A-Za-z_][A-Za-z0-9_.]*(\([^)]*\))?""")
private val WHITESPACE_REGEX = Regex("""\s+""")
private val TYPESCRIPT_FIELD_PATTERN = Regex("""([A-Za-z_$][A-Za-z0-9_$]*)(\??)\s*:\s*(.+)""")
private val GO_JSON_TAG_REGEX = Regex("""json:"([^",]+)""")
private val JAVA_FIELD_KEYWORDS = setOf(
    "public",
    "protected",
    "private",
    "final",
    "transient",
    "volatile",
)
private val KOTLIN_PARAMETER_KEYWORDS = setOf(
    "public",
    "private",
    "protected",
    "internal",
    "override",
    "open",
)
private val KOTLIN_BODY_KEYWORDS = setOf(
    "public",
    "private",
    "protected",
    "internal",
    "override",
    "open",
    "lateinit",
    "const",
)
private val COLLECTION_TYPE_NAMES = setOf(
    "Array",
    "ArrayList",
    "Collection",
    "Iterable",
    "List",
    "MutableList",
    "MutableSet",
    "Set",
)
private val MAP_TYPE_NAMES = setOf(
    "HashMap",
    "LinkedHashMap",
    "Map",
    "MutableMap",
)
private val JAVA_STRING_TYPE_NAMES = setOf(
    "Char",
    "CharSequence",
    "Character",
    "Instant",
    "LocalDate",
    "LocalDateTime",
    "OffsetDateTime",
    "String",
    "UUID",
    "char",
)
private val JAVA_NUMBER_TYPE_NAMES = setOf(
    "BigDecimal",
    "BigInteger",
    "Byte",
    "Double",
    "Float",
    "Int",
    "Integer",
    "Long",
    "Short",
    "byte",
    "double",
    "float",
    "int",
    "long",
    "short",
)
private val KOTLIN_STRING_TYPE_NAMES = setOf(
    "Char",
    "String",
)
private val KOTLIN_NUMBER_TYPE_NAMES = setOf(
    "Byte",
    "Double",
    "Float",
    "Int",
    "Long",
    "Short",
    "UByte",
    "UInt",
    "ULong",
    "UShort",
)
private val BOOLEAN_TYPE_NAMES = setOf(
    "Boolean",
    "boolean",
)
private val GO_STRING_TYPE_NAMES = setOf(
    "rune",
    "string",
)
private val GO_NUMBER_TYPE_NAMES = setOf(
    "float32",
    "float64",
    "int",
    "int16",
    "int32",
    "int64",
    "int8",
    "uint",
    "uint16",
    "uint32",
    "uint64",
    "uint8",
)
private val GO_BOOLEAN_TYPE_NAMES = setOf(
    "bool",
)
