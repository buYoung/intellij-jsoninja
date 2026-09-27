package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import java.util.concurrent.CancellationException

internal object TypeCodeHighlighterResolver {
    fun create(
        requestedExtension: String,
        project: Project,
        fileType: FileType,
        scheme: EditorColorsScheme,
    ): EditorHighlighter {
        val profile = profileFor(requestedExtension)
            ?: return HighlighterFactory.createHighlighter(fileType, scheme, project)
        return try {
            val native = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, null)
            // Generic keyword tables cannot supply declaration/type/field roles. Prefer a real
            // language provider when installed; otherwise the bundled lexer covers every language.
            val hasLanguageSupport = fileType is LanguageFileType &&
                LanguageParserDefinitions.INSTANCE.forLanguage(fileType.language) != null
            if (hasLanguageSupport && native != null && hasLexicalTokens(native, profile.nativeProbe) &&
                (profile.nativeStringProbe == null || supportsStringProbe(native, profile.nativeStringProbe)) &&
                (!profile.hasDocumentationTags || supportsDocumentation(native))) {
                EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, scheme, project)
            } else {
                EditorHighlighterFactory.getInstance().createEditorHighlighter(TypeCodeSyntaxHighlighter(profile), scheme)
            }
        } catch (cancelled: ProcessCanceledException) {
            throw cancelled
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            logger<TypeCodeHighlighterResolver>().warn("Cannot create conversion highlighter for $requestedExtension", exception)
            HighlighterFactory.createHighlighter(PlainTextFileType.INSTANCE, scheme, project)
        }
    }

    fun profileFor(extension: String): TypeCodeProfile? = when (extension.lowercase(java.util.Locale.ROOT)) {
        "kt" -> KotlinCodeHighlighting.profile
        "java" -> JavaCodeHighlighting.profile
        "ts" -> TypeScriptCodeHighlighting.profile
        "go" -> GoCodeHighlighting.profile
        "c" -> CCodeHighlighting.profile
        "cpp" -> CppCodeHighlighting.profile
        "cs" -> CSharpCodeHighlighting.profile
        "py" -> PythonCodeHighlighting.profile
        "rs" -> RustCodeHighlighting.profile
        "scala" -> ScalaCodeHighlighting.profile
        "js" -> JSDocCodeHighlighting.profile
        else -> null
    }

    internal fun hasLexicalTokens(highlighter: SyntaxHighlighter, probe: String): Boolean {
        val lexer = highlighter.highlightingLexer
        lexer.start(probe)
        while (lexer.tokenType != null) {
            if (highlighter.getTokenHighlights(lexer.tokenType!!).any { it != HighlighterColors.TEXT }) return true
            val previousEnd = lexer.tokenEnd
            lexer.advance()
            if (lexer.tokenType != null && lexer.tokenEnd <= previousEnd) return false
        }
        return false
    }

    private fun supportsStringProbe(highlighter: SyntaxHighlighter, probe: String): Boolean {
        val lexer = highlighter.highlightingLexer
        lexer.start(probe)
        val stringKeys = highlighter.getTokenHighlights(lexer.tokenType ?: return false).toSet()
        val markerOffset = probe.indexOf("JSONINJA_LITERAL")
        while (lexer.tokenType != null && lexer.tokenEnd <= markerOffset) {
            val previousEnd = lexer.tokenEnd
            lexer.advance()
            if (lexer.tokenType != null && lexer.tokenEnd <= previousEnd) return false
        }
        return lexer.tokenType?.let { type -> highlighter.getTokenHighlights(type).any { it in stringKeys } } == true
    }

    internal fun supportsDocumentation(highlighter: SyntaxHighlighter): Boolean {
        val probe = "/** description\n * @typedef {string} JsoninjaProbe\n * @property {?number} [count=1]\n */"
        val offsets = listOf(probe.indexOf("description"), probe.indexOf("@typedef") + 1, probe.indexOf("string"), probe.indexOf("@property") + 1)
        val keys = arrayOfNulls<Set<com.intellij.openapi.editor.colors.TextAttributesKey>>(offsets.size)
        val lexer = highlighter.highlightingLexer
        lexer.start(probe)
        while (lexer.tokenType != null) {
            offsets.forEachIndexed { index, offset ->
                if (offset >= lexer.tokenStart && offset < lexer.tokenEnd) keys[index] = highlighter.getTokenHighlights(lexer.tokenType!!).toSet()
            }
            val previousEnd = lexer.tokenEnd
            lexer.advance()
            if (lexer.tokenType != null && lexer.tokenEnd <= previousEnd) return false
        }
        return keys.all { !it.isNullOrEmpty() } && keys[0] != keys[1] && keys[0] != keys[2] && keys[0] != keys[3]
    }

    fun refresh(field: EditorTextField?, extension: String, project: Project) {
        // EditorColorsListener's component-tree delivery covers switching and same-scheme Apply.
        val editor = field?.editor as? EditorEx ?: return
        if (editor.isDisposed || project.isDisposed) return
        val scheme = EditorColorsManager.getInstance().globalScheme
        editor.colorsScheme = editor.createBoundColorSchemeDelegate(scheme)
        editor.backgroundColor = scheme.defaultBackground
        editor.highlighter = create(extension, project, field.fileType, editor.colorsScheme)
    }
}
