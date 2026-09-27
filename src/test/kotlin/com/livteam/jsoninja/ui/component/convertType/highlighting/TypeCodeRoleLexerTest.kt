package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.psi.tree.IElementType
import com.livteam.jsoninja.model.SupportedLanguage
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class TypeCodeRoleLexerTest {
    private data class Token(val start: Int, val end: Int, val state: Int, val type: IElementType)
    private data class Role(val start: Int, val end: Int, val name: String)
    private val roles = setOf(TypeCodeToken.TYPE_NAME, TypeCodeToken.PREDEFINED_TYPE, TypeCodeToken.FIELD,
        TypeCodeToken.FUNCTION_DECLARATION, TypeCodeToken.FUNCTION_CALL, TypeCodeToken.METADATA)

    @Test fun allElevenLanguagesMatchIndependentDeclarationRoleGoldens() {
        for (language in SupportedLanguage.entries) {
            val annotated = resource("/typeConversion/highlighting/fallback/${language.fileExtension}.txt")
            val source = StringBuilder()
            val expected = mutableListOf<Role>()
            var offset = 0
            val markers = Regex("<([A-Z_]+)>(.*?)</\\1>")
            for (match in markers.findAll(annotated)) {
                source.append(annotated.substring(offset, match.range.first))
                val start = source.length
                source.append(match.groupValues[2])
                expected += Role(start, source.length, match.groupValues[1])
                offset = match.range.last + 1
            }
            source.append(annotated.substring(offset))
            val actual = scan(source.toString(), language).filter { it.type in roles }.map { Role(it.start, it.end, it.type.toString()) }
            assertEquals(language.name, expected, actual)
        }
    }

    @Test fun everyGeneratedLanguageHasTypeAndFieldRolesWithoutAPlatformLanguagePlugin() {
        for (language in SupportedLanguage.entries) {
            val source = resource("/typeConversion/golden/object.${language.fileExtension}")
            val tokens = scan(source, language)
            assertTrue(language.name + " Root declaration", tokens.any { it.type == TypeCodeToken.TYPE_NAME && source.substring(it.start, it.end) == "Root" })
            assertTrue(language.name + " id field", tokens.any { it.type == TypeCodeToken.FIELD && source.substring(it.start, it.end).lowercase() == "id" })
            assertTrue(language.name + " type tokens", tokens.any { it.type == TypeCodeToken.PREDEFINED_TYPE || it.type == TypeCodeToken.KEYWORD })
        }
    }

    @Test fun fullAndRestartedScansAgreeForEveryLanguageAndEveryTokenBoundary() {
        for (language in SupportedLanguage.entries) {
            val source = resource("/typeConversion/golden/object.${language.fileExtension}") + "\n" +
                resource("/typeConversion/highlighting/fallback/${language.fileExtension}.txt").replace(Regex("</?[A-Z_]+>"), "")
            val expected = scan(source, language)
            expected.forEachIndexed { index, token ->
                assertEquals("${language.name} restart at ${token.start}", expected.drop(index), scan(source, language, token.start, source.length, token.state))
            }
        }
    }

    @Test fun truncatedRangesAndUnicodeDoNotEscapeTheRequestedBuffer() {
        for (language in SupportedLanguage.entries) {
            val source = checkNotNull(TypeCodeHighlighterResolver.profileFor(language.fileExtension)).nativeProbe + "\n/* 한글 😀 */\nvalue: List<Map<String, User>>"
            for (end in 0..source.length) {
                val tokens = scan(source, language, end = end)
                assertEquals("${language.name} $end", end, tokens.lastOrNull()?.end ?: 0)
                tokens.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
                assertTrue(tokens.all { it.start < it.end && it.end <= end })
            }
        }
    }

    @Test fun pythonScreenshotTypesAndFieldsAreNotGenericIdentifiers() {
        val source = "from typing import Any, NotRequired, TypedDict\nclass RootItem(TypedDict):\n    createdAt: NotRequired[float | None]\n    limit: NotRequired[bool | str | None]\nRoot = list[RootItem]"
        val tokens = scan(source, SupportedLanguage.PYTHON)
        val expected = mapOf("Any" to TypeCodeToken.PREDEFINED_TYPE, "NotRequired" to TypeCodeToken.PREDEFINED_TYPE,
            "TypedDict" to TypeCodeToken.PREDEFINED_TYPE, "RootItem" to TypeCodeToken.TYPE_NAME,
            "createdAt" to TypeCodeToken.FIELD, "limit" to TypeCodeToken.FIELD, "float" to TypeCodeToken.PREDEFINED_TYPE,
            "bool" to TypeCodeToken.PREDEFINED_TYPE, "str" to TypeCodeToken.PREDEFINED_TYPE, "list" to TypeCodeToken.PREDEFINED_TYPE)
        for ((word, role) in expected) {
            val matches = tokens.filter { source.substring(it.start, it.end) == word }
            assertTrue(word, matches.isNotEmpty())
            matches.forEach { assertSame(word, role, it.type) }
        }
    }

    @Test fun addedFallbacksKeepCommentsAndDeclarationsInsideLiterals() {
        val samples = mapOf(
            SupportedLanguage.KOTLIN to "val text = \"\"\"class Fake(val field: String) // 😀\n\"\"\"",
            SupportedLanguage.JAVA to "String text = \"\"\"\nclass Fake { String field; } // 😀\n\"\"\";",
            SupportedLanguage.TYPESCRIPT to "const text = `class Fake { field: string } // 😀\n`;",
            SupportedLanguage.GO to "var text = `type Fake struct { Field string } // 😀\n`",
        )
        for ((language, source) in samples) {
            val tokens = scan(source, language)
            val literal = tokens.single { it.type == TypeCodeToken.STRING }
            assertTrue(language.name, source.substring(literal.start, literal.end).contains("Fake"))
            assertFalse(tokens.any { it.type == TypeCodeToken.LINE_COMMENT })
        }
    }

    @Test fun longLookaheadIsCancellable() {
        var checks = 0
        val lexer = TypeCodeRoleLexer(PythonCodeHighlighting.profile) { if (++checks == 4) throw CancellationException() }
        assertThrows(CancellationException::class.java) { lexer.start("name" + " ".repeat(20000) + ": str") }
    }

    @Test fun contextualKeywordsAndEscapedNamesCanStillDeclareFields() {
        val cases = mapOf(
            SupportedLanguage.KOTLIN to "data class Root(val data: String, val field: Int)",
            SupportedLanguage.CSHARP to "class Root { string value; string @class; }",
            SupportedLanguage.TYPESCRIPT to "interface Root { readonly: string; class: number; }",
        )
        val expected = mapOf(SupportedLanguage.KOTLIN to listOf("data", "field"),
            SupportedLanguage.CSHARP to listOf("value", "@class"), SupportedLanguage.TYPESCRIPT to listOf("readonly", "class"))
        for ((language, source) in cases) {
            assertEquals(language.name, expected[language], scan(source, language).filter { it.type == TypeCodeToken.FIELD }.map { source.substring(it.start, it.end) })
        }
    }

    @Test fun goInlineFieldsAndKotlinInterpolationKeepTheirRoles() {
        val go = "type Root struct { Name string; Scores []int }"
        assertEquals(listOf("Name", "Scores"), scan(go, SupportedLanguage.GO).filter { it.type == TypeCodeToken.FIELD }.map { go.substring(it.start, it.end) })
        val literal = "\"text \${map[\"field\"]} // still a string\""
        val kotlin = "val text = $literal"
        val tokens = scan(kotlin, SupportedLanguage.KOTLIN)
        assertEquals(listOf(literal), tokens.filter { it.type == TypeCodeToken.STRING }.map { kotlin.substring(it.start, it.end) })
        assertFalse(tokens.any { it.type == TypeCodeToken.LINE_COMMENT })
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
    private fun scan(source: String, language: SupportedLanguage, start: Int = 0, end: Int = source.length, state: Int = 0): List<Token> {
        val lexer = TypeCodeRoleLexer(checkNotNull(TypeCodeHighlighterResolver.profileFor(language.fileExtension))) {}
        lexer.start(source, start, end, state)
        val result = mutableListOf<Token>()
        while (lexer.tokenType != null) {
            result += Token(lexer.tokenStart, lexer.tokenEnd, lexer.state, lexer.tokenType!!)
            assertTrue(result.size <= source.length)
            lexer.advance()
        }
        return result
    }
}
