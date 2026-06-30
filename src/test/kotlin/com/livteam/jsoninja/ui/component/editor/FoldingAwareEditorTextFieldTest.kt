package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil

class FoldingAwareEditorTextFieldTest : BasePlatformTestCase() {
    private val editors = mutableListOf<Editor>()
    private val disposables = mutableListOf<Disposable>()

    override fun tearDown() {
        try {
            runInEdtAndWait {
                editors.forEach(EditorFactory.getInstance()::releaseEditor)
                editors.clear()
                disposables.forEach(Disposer::dispose)
                disposables.clear()
            }
        } finally {
            super.tearDown()
        }
    }

    fun testJsonEditorCreatesFoldRegionsForMultilineJson() {
        val editor = createJsonEditor(
            """
            {
              "user": {
                "name": "JSONinja",
                "roles": [
                  "formatter",
                  "query"
                ]
              }
            }
            """.trimIndent(),
        )

        refreshFoldRegionsSynchronously(editor)

        val foldRegions = editor.foldingModel.allFoldRegions
        assertTrue(
            "Expected at least one fold region for multiline JSON",
            foldRegions.isNotEmpty(),
        )
        assertTrue(
            "Expected a fold region covering nested JSON content",
            foldRegions.any { it.endOffset - it.startOffset > 1 },
        )
        assertTrue(
            "Expected expand/collapse toggle action to have a fold target",
            hasToggleableFoldRegion(editor),
        )
    }

    fun testJsonEditorDoesNotCreateFoldRegionsForSingleLineJson() {
        val editor = createJsonEditor("""{"name":"JSONinja"}""")

        refreshFoldRegionsSynchronously(editor)

        assertEmpty(editor.foldingModel.allFoldRegions.toList())
        assertFalse(
            "Expected expand/collapse toggle action guard to no-op without fold regions",
            hasToggleableFoldRegion(editor),
        )
    }

    fun testJsonEditorTextFieldCreatesFoldRegionsAfterEditorCreation() {
        val editorTextField = EditorTextFieldFactory.createJsonField(
            project = project,
            initialText = """
            {
              "user": {
                "name": "JSONinja",
                "roles": [
                  "formatter",
                  "query"
                ]
              }
            }
            """.trimIndent(),
        )
        val disposable = Disposer.newDisposable("JSONinja folding text field test")
        disposables.add(disposable)

        val editor = createEditorTextFieldEditor(editorTextField, disposable)

        waitForFoldRegions(editor)

        assertTrue(
            "Expected EditorTextField-backed JSON editor to create fold regions",
            editor.foldingModel.allFoldRegions.isNotEmpty(),
        )
    }

    private fun createJsonEditor(initialText: String): EditorEx {
        val document = JsonDocumentFactory.createJsonDocument(
            value = initialText,
            project = project,
            documentCreator = SimpleJsonDocumentCreator(),
            fileExtension = "json5",
        )
        return runInEdtAndGet {
            EditorFactory.getInstance().createEditor(document, project) as EditorEx
        }.also(editors::add)
    }

    private fun refreshFoldRegionsSynchronously(editor: EditorEx) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val foldRegions = ApplicationManager.getApplication().runReadAction(Computable {
            collectFoldRegions(project, editor.document)
        })
        applyFoldRegions(editor, foldRegions)
    }

    private fun createEditorTextFieldEditor(
        editorTextField: EditorTextField,
        disposable: Disposable,
    ): EditorEx {
        return runInEdtAndGet {
            editorTextField.setDisposedWith(disposable)
            editorTextField.getEditor(true) as EditorEx
        }
    }

    private fun waitForFoldRegions(editor: EditorEx) {
        val deadlineMs = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadlineMs) {
            UIUtil.dispatchAllInvocationEvents()
            val hasFoldRegions = runInEdtAndGet {
                editor.foldingModel.allFoldRegions.isNotEmpty()
            }
            if (hasFoldRegions) {
                return
            }
            Thread.sleep(50)
        }
    }
}
