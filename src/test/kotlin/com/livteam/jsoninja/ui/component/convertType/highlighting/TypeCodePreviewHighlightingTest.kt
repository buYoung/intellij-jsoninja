package com.livteam.jsoninja.ui.component.convertType.highlighting

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import com.intellij.util.ComponentTreeEventDispatcher
import com.livteam.jsoninja.ui.component.convertType.CodePreviewPanel
import com.livteam.jsoninja.ui.component.editor.EditorTextFieldFactory
import java.awt.Color
import java.awt.Component
import java.awt.Container

class TypeCodePreviewHighlightingTest : BasePlatformTestCase() {
    fun testKotlinPreviewEnablesNativeHighlightingWhileRemainingReadOnly() {
        val source = """
            data class Root(
                val estDolor: Any?,
                val options: List<String>,
                val permission: String,
                val total: Boolean,
                val url: Any?
            )
        """.trimIndent()
        val lifetime = Disposer.newDisposable()
        val preview = CodePreviewPanel(project)
        val colors = EditorColorsManager.getInstance()
        val originalScheme = colors.globalScheme
        try {
            preview.setSuccess(source, "kt")
            val field = descendants(preview).filterIsInstance<EditorTextField>().single()
            field.setDisposedWith(lifetime)
            val editor = checkNotNull(field.getEditor(true))
            assertTrue(editor.isViewer)
            val psi = checkNotNull(PsiDocumentManager.getInstance(project).getPsiFile(editor.document))
            assertEquals("kotlin", psi.language.id)
            assertTrue("Read-only previews must retain native PSI highlighting", DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(psi))
            assertFalse("Generated previews do not need inspections", HighlightingLevelManager.getInstance(project).shouldInspect(psi))
            // This fixture verifies native keys and filtering. Activation in the modal dialog,
            // including language switches, is checked separately by TypeConversionRobotTest.
            myFixture.configureFromExistingVirtualFile(psi.virtualFile)
            assertSame(editor.document, myFixture.editor.document)
            val highlights = myFixture.doHighlighting()
            assertTrue(highlights.none { it.severity >= HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING })
            val markup = editor.filteredDocumentMarkupModel.allHighlighters.sortedBy { it.startOffset }
            val snapshot = markup.joinToString("\n") {
                "${it.startOffset}..${it.endOffset} ${source.substring(it.startOffset, it.endOffset)} ${it.textAttributesKey?.externalName}"
            }
            val expected = checkNotNull(javaClass.getResource("/typeConversion/highlighting/kotlin-preview.txt")).readText().trimEnd()
            assertEquals(expected, snapshot)
            editor.selectionModel.setSelection(11, 15)
            for ((index, background) in listOf(Color(245, 245, 245), Color(35, 35, 35)).withIndex()) {
                val scheme = originalScheme.clone() as EditorColorsScheme
                scheme.name = "JSONinja native Kotlin preview $index"
                scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(Color.GRAY, background, null, null, 0))
                colors.setGlobalScheme(scheme)
                for (custom in listOf(Color(30, 120, 190), Color(180, 60, 110))) {
                    markup.map { checkNotNull(it.textAttributesKey) }.distinct().forEach { key ->
                        scheme.setAttributes(key, TextAttributes(custom, null, null, null, 0))
                    }
                    ComponentTreeEventDispatcher.create(preview, EditorColorsListener::class.java).multicaster.globalSchemeChange(null)
                    markup.forEach { assertEquals(custom, it.getTextAttributes(editor.colorsScheme)?.foregroundColor) }
                    assertEquals(background, editor.backgroundColor)
                    assertSame(field.document, editor.document)
                    assertTrue(editor.isViewer)
                    assertEquals(11, editor.selectionModel.selectionStart)
                    assertEquals(15, editor.selectionModel.selectionEnd)
                }
            }
            assertEquals(source, field.text)
            val updated = "data class Changed(val name: String)"
            preview.setSuccess(updated, "kt")
            assertSame(field, descendants(preview).filterIsInstance<EditorTextField>().single())
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            myFixture.doHighlighting()
            val updatedMarkup = editor.filteredDocumentMarkupModel.allHighlighters
            assertTrue(updatedMarkup.any { it.startOffset == updated.indexOf("name") && it.textAttributesKey?.externalName == "KOTLIN_INSTANCE_PROPERTY" })
            assertTrue(updatedMarkup.all { it.endOffset <= updated.length })
        } finally {
            preview.dispose()
            Disposer.dispose(lifetime)
            UIUtil.dispatchAllInvocationEvents()
            colors.setGlobalScheme(originalScheme)
        }
    }

    fun testJavaPreviewDoesNotShowMissingContextDiagnostics() {
        val lifetime = Disposer.newDisposable()
        val preview = CodePreviewPanel(project)
        try {
            preview.setSuccess("public class Root { private MissingType value; }", "java")
            val field = descendants(preview).filterIsInstance<EditorTextField>().single()
            field.setDisposedWith(lifetime)
            val editor = checkNotNull(field.getEditor(true))
            val psi = checkNotNull(PsiDocumentManager.getInstance(project).getPsiFile(editor.document))
            assertEquals("JAVA", psi.language.id)
            myFixture.configureFromExistingVirtualFile(psi.virtualFile)
            val highlights = myFixture.doHighlighting()
            assertTrue("Preview fragments have no compilation context: $highlights", highlights.none { it.severity >= HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING })
            assertTrue(editor.filteredDocumentMarkupModel.allHighlighters.any {
                it.startOffset == field.text.indexOf("Root") && it.textAttributesKey != null
            })
            myFixture.configureByText("Regular.java", "class Regular { private MissingType value; }")
            assertTrue("Regular project editors must retain diagnostics", myFixture.doHighlighting().any { it.severity == HighlightSeverity.ERROR })
        } finally {
            preview.dispose()
            Disposer.dispose(lifetime)
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    fun testPreviewPolicyDoesNotChangeOtherFieldsOrJsonPreview() {
        val lifetime = Disposer.newDisposable()
        val preview = CodePreviewPanel(project)
        try {
            val regularViewer = EditorTextFieldFactory.createCodeField(project, "kt", initialText = "class Regular", isViewer = true)
            regularViewer.setDisposedWith(lifetime)
            regularViewer.getEditor(true)
            val regularPsi = checkNotNull(PsiDocumentManager.getInstance(project).getPsiFile(regularViewer.document))
            assertFalse(DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(regularPsi))
            assertNull(TypeCodePreviewHighlightingSettingProvider().getDefaultSetting(project, regularPsi.virtualFile))
            preview.setSuccess("{}", "json")
            val jsonViewer = descendants(preview).filterIsInstance<EditorTextField>().single()
            jsonViewer.setDisposedWith(lifetime)
            jsonViewer.getEditor(true)
            val jsonPsi = checkNotNull(PsiDocumentManager.getInstance(project).getPsiFile(jsonViewer.document))
            assertNull(TypeCodePreviewHighlightingSettingProvider().getDefaultSetting(project, jsonPsi.virtualFile))
            assertFalse(DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(jsonPsi))
        } finally {
            preview.dispose()
            Disposer.dispose(lifetime)
            UIUtil.dispatchAllInvocationEvents()
        }
    }

    private fun descendants(root: Component): List<Component> = listOf(root) +
        if (root is Container) root.components.flatMap(::descendants) else emptyList()
}
