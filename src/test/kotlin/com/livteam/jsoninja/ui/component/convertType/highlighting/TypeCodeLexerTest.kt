package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class TypeCodeLexerTest {
    private data class Token(val start: Int, val end: Int, val state: Int, val type: IElementType)

    @Test fun cLexicalCategoriesMatchGoldenTokens() {
        val source = "#include <stdint.h>\ntypedef struct User { int id; char *name; }; // hi\nL\"문자 😀 /*literal*/\" 'x' 12.5e-2 /* unfinished"
        val actual = scan(source).filter { it.type != TokenType.WHITE_SPACE }.map { source.substring(it.start, it.end) to it.type }
        assertEquals(listOf(
            "#include" to TypeCodeToken.METADATA, "<stdint.h>" to TypeCodeToken.STRING,
            "typedef" to TypeCodeToken.KEYWORD, "struct" to TypeCodeToken.KEYWORD, "User" to TypeCodeToken.IDENTIFIER,
            "{" to TypeCodeToken.BRACES, "int" to TypeCodeToken.KEYWORD, "id" to TypeCodeToken.IDENTIFIER,
            ";" to TypeCodeToken.SEMICOLON, "char" to TypeCodeToken.KEYWORD, "*" to TypeCodeToken.OPERATOR,
            "name" to TypeCodeToken.IDENTIFIER, ";" to TypeCodeToken.SEMICOLON, "}" to TypeCodeToken.BRACES,
            ";" to TypeCodeToken.SEMICOLON, "// hi" to TypeCodeToken.LINE_COMMENT,
            "L\"문자 😀 /*literal*/\"" to TypeCodeToken.STRING, "'x'" to TypeCodeToken.STRING,
            "12.5e-2" to TypeCodeToken.NUMBER, "/* unfinished" to TypeCodeToken.BLOCK_COMMENT,
        ), actual)
    }

    @Test fun freshLexerRestartsMatchFullScanAtEveryTokenBoundary() {
        val source = "#include <x.h>\nconst char *x = u8\"é😀\\\"//\";\n/* multi\nline */ int n; //continued\\\ncomment\n"
        val expected = scan(source)
        expected.forEachIndexed { index, token ->
            assertEquals("restart at ${token.start}", expected.drop(index), scan(source, token.start, source.length, token.state))
        }
    }

    @Test fun everyTruncatedInputConsumesExactlyTheSuppliedUtf16Range() {
        val source = "#include <missing\nchar *s=\"😀\\\ncontinued\"; /* open\n comment */ int a[2];"
        for (end in 0..source.length) {
            val tokens = scan(source, end = end)
            assertEquals(end, tokens.lastOrNull()?.end ?: 0)
            assertEquals(0, tokens.firstOrNull()?.start ?: 0)
            tokens.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
            assertTrue(tokens.all { it.end > it.start && it.end <= end })
        }
    }

    @Test fun fallbackReturnsExistingKeysAndCreatesIndependentLexers() {
        val highlighter = TypeCodeSyntaxHighlighter(CCodeHighlighting.profile)
        assertSame(DefaultLanguageHighlighterColors.KEYWORD, highlighter.getTokenHighlights(TypeCodeToken.KEYWORD).single())
        assertSame(DefaultLanguageHighlighterColors.STRING, highlighter.getTokenHighlights(TypeCodeToken.STRING).single())
        assertSame(DefaultLanguageHighlighterColors.METADATA, highlighter.getTokenHighlights(TypeCodeToken.METADATA).single())
        assertNotSame(highlighter.highlightingLexer, highlighter.highlightingLexer)
        assertSame(highlighter.getTokenHighlights(TypeCodeToken.NUMBER), highlighter.getTokenHighlights(TypeCodeToken.NUMBER))
    }

    @Test fun longUnfinishedTokensObserveCancellation() {
        var checks = 0
        val lexer = TypeCodeLexer(CCodeHighlighting.profile) { if (++checks == 3) throw CancellationException("cancelled scan") }
        assertThrows(CancellationException::class.java) { lexer.start("/*" + "x".repeat(20000)) }
        assertEquals(3, checks)
    }

    @Test fun cppRawStringsKeepQuotesCommentsAndWrongDelimitersInsideTheLiteral() {
        val raw = "u8R\"tag(first \" // comment\n)wrong\" /* still literal */\n)tag\""
        val source = "auto value = $raw; int count = 1'000;"
        val tokens = scan(source, profile = CppCodeHighlighting.profile)
        assertEquals(listOf(raw), tokens.filter { it.type == TypeCodeToken.STRING }.map { source.substring(it.start, it.end) })
        assertFalse(tokens.any { it.type == TypeCodeToken.LINE_COMMENT || it.type == TypeCodeToken.BLOCK_COMMENT })
        assertEquals("1'000", tokens.single { it.type == TypeCodeToken.NUMBER }.let { source.substring(it.start, it.end) })
        tokens.forEachIndexed { index, token ->
            assertEquals(tokens.drop(index), scan(source, token.start, source.length, token.state, CppCodeHighlighting.profile))
        }
        for (end in 0..source.length) {
            assertEquals(end, scan(source, end = end, profile = CppCodeHighlighting.profile).lastOrNull()?.end ?: 0)
        }
    }

    @Test fun csharpVerbatimInterpolationRawStringsAndEscapedNamesStayBounded() {
        val literals = listOf("@\"first\n\"\" /* text */\"", "\$\"{map[\"key\"]} text\"", "\"\"\"raw \" // text\nend\"\"\"")
        val source = "/// documentation\nstring @class; " + literals.joinToString("; ") + "; // done\\\nint next;"
        val tokens = scan(source, profile = CSharpCodeHighlighting.profile)
        assertEquals(literals, tokens.filter { it.type == TypeCodeToken.STRING }.map { source.substring(it.start, it.end) })
        assertEquals(TypeCodeToken.DOC_COMMENT, tokens.first().type)
        assertEquals(TypeCodeToken.IDENTIFIER, tokens.single { source.substring(it.start, it.end) == "@class" }.type)
        assertEquals(TypeCodeToken.KEYWORD, tokens.single { source.substring(it.start, it.end) == "int" }.type)
        tokens.forEachIndexed { index, token ->
            assertEquals(tokens.drop(index), scan(source, token.start, source.length, token.state, CSharpCodeHighlighting.profile))
        }
        for (end in 0..source.length) assertEquals(end, scan(source, end = end, profile = CSharpCodeHighlighting.profile).lastOrNull()?.end ?: 0)
    }

    @Test fun pythonPrefixesTripleStringsDecoratorsAndFloorDivisionHaveIndependentTokens() {
        val literals = listOf("rf\"\"\"first {value}\n# literal 😀\"\"\"", "b'bytes'", "r'raw\\path'")
        val source = "@dataclass\nclass Root: # note\n    value = " + literals.joinToString("; ") + "; count = 9 // 2\nmatch = case"
        val tokens = scan(source, profile = PythonCodeHighlighting.profile)
        assertEquals(literals, tokens.filter { it.type == TypeCodeToken.STRING }.map { source.substring(it.start, it.end) })
        assertEquals(TypeCodeToken.METADATA, tokens.first().type)
        assertEquals(1, tokens.count { it.type == TypeCodeToken.LINE_COMMENT })
        assertEquals(2, tokens.count { source.substring(it.start, it.end) in setOf("match", "case") && it.type == TypeCodeToken.IDENTIFIER })
        tokens.forEachIndexed { index, token ->
            assertEquals(tokens.drop(index), scan(source, token.start, source.length, token.state, PythonCodeHighlighting.profile))
        }
        for (end in 0..source.length) assertEquals(end, scan(source, end = end, profile = PythonCodeHighlighting.profile).lastOrNull()?.end ?: 0)
        var checks = 0
        val lexer = TypeCodeLexer(PythonCodeHighlighting.profile) { if (++checks == 3) throw CancellationException() }
        assertThrows(CancellationException::class.java) { lexer.start("\"\"\"" + "x".repeat(20000)) }
    }

    @Test fun rustRawDelimitersLifetimesAttributesAndNestedCommentsRestartCorrectly() {
        val raw = "br##\"first \"# /* string */ 😀\nlast\"##"
        val source = "#[derive(Debug)]\npub struct Root<'a> { r#type: &'a str } 'x' " + raw + " /* outer /* inner */ tail */ fn next() {}"
        val tokens = scan(source, profile = RustCodeHighlighting.profile)
        assertEquals(listOf("'x'", raw), tokens.filter { it.type == TypeCodeToken.STRING }.map { source.substring(it.start, it.end) })
        assertEquals(2, tokens.count { source.substring(it.start, it.end) == "'a" && it.type == TypeCodeToken.IDENTIFIER })
        assertEquals("/* outer /* inner */ tail */", tokens.single { it.type == TypeCodeToken.BLOCK_COMMENT }.let { source.substring(it.start, it.end) })
        assertEquals(TypeCodeToken.METADATA, tokens.first().type)
        tokens.forEachIndexed { index, token -> assertEquals(tokens.drop(index), scan(source, token.start, source.length, token.state, RustCodeHighlighting.profile)) }
        for (end in 0..source.length) assertEquals(end, scan(source, end = end, profile = RustCodeHighlighting.profile).lastOrNull()?.end ?: 0)
    }

    @Test fun scalaTripleInterpolationBackticksAndNestedCommentsRemainIndependent() {
        val literal = "s\"\"\"first \${map[\"key\"]} /* literal */\nlast\"\"\""
        val source = "@deprecated\ncase class Root(`type`: String) /* outer /* inner */ tail */ val s = " + literal
        val tokens = scan(source, profile = ScalaCodeHighlighting.profile)
        assertEquals(listOf(literal), tokens.filter { it.type == TypeCodeToken.STRING }.map { source.substring(it.start, it.end) })
        assertEquals(TypeCodeToken.IDENTIFIER, tokens.single { source.substring(it.start, it.end) == "`type`" }.type)
        assertEquals(1, tokens.count { it.type == TypeCodeToken.BLOCK_COMMENT })
        tokens.forEachIndexed { index, token -> assertEquals(tokens.drop(index), scan(source, token.start, source.length, token.state, ScalaCodeHighlighting.profile)) }
        for (end in 0..source.length) assertEquals(end, scan(source, end = end, profile = ScalaCodeHighlighting.profile).lastOrNull()?.end ?: 0)
    }

    @Test fun jsdocTagsPayloadsAndDefaultLiteralsKeepRestartStateWithoutColoringStringLookalikes() {
        val quoted = "\"/** @typedef {string} Fake */\""
        val template = "`/** @property {number} fake */`"
        val source = "const fake = $quoted; const t = $template;\n/**\n * description 😀\n * @typedef {Object} Root\n * @property {?number} [count=12]\n * @property {string} [name=\"Ada\"]\n */\nconst next = 1;"
        val tokens = scan(source, profile = JSDocCodeHighlighting.profile)
        assertEquals(listOf("@typedef", "@property", "@property"), tokens.filter { it.type == TypeCodeToken.DOC_TAG }.map { source.substring(it.start, it.end) })
        assertEquals(listOf(quoted, template, "\"Ada\""), tokens.filter { it.type == TypeCodeToken.STRING }.map { source.substring(it.start, it.end) })
        assertTrue(tokens.any { it.type == TypeCodeToken.DOC_VALUE })
        assertTrue(tokens.any { it.state != 0 })
        assertTrue(TypeCodeHighlighterResolver.supportsDocumentation(TypeCodeSyntaxHighlighter(JSDocCodeHighlighting.profile)))
        assertFalse(TypeCodeHighlighterResolver.supportsDocumentation(TypeCodeSyntaxHighlighter(CCodeHighlighting.profile)))
        tokens.forEachIndexed { index, token -> assertEquals(tokens.drop(index), scan(source, token.start, source.length, token.state, JSDocCodeHighlighting.profile)) }
        for (end in 0..source.length) assertEquals(end, scan(source, end = end, profile = JSDocCodeHighlighting.profile).lastOrNull()?.end ?: 0)
        val highlighter = TypeCodeSyntaxHighlighter(JSDocCodeHighlighting.profile)
        assertSame(DefaultLanguageHighlighterColors.DOC_COMMENT_TAG, highlighter.getTokenHighlights(TypeCodeToken.DOC_TAG).single())
        assertSame(DefaultLanguageHighlighterColors.DOC_COMMENT_TAG_VALUE, highlighter.getTokenHighlights(TypeCodeToken.DOC_VALUE).single())
    }

    private fun scan(source: String, start: Int = 0, end: Int = source.length, state: Int = 0, profile: TypeCodeProfile = CCodeHighlighting.profile): List<Token> {
        val lexer = TypeCodeLexer(profile) {}
        lexer.start(source, start, end, state)
        val result = mutableListOf<Token>()
        while (lexer.tokenType != null) {
            assertTrue("non-progressing lexer", result.size <= source.length)
            result += Token(lexer.tokenStart, lexer.tokenEnd, lexer.state, lexer.tokenType!!)
            lexer.advance()
        }
        return result
    }
}
