package com.livteam.jsoninja.services.treesitter

enum class SupportedLanguage(
    val languageId: Int,
    val jsonName: String,
    val queryResourcePath: String,
) {
    JAVA(
        languageId = 0,
        jsonName = "java",
        queryResourcePath = "tree-sitter/queries/java/type-declarations.scm",
    ),
    KOTLIN(
        languageId = 1,
        jsonName = "kotlin",
        queryResourcePath = "tree-sitter/queries/kotlin/type-declarations.scm",
    ),
    TYPESCRIPT(
        languageId = 2,
        jsonName = "typescript",
        queryResourcePath = "tree-sitter/queries/typescript/type-declarations.scm",
    ),
    GO(
        languageId = 3,
        jsonName = "go",
        queryResourcePath = "tree-sitter/queries/go/type-declarations.scm",
    ),
}
