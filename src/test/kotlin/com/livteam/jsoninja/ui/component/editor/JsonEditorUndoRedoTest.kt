package com.livteam.jsoninja.ui.component.editor

import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil

class JsonEditorUndoRedoTest : BasePlatformTestCase() {
    private val editorDisposables = mutableListOf<Disposable>()

    override fun tearDown() {
        try {
            runInEdtAndWait {
                editorDisposables.asReversed().forEach(Disposer::dispose)
                editorDisposables.clear()
            }
        } finally {
            super.tearDown()
        }
    }

    fun testTypingUndoRedoThroughEditorActions() {
        val jsonEditor = createJsonEditor()
        val typedText = """{"value":1}"""

        runInEdtAndWait {
            appendText(jsonEditor.editor, typedText)
            performEditorAction(jsonEditor.editor, IdeActions.ACTION_UNDO)
            assertEquals("", jsonEditor.view.getText())

            performEditorAction(jsonEditor.editor, IdeActions.ACTION_REDO)
            assertEquals(typedText, jsonEditor.view.getText())
        }
    }

    fun testReplacementUndoRedoThroughEditorActions() {
        val originalText = """{"value":1}"""
        val replacementText = "{\n  \"value\": 1\n}"
        val jsonEditor = createJsonEditor(originalText)

        runInEdtAndWait {
            jsonEditor.view.setText(replacementText)
            performEditorAction(jsonEditor.editor, IdeActions.ACTION_UNDO)
            assertEquals(originalText, jsonEditor.view.getText())

            performEditorAction(jsonEditor.editor, IdeActions.ACTION_REDO)
            assertEquals(replacementText, jsonEditor.view.getText())
        }
    }

    fun testRedoThroughEditorActionAfterDirectUndo() {
        val jsonEditor = createJsonEditor()
        val typedText = """{"value":1}"""

        runInEdtAndWait {
            appendText(jsonEditor.editor, typedText)
            val fileEditor = TextEditorProvider.getInstance().getTextEditor(jsonEditor.editor)
            val undoManager = UndoManager.getInstance(project)
            assertTrue("The JSON document must have an undo record", undoManager.isUndoAvailable(fileEditor))
            undoManager.undo(fileEditor)
            assertEquals("", jsonEditor.view.getText())
            assertTrue("Direct undo must create a redo record", undoManager.isRedoAvailable(fileEditor))

            performEditorAction(jsonEditor.editor, IdeActions.ACTION_REDO)
            assertEquals(typedText, jsonEditor.view.getText())
        }
    }

    fun testUndoThroughEditorActionDoesNotChangeParentEditor() {
        myFixture.configureByText("host.json", """{"host":0}""")

        runInEdtAndWait {
            val hostEditor = myFixture.editor as EditorEx
            val hostFileEditor = TextEditorProvider.getInstance().getTextEditor(hostEditor)
            appendText(hostEditor, " ")
            val hostTextAfterEdit = hostEditor.document.text
            val jsonEditor = createJsonEditor(parentFileEditor = hostFileEditor)
            appendText(jsonEditor.editor, """{"value":1}""")

            performEditorAction(jsonEditor.editor, IdeActions.ACTION_UNDO)
            assertEquals(
                "Undo from the JSON editor must not change the parent file editor",
                hostTextAfterEdit,
                hostEditor.document.text,
            )
            assertEquals("", jsonEditor.view.getText())

            performEditorAction(jsonEditor.editor, IdeActions.ACTION_REDO)
            assertEquals("""{"value":1}""", jsonEditor.view.getText())
            assertEquals(hostTextAfterEdit, hostEditor.document.text)
        }
    }

    fun testDirectUndoRedoRecordsTypedContent() {
        val jsonEditor = createJsonEditor()
        val typedText = """{"value":1}"""

        runInEdtAndWait {
            appendText(jsonEditor.editor, typedText)
            assertDirectUndoRedo(jsonEditor, "", typedText)
        }
    }

    fun testDirectUndoRedoRecordsPresenterReplacement() {
        val originalText = """{"value":1}"""
        val replacementText = "{\n  \"value\": 1\n}"
        val jsonEditor = createJsonEditor(originalText)

        runInEdtAndWait {
            jsonEditor.view.setText(replacementText)
            assertDirectUndoRedo(jsonEditor, originalText, replacementText)
        }
    }

    fun testNewEditAfterDirectUndoClearsRedo() {
        val jsonEditor = createJsonEditor()

        runInEdtAndWait {
            appendText(jsonEditor.editor, """{"value":1}""")
            val fileEditor = TextEditorProvider.getInstance().getTextEditor(jsonEditor.editor)
            val undoManager = UndoManager.getInstance(project)
            assertTrue(undoManager.isUndoAvailable(fileEditor))
            undoManager.undo(fileEditor)
            assertTrue(undoManager.isRedoAvailable(fileEditor))

            appendText(jsonEditor.editor, """{"value":2}""")
            assertFalse("A new edit must invalidate the old redo branch", undoManager.isRedoAvailable(fileEditor))
            assertEquals("""{"value":2}""", jsonEditor.view.getText())
        }
    }

    fun testPlaceholderNormalizationIsUndoneAndRedoneWithTyping() {
        val jsonEditor = createJsonEditor()
        val normalizedText = """{"value":{{name}}}"""

        runInEdtAndWait {
            appendText(jsonEditor.editor, """{"value":{{ name }}}""")
        }
        waitForEditorText(jsonEditor.view, normalizedText)

        runInEdtAndWait {
            assertDirectUndoRedo(jsonEditor, "", normalizedText)
        }
    }

    private fun createJsonEditor(
        initialText: String = "",
        parentFileEditor: TextEditor? = null,
    ): TestJsonEditor = runInEdtAndGet {
        val disposable = Disposer.newDisposable("JSONinja undo redo test")
        editorDisposables.add(disposable)
        val view = JsonEditorView(project)
        Disposer.register(disposable, view)
        view.editor.setDisposedWith(disposable)
        UiDataProvider.wrapComponent(view) { sink ->
            sink[CommonDataKeys.PROJECT] = project
            if (parentFileEditor != null) {
                sink[PlatformCoreDataKeys.FILE_EDITOR] = parentFileEditor
            }
        }
        view.setText(initialText)
        TestJsonEditor(view, view.editor.getEditor(true) as EditorEx)
    }

    private fun appendText(editor: EditorEx, text: String) {
        WriteCommandAction.runWriteCommandAction(project, "Type JSON", null, {
            editor.document.insertString(editor.document.textLength, text)
        })
    }

    private fun performEditorAction(editor: EditorEx, actionId: String) {
        // The headless fixture's DataManager ignores components; build the production UI snapshot directly.
        val dataContext = IdeUiService.getInstance().createUiDataContext(editor.contentComponent)
        assertSame("The action must originate from the JSON editor", editor, CommonDataKeys.EDITOR.getData(dataContext))
        val action = checkNotNull(ActionManager.getInstance().getAction(actionId))
        val event = AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
        action.update(event)
        assertTrue(
            actionId + " must be enabled for the JSON editor; FILE_EDITOR=" +
                PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext),
            event.presentation.isEnabled,
        )
        action.actionPerformed(event)
    }

    private fun assertDirectUndoRedo(jsonEditor: TestJsonEditor, originalText: String, editedText: String) {
        val fileEditor = TextEditorProvider.getInstance().getTextEditor(jsonEditor.editor)
        val undoManager = UndoManager.getInstance(project)
        assertTrue("The JSON document must have an undo record", undoManager.isUndoAvailable(fileEditor))
        undoManager.undo(fileEditor)
        assertEquals(originalText, jsonEditor.view.getText())

        assertTrue("Undo must create a redo record", undoManager.isRedoAvailable(fileEditor))
        undoManager.redo(fileEditor)
        assertEquals(editedText, jsonEditor.view.getText())
    }

    private fun waitForEditorText(view: JsonEditorView, expectedText: String) {
        val deadlineMs = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadlineMs) {
            UIUtil.dispatchAllInvocationEvents()
            if (runInEdtAndGet { view.getText() == expectedText }) {
                return
            }
            Thread.sleep(10)
        }
        assertEquals(expectedText, runInEdtAndGet { view.getText() })
    }

    private data class TestJsonEditor(
        val view: JsonEditorView,
        val editor: EditorEx,
    )
}
