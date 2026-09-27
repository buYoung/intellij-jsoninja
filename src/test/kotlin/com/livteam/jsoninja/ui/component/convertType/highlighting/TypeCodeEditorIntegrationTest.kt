package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ComponentTreeEventDispatcher
import com.livteam.jsoninja.ui.component.convertType.CodeInputPanel
import com.livteam.jsoninja.ui.component.convertType.CodePreviewPanel
import com.livteam.jsoninja.ui.component.editor.EditorTextFieldFactory
import java.awt.Color
import java.awt.Component
import java.awt.Container

class TypeCodeEditorIntegrationTest : BasePlatformTestCase() {
    fun testSwingFontUpdatesDoNotOverwriteUserEditorScheme() {
        val manager = EditorColorsManager.getInstance()
        val original = manager.globalScheme
        val scheme = original.clone() as EditorColorsScheme
        scheme.editorFontName = "Monospaced"
        val lifetime = Disposer.newDisposable()
        val input = CodeInputPanel(project)
        val preview = CodePreviewPanel(project)
        try {
            manager.setGlobalScheme(scheme)
            input.updateLanguage("py", "")
            input.setText("class Root: name: str")
            preview.setSuccess("class Root: name: str", "py")
            val fields = listOf(findField(input), findField(preview), EditorTextFieldFactory.createCodeField(project, "json", initialText = "{}", shouldApplyEditorColors = true))
            fields.forEach { field ->
                field.setDisposedWith(lifetime)
                val editor = checkNotNull(field.getEditor(true))
                field.font = java.awt.Font("Dialog", java.awt.Font.PLAIN, 24)
                assertEquals("UI font updates must not change the user's scheme", "Monospaced", scheme.editorFontName)
                assertEquals("Code must keep the IDE editor font", "Monospaced", editor.colorsScheme.editorFontName)
            }
            for (panel in listOf(input, preview)) ComponentTreeEventDispatcher.create(panel, EditorColorsListener::class.java).multicaster.globalSchemeChange(null)
            fields.take(2).forEach { field ->
                field.font = java.awt.Font("Dialog", java.awt.Font.BOLD, 19)
                assertEquals("Monospaced", scheme.editorFontName)
                assertEquals("Monospaced", field.editor!!.colorsScheme.editorFontName)
            }
        } finally {
            input.dispose()
            preview.dispose()
            Disposer.dispose(lifetime)
            manager.setGlobalScheme(original)
        }
    }

    fun testOpenInputAndReadOnlyPreviewUseChangedSchemeAttributesWithoutReplacingDocument() {
        val fileTypes = FileTypeManager.getInstance()
        val originalC = fileTypes.getFileTypeByExtension("c")
        WriteCommandAction.runWriteCommandAction(project) { fileTypes.associateExtension(PlainTextFileType.INSTANCE, "c") }
        val input = CodeInputPanel(project)
        val preview = CodePreviewPanel(project)
        val lifetime = Disposer.newDisposable()
        val manager = EditorColorsManager.getInstance()
        val original = manager.globalScheme
        try {
            input.updateLanguage("c", "")
            input.setText("typedef struct Root { int id; }; // note")
            preview.setSuccess("typedef struct Root { int id; };", "c")
            val fields = listOf(findField(input), findField(preview))
            fields.forEach { it.setDisposedWith(lifetime) }
            val editors = fields.map { checkNotNull(it.getEditor(true)) }
            assertFalse(editors[0].isViewer)
            assertTrue(editors[1].isViewer)
            val document = editors[0].document
            val originalText = document.text
            WriteCommandAction.runWriteCommandAction(project, "Edit conversion input", null, {
                document.insertString(document.textLength, "\nint added;")
            })
            editors[0].caretModel.moveToOffset(8)
            editors[0].selectionModel.setSelection(8, 14)
            val content = document.text
            val scheme = original.clone() as EditorColorsScheme
            scheme.name = "JSONinja test scheme"
            manager.setGlobalScheme(scheme)
            for (color in listOf(Color(12, 110, 170), Color(170, 65, 80))) {
                scheme.setAttributes(DefaultLanguageHighlighterColors.KEYWORD, TextAttributes(color, null, null, null, 0))
                // The same public component-tree delivery used by EditorColorsManager, including null for same-scheme Apply.
                ComponentTreeEventDispatcher.create(input, EditorColorsListener::class.java).multicaster.globalSchemeChange(null)
                ComponentTreeEventDispatcher.create(preview, EditorColorsListener::class.java).multicaster.globalSchemeChange(null)
                editors.forEach { editor ->
                    assertEquals(color, editor.highlighter.createIterator(0).textAttributes.foregroundColor)
                    assertEquals(scheme.defaultBackground, editor.backgroundColor)
                }
                assertSame(document, editors[0].document)
                assertEquals(content, document.text)
                assertEquals(8, editors[0].caretModel.offset)
                assertEquals(8, editors[0].selectionModel.selectionStart)
                assertEquals(14, editors[0].selectionModel.selectionEnd)
            }
            val fileEditor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(editors[0])
            val undoManager = com.intellij.openapi.command.undo.UndoManager.getInstance(project)
            assertTrue(undoManager.isUndoAvailable(fileEditor))
            undoManager.undo(fileEditor)
            assertEquals(originalText, document.text)
            assertTrue(undoManager.isRedoAvailable(fileEditor))
            undoManager.redo(fileEditor)
            assertEquals(content, document.text)
            input.dispose()
            preview.dispose()
            Disposer.dispose(lifetime)
            com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
            assertTrue(editors.all { it.isDisposed })
            input.globalSchemeChange(null)
            preview.globalSchemeChange(null)
        } finally {
            input.dispose()
            preview.dispose()
            Disposer.dispose(lifetime)
            manager.setGlobalScheme(original)
            WriteCommandAction.runWriteCommandAction(project) { fileTypes.associateExtension(originalC, "c") }
        }
    }

    fun testEditorIncrementalChangesRecoverAfterClosingComment() {
        val lifetime = Disposer.newDisposable()
        val field = EditorTextFieldFactory.createCodeField(
            project, "c", initialText = "/* open\nint value;", shouldApplyHighlighter = true,
            highlighterProvider = { owner, _, scheme -> TypeCodeHighlighterResolver.create("c", owner, PlainTextFileType.INSTANCE, scheme) },
        )
        try {
            field.setDisposedWith(lifetime)
            val editor = checkNotNull(field.getEditor(true))
            assertEquals(TypeCodeToken.BLOCK_COMMENT, editor.highlighter.createIterator(8).tokenType)
            WriteCommandAction.runWriteCommandAction(project) { field.document.insertString(8, "*/ ") }
            val offset = field.text.indexOf("int")
            assertEquals(TypeCodeToken.KEYWORD, editor.highlighter.createIterator(offset).tokenType)
            WriteCommandAction.runWriteCommandAction(project) { field.document.deleteString(8, 11) }
            assertEquals(TypeCodeToken.BLOCK_COMMENT, editor.highlighter.createIterator(8).tokenType)
        } finally { Disposer.dispose(lifetime) }
    }

    fun testNativeProviderKeysKeepUserOverridesEvenWithMonochromeColors() {
        val type = FileTypeManager.getInstance().getFileTypeByExtension("kt")
        val native = checkNotNull(SyntaxHighlighterFactory.getSyntaxHighlighter(type, project, null))
        val probe = KotlinCodeHighlighting.profile.nativeProbe
        assertTrue(TypeCodeHighlighterResolver.hasLexicalTokens(native, probe))
        val lexer = native.highlightingLexer
        lexer.start(probe)
        val nativeType = checkNotNull(lexer.tokenType)
        val keys = native.getTokenHighlights(nativeType)
        assertTrue(keys.isNotEmpty())
        val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        val monochrome = Color(91, 91, 91)
        keys.forEach { scheme.setAttributes(it, TextAttributes(monochrome, null, null, null, 0)) }
        val selected = TypeCodeHighlighterResolver.create("kt", project, type, scheme)
        selected.setText(probe)
        val token = selected.createIterator(0)
        assertEquals(nativeType, token.tokenType)
        assertEquals(monochrome, token.textAttributes.foregroundColor)
    }

    fun testCallerCanDisableHighlightingAndWarningsNeverEnterViewerText() {
        val lifetime = Disposer.newDisposable()
        val field = EditorTextFieldFactory.createCodeField(project, "c", initialText = "int value;",
            shouldApplyHighlighter = false, highlighterProvider = { _, _, _ -> error("disabled callback invoked") })
        val preview = CodePreviewPanel(project)
        try {
            field.setDisposedWith(lifetime)
            assertNotNull(field.getEditor(true))
            preview.setSuccess("{\"value\":null}", "json", listOf("Unknown type <b>is plain text</b>"))
            assertEquals("{\"value\":null}", findField(preview).text)
            assertEquals("Unknown type <b>is plain text</b>", descendants(preview).filterIsInstance<JBTextArea>().single().text)
            preview.setLoading()
            assertEquals("", descendants(preview).filterIsInstance<JBTextArea>().single().text)
            preview.setSuccess("{}", "json")
            assertEquals("{}", findField(preview).text)
            assertNull(TypeCodeHighlighterResolver.profileFor("json"))
            assertNotNull(TypeCodeHighlighterResolver.profileFor("kt"))
        } finally {
            preview.dispose()
            Disposer.dispose(lifetime)
        }
    }

    fun testAllElevenLanguagePanelsApplyUserColorsWithoutNativeLanguageSupport() {
        val manager = EditorColorsManager.getInstance()
        val original = manager.globalScheme
        val fileTypes = FileTypeManager.getInstance()
        for (language in com.livteam.jsoninja.model.SupportedLanguage.entries) {
            val extension = language.fileExtension
            val previousType = fileTypes.getFileTypeByExtension(extension)
            val source = checkNotNull(javaClass.getResource("/typeConversion/golden/object.$extension")).readText()
            val lifetime = Disposer.newDisposable()
            val input = CodeInputPanel(project)
            val preview = CodePreviewPanel(project)
            try {
                WriteCommandAction.runWriteCommandAction(project) { fileTypes.associateExtension(PlainTextFileType.INSTANCE, extension) }
                input.updateLanguage(extension, "")
                input.setText(source)
                preview.setSuccess(source, extension)
                val fields = listOf(findField(input), findField(preview))
                fields.forEach { it.setDisposedWith(lifetime) }
                val editors = fields.map { checkNotNull(it.getEditor(true)) }
                editors.forEach { assertNull(com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(it.document)) }
                for ((iteration, background) in listOf(Color(245, 245, 245), Color(35, 35, 35)).withIndex()) {
                    val scheme = original.clone() as EditorColorsScheme
                    scheme.name = "JSONinja ${language.name} $iteration"
                    scheme.setAttributes(com.intellij.openapi.editor.HighlighterColors.TEXT, TextAttributes(Color.GRAY, background, null, null, 0))
                    val custom = Color(60 + iteration * 70, 110, 150)
                    val keys = listOf(DefaultLanguageHighlighterColors.KEYWORD, DefaultLanguageHighlighterColors.IDENTIFIER,
                        DefaultLanguageHighlighterColors.STRING, DefaultLanguageHighlighterColors.NUMBER,
                        DefaultLanguageHighlighterColors.DOC_COMMENT, DefaultLanguageHighlighterColors.DOC_COMMENT_TAG,
                        DefaultLanguageHighlighterColors.DOC_COMMENT_TAG_VALUE, DefaultLanguageHighlighterColors.LINE_COMMENT,
                        DefaultLanguageHighlighterColors.CLASS_NAME, DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL,
                        DefaultLanguageHighlighterColors.INSTANCE_FIELD, DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
                    keys.forEach { scheme.setAttributes(it, TextAttributes(custom, null, null, null, 0)) }
                    manager.setGlobalScheme(scheme)
                    for (panel in listOf(input, preview)) ComponentTreeEventDispatcher.create(panel, EditorColorsListener::class.java).multicaster.globalSchemeChange(null)
                    for (editor in editors) {
                        assertEquals(source, editor.document.text)
                        assertEquals(background, editor.backgroundColor)
                        val iterator = editor.highlighter.createIterator(0)
                        var colored = 0
                        val seenRoles = mutableSetOf<TypeCodeToken>()
                        while (!iterator.atEnd()) {
                            val token = iterator.tokenType as? TypeCodeToken
                            token?.let(seenRoles::add)
                            if (token?.keys?.any { it in keys } == true) {
                                assertEquals(language.name, custom, iterator.textAttributes.foregroundColor)
                                colored++
                            }
                            iterator.advance()
                        }
                        assertTrue(language.name, colored > 0)
                        assertTrue(language.name, TypeCodeToken.TYPE_NAME in seenRoles)
                        assertTrue(language.name, TypeCodeToken.FIELD in seenRoles)
                    }
                }
            } finally {
                input.dispose(); preview.dispose(); Disposer.dispose(lifetime)
                com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
                manager.setGlobalScheme(original)
                WriteCommandAction.runWriteCommandAction(project) { fileTypes.associateExtension(previousType, extension) }
            }
        }
    }

    fun testGenericKeywordTablesCannotReplaceTheBundledDeclarationHighlighter() {
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension("c")
        val source = "struct Root { int id; };"
        val highlighter = TypeCodeHighlighterResolver.create("c", project, fileType, EditorColorsManager.getInstance().globalScheme)
        highlighter.setText(source)
        assertSame(TypeCodeToken.TYPE_NAME, highlighter.createIterator(source.indexOf("Root")).tokenType)
        assertSame(TypeCodeToken.FIELD, highlighter.createIterator(source.indexOf("id")).tokenType)
    }

    fun testIncrementalLiteralAndDocumentationClosuresRecoverForEveryFallback() {
        val cases = listOf(
            Triple("c", "/* unfinished", "*/"), Triple("cpp", "R\"tag(unfinished", ")tag\""),
            Triple("cs", "@\"unfinished", "\""), Triple("py", "'''unfinished", "'''"),
            Triple("rs", "r##\"unfinished", "\"##"), Triple("scala", "\"\"\"unfinished", "\"\"\""), Triple("js", "/** unfinished", "*/"),
            Triple("kt", "\"\"\"unfinished", "\"\"\""), Triple("java", "\"\"\"unfinished", "\"\"\""),
            Triple("ts", "`unfinished", "`"), Triple("go", "`unfinished", "`"),
        )
        for ((extension, opening, closing) in cases) {
            val lifetime = Disposer.newDisposable()
            val keyword = if (extension in setOf("c", "cpp", "rs", "go")) "struct" else "class"
            val field = EditorTextFieldFactory.createCodeField(project, extension, initialText = "$opening\n$keyword Next", shouldApplyHighlighter = true,
                highlighterProvider = { owner, _, scheme -> TypeCodeHighlighterResolver.create(extension, owner, PlainTextFileType.INSTANCE, scheme) })
            try {
                field.setDisposedWith(lifetime)
                val editor = checkNotNull(field.getEditor(true))
                val offset = opening.length
                WriteCommandAction.runWriteCommandAction(project) { field.document.insertString(offset, closing) }
                assertEquals(extension, TypeCodeToken.KEYWORD, editor.highlighter.createIterator(field.text.indexOf(keyword)).tokenType)
                WriteCommandAction.runWriteCommandAction(project) { field.document.deleteString(offset, offset + closing.length) }
                assertTrue(extension, editor.highlighter.createIterator(field.text.indexOf(keyword)).tokenType != TypeCodeToken.KEYWORD)
            } finally { Disposer.dispose(lifetime); com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents() }
        }
    }

    fun testDeclarationRolesRecoverWhenLookaheadOrEarlierKeywordsAreEdited() {
        val lifetime = Disposer.newDisposable()
        val field = EditorTextFieldFactory.createCodeField(project, "py", initialText = "value      str\nclass Root:\n    field: int", shouldApplyHighlighter = true,
            highlighterProvider = { owner, _, scheme -> TypeCodeHighlighterResolver.create("py", owner, PlainTextFileType.INSTANCE, scheme) })
        try {
            field.setDisposedWith(lifetime)
            val editor = checkNotNull(field.getEditor(true))
            assertSame(TypeCodeToken.IDENTIFIER, editor.highlighter.createIterator(0).tokenType)
            WriteCommandAction.runWriteCommandAction(project) { field.document.insertString(5, ":") }
            assertSame(TypeCodeToken.FIELD, editor.highlighter.createIterator(0).tokenType)
            WriteCommandAction.runWriteCommandAction(project) { field.document.deleteString(5, 6) }
            assertSame(TypeCodeToken.IDENTIFIER, editor.highlighter.createIterator(0).tokenType)
            val classStart = field.text.indexOf("class")
            WriteCommandAction.runWriteCommandAction(project) { field.document.insertString(classStart, "# ") }
            assertSame(TypeCodeToken.LINE_COMMENT, editor.highlighter.createIterator(field.text.indexOf("Root")).tokenType)
            assertSame(TypeCodeToken.FIELD, editor.highlighter.createIterator(field.text.indexOf("field")).tokenType)
            WriteCommandAction.runWriteCommandAction(project) { field.document.deleteString(classStart, classStart + 2) }
            assertSame(TypeCodeToken.TYPE_NAME, editor.highlighter.createIterator(field.text.indexOf("Root")).tokenType)
        } finally { Disposer.dispose(lifetime); com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents() }
    }

    private fun findField(root: Component): EditorTextField = descendants(root).filterIsInstance<EditorTextField>().single()
    private fun descendants(root: Component): List<Component> = listOf(root) +
        if (root is Container) root.components.flatMap(::descendants) else emptyList()
}
