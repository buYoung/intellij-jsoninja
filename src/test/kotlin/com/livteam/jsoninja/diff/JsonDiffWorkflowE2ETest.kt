package com.livteam.jsoninja.diff

import com.intellij.codeInsight.hint.EditorHintListener
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.Side
import com.intellij.ide.ui.IdeUiService
import com.intellij.json.JsonFileType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.FileEditorManagerTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.runAll
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import com.intellij.ui.UiInterceptors
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.actions.RenameJsonDiffTitlesAction
import com.livteam.jsoninja.actions.RestoreJsonDiffSortAction
import com.livteam.jsoninja.actions.SelectJsonDiffSortModeAction
import com.livteam.jsoninja.actions.ShowJsonDiffAction
import com.livteam.jsoninja.actions.SortJsonDiffKeysOnceAction
import com.livteam.jsoninja.actions.ToggleJsonDiffArrayOrderAction
import com.livteam.jsoninja.actions.ToggleJsonDiffAutoSortAction
import com.livteam.jsoninja.actions.editor.EditorShowJsonDiffAction
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.model.JsonDiffSortMode
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.diff.JsonDiffTitlesDialog
import com.livteam.jsoninja.ui.diff.JsonDiffVirtualFile
import com.livteam.jsoninja.ui.diff.JsonDiffWindowDialog
import java.awt.Component
import java.io.File
import javax.swing.JPanel

/**
 * JSON Diff 비교 흐름을 실제 IntelliJ diff host에서 끝까지 구동하는 e2e golden 테스트.
 *
 * - editor tab: 실제 FileEditorManagerImpl로 JsonDiffVirtualFile을 열고 DiffRequestProcessor의 viewer를 사용한다.
 * - window: JsonDiffService가 만든 JsonDiffWindowDialog를 UiInterceptors로 받아 그 안의 diff panel을 사용한다.
 * - 툴바 액션은 request의 CONTEXT_ACTIONS 인스턴스를 toolbar 대상 컴포넌트의 실제 UI DataContext로 실행한다.
 * - 문서 결과는 testData/jsonDiff/workflow의 golden 파일과 비교한다.
 */
class JsonDiffWorkflowE2ETest : FileEditorManagerTestCase() {

    private val diffService: JsonDiffService
        get() = project.service<JsonDiffService>()

    private val settings: JsoninjaSettingsState
        get() = JsoninjaSettingsState.getInstance(project)

    private val openedDialogs = mutableListOf<DialogWrapper>()
    private var originalIndentSize = DEFAULT_INDENT_SIZE
    private var originalDiffSortKeys = false
    private var originalDiffDisplayMode = JsonDiffDisplayMode.WINDOW.name
    private var originalShowLargeFileWarning = true
    private var originalIsDiffInEditor = true

    override fun getTestDataPath(): String = "src/test/testData/jsonDiff/workflow"

    override fun setUp() {
        super.setUp()
        originalIndentSize = settings.indentSize
        originalDiffSortKeys = settings.diffSortKeys
        originalDiffDisplayMode = settings.diffDisplayMode
        originalShowLargeFileWarning = settings.showLargeFileWarning
        originalIsDiffInEditor = DiffEditorTabFilesManager.isDiffInEditor

        settings.indentSize = DEFAULT_INDENT_SIZE
        settings.diffSortKeys = false
        settings.diffDisplayMode = JsonDiffDisplayMode.EDITOR_TAB.name
        settings.showLargeFileWarning = true
        DiffEditorTabFilesManager.isDiffInEditor = true
    }

    override fun tearDown() {
        runAll(
            {
                openedDialogs.filterNot { it.isDisposed }.forEach { it.close(DialogWrapper.CANCEL_EXIT_CODE) }
                openedDialogs.clear()
            },
            { UiInterceptors.clear() },
            {
                settings.indentSize = originalIndentSize
                settings.diffSortKeys = originalDiffSortKeys
                settings.diffDisplayMode = originalDiffDisplayMode
                settings.showLargeFileWarning = originalShowLargeFileWarning
                DiffEditorTabFilesManager.isDiffInEditor = originalIsDiffInEditor
            },
            { super.tearDown() }
        )
    }

    // 전역 기준 2: 툴바에서 모드/자동/배열/복원/이름 변경을 설정 화면 없이 제공한다.
    fun testToolbarExposesSessionControlsInBothHosts() {
        for (displayMode in JsonDiffDisplayMode.entries) {
            val host = openHost(displayMode, inputText("object-order/left-input.json"), defaultSortKeys = false)
            try {
                val contextActions = checkNotNull(host.request.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS))
                assertEquals(
                    listOf(
                        SortJsonDiffKeysOnceAction::class,
                        SelectJsonDiffSortModeAction::class,
                        ToggleJsonDiffAutoSortAction::class,
                        ToggleJsonDiffArrayOrderAction::class,
                        RestoreJsonDiffSortAction::class,
                        RenameJsonDiffTitlesAction::class
                    ),
                    contextActions.map { it::class }
                )
                val toolbarActions = flattenActions(host.toolbar.actionGroup)
                contextActions.forEach { action ->
                    assertTrue(
                        "$displayMode toolbar must contain ${action.javaClass.simpleName}",
                        toolbarActions.any { it === action }
                    )
                }

                val modeAction = host.contextAction<SelectJsonDiffSortModeAction>()
                assertEquals(selectedModeText(JsonDiffSortMode.KEY_ASCENDING), update(host, modeAction).text)
                assertEquals(
                    JsonDiffSortMode.entries.map { sortModeText(it) },
                    modeAction.createPopupActionGroup(JPanel(), host.dataContext()).getChildren(null)
                        .map { it.templatePresentation.text }
                )
                assertEquals(
                    LocalizationBundle.message("action.diff.sort.keys.once"),
                    host.contextAction<SortJsonDiffKeysOnceAction>().templatePresentation.text
                )
                assertTrue(update(host, host.contextAction<SortJsonDiffKeysOnceAction>()).isEnabledAndVisible)
                assertFalse(Toggleable.isSelected(update(host, host.contextAction<ToggleJsonDiffAutoSortAction>())))
                assertFalse(Toggleable.isSelected(update(host, host.contextAction<ToggleJsonDiffArrayOrderAction>())))

                val restorePresentation = update(host, host.contextAction<RestoreJsonDiffSortAction>())
                assertTrue(restorePresentation.isVisible)
                assertFalse("Restore must be unavailable before any sort", restorePresentation.isEnabled)
                assertTrue(update(host, host.contextAction<RenameJsonDiffTitlesAction>()).isEnabledAndVisible)
            } finally {
                host.close()
            }
        }
    }

    // 새 세션은 기존 diffSortKeys로 자동 정렬을 시작하고, 툴바 선택은 설정에 저장하지 않는다.
    fun testAutoSortDefaultComesFromSettingsAndToolbarChoicesStaySessionLocal() {
        settings.diffSortKeys = true
        ShowJsonDiffAction.openDiffForCurrentJson(project, JsonDiffDisplayMode.EDITOR_TAB)
        val firstHost = awaitEditorTabHost()
        val firstSession = firstHost.session
        try {
            assertTrue(firstSession.shouldAutoSort)
            assertEquals(JsonDiffSortMode.KEY_ASCENDING, firstSession.sortMode)
            assertFalse(firstSession.shouldIgnoreArrayOrder)
            assertTrue(Toggleable.isSelected(update(firstHost, firstHost.contextAction<ToggleJsonDiffAutoSortAction>())))

            perform(firstHost, firstHost.contextAction<ToggleJsonDiffAutoSortAction>())
            perform(firstHost, firstHost.contextAction<ToggleJsonDiffArrayOrderAction>())
            selectSortMode(firstHost, JsonDiffSortMode.LEFT_ORDER)
            pumpEvents(QUIET_PERIOD_MS)

            assertFalse(firstSession.shouldAutoSort)
            assertTrue(firstSession.shouldIgnoreArrayOrder)
            assertEquals(JsonDiffSortMode.LEFT_ORDER, firstSession.sortMode)
            assertTrue("Toolbar choices must not rewrite the persisted default", settings.diffSortKeys)
            assertEquals(JsonDiffDisplayMode.EDITOR_TAB.name, settings.diffDisplayMode)
        } finally {
            firstHost.close()
        }

        settings.diffSortKeys = false
        val nextHost = openHost(JsonDiffDisplayMode.EDITOR_TAB, "{}", settings.diffSortKeys)
        try {
            assertNotSame(firstSession, nextHost.session)
            assertTrue(firstSession.isClosed)
            assertFalse(nextHost.session.shouldAutoSort)
            assertEquals(JsonDiffSortMode.KEY_ASCENDING, nextHost.session.sortMode)
            assertFalse(nextHost.session.shouldIgnoreArrayOrder)
        } finally {
            nextHost.close()
        }
    }

    // 전역 기준 1: 왼쪽 기준과 키 사전순을 두 host 모두에서 적용한다.
    fun testEditorTabAppliesLeftOrderAndKeyAscending() = assertObjectOrderModes(JsonDiffDisplayMode.EDITOR_TAB)

    fun testWindowAppliesLeftOrderAndKeyAscending() = assertObjectOrderModes(JsonDiffDisplayMode.WINDOW)

    // 전역 기준 2: 자동 왼쪽 기준에서 기준 문서의 키 순서 변경이 바뀌지 않은 오른쪽에 전파된다.
    fun testEditorTabAutoLeftOrderFollowsReferenceEdits() =
        assertAutoLeftOrderFollowsReferenceEdits(JsonDiffDisplayMode.EDITOR_TAB)

    fun testWindowAutoLeftOrderFollowsReferenceEdits() =
        assertAutoLeftOrderFollowsReferenceEdits(JsonDiffDisplayMode.WINDOW)

    // 전역 기준 3: 정렬 직후 되돌리기, 이후 편집이 있으면 되돌리기 불가.
    fun testEditorTabRestoreBeforeSort() = assertRestoreFlow(JsonDiffDisplayMode.EDITOR_TAB)

    fun testWindowRestoreBeforeSort() = assertRestoreFlow(JsonDiffDisplayMode.WINDOW)

    fun testRestoreWithAutoSortDoesNotResortItsOwnWrite() {
        val host = openHost(JsonDiffDisplayMode.EDITOR_TAB, inputText("restore/input.json"), defaultSortKeys = false)
        try {
            awaitAutoFormatter(host)
            replaceWithoutAutoFormatting(host.leftDocument, golden("restore/left-prepared.json").trim())
            replaceWithoutAutoFormatting(host.rightDocument, inputText("restore/input.json"))
            val preSortLeftText = host.leftDocument.text
            val preSortRightText = host.rightDocument.text

            perform(host, host.contextAction<ToggleJsonDiffAutoSortAction>())
            assertDocumentMatchesGolden(host.leftDocument, "restore/sorted.json")
            assertDocumentMatchesGolden(host.rightDocument, "restore/sorted.json")

            perform(host, host.contextAction<RestoreJsonDiffSortAction>())
            assertEquals(preSortLeftText, host.leftDocument.text)
            assertEquals(preSortRightText, host.rightDocument.text)

            pumpEvents(QUIET_PERIOD_MS)
            assertTrue(host.session.shouldAutoSort)
            assertEquals("Restore must not trigger its own automatic sort", preSortLeftText, host.leftDocument.text)
            assertEquals(preSortRightText, host.rightDocument.text)

            typeText(host.leftDocument, inputText("restore/auto-edit.json"))
            assertDocumentMatchesGolden(host.leftDocument, "restore/auto-edit-sorted.json")
            assertFalse(update(host, host.contextAction<RestoreJsonDiffSortAction>()).isEnabled)
        } finally {
            host.close()
        }
    }

    // 짝 정렬이 불가능한 수동 요청은 문서를 바꾸지 않고 안내만 표시한다.
    fun testUnavailablePairSortShowsHintWithoutChangingDocuments() {
        val hintedEditors = recordEditorHints()
        val host = openHost(JsonDiffDisplayMode.WINDOW, inputText("object-order/left-input.json"), defaultSortKeys = false)
        try {
            replaceWithoutAutoFormatting(host.rightDocument, """{"name":"B",""")
            val leftText = host.leftDocument.text
            val rightText = host.rightDocument.text

            selectSortMode(host, JsonDiffSortMode.LEFT_ORDER)
            waitUntil("Unavailable LEFT_ORDER sort must show an error hint") {
                hintedEditors.any { editor -> host.viewer.editors.any { it === editor } }
            }
            pumpEvents(QUIET_PERIOD_MS)

            assertEquals(leftText, host.leftDocument.text)
            assertEquals(rightText, host.rightDocument.text)
            assertFalse(update(host, host.contextAction<RestoreJsonDiffSortAction>()).isEnabled)
        } finally {
            host.close()
        }
    }

    // 전역 기준 4: 배열 순서는 기본적으로 유지되고, 배열 순서 무시를 켠 뒤에만 같은 요소끼리 맞춘다.
    fun testEditorTabArrayOrderOption() = assertArrayOrderOption(JsonDiffDisplayMode.EDITOR_TAB)

    fun testWindowArrayOrderOption() = assertArrayOrderOption(JsonDiffDisplayMode.WINDOW)

    // 전역 기준 5: 편집기 선택을 왼쪽에 열고 원본 편집기는 변경하지 않는다.
    fun testEditorSelectionReplacesOnlyLeftInputWithoutSourceChange() {
        val host = openHost(
            JsonDiffDisplayMode.EDITOR_TAB,
            inputText("editor-entry/tool-window-input.json"),
            defaultSortKeys = false
        )
        replaceWithoutAutoFormatting(host.rightDocument, inputText("editor-entry/right-baseline.json"))
        val diffFile = checkNotNull(findOpenDiffFile())
        val rightBaselineText = host.rightDocument.text

        myFixture.configureByText("source.json", inputText("editor-entry/source-with-selection.json"))
        val sourceEditor = myFixture.editor
        val sourceDocument = sourceEditor.document
        val sourceText = sourceDocument.text
        val sourceStamp = sourceDocument.modificationStamp
        val selectionStart = sourceEditor.selectionModel.selectionStart
        val selectionEnd = sourceEditor.selectionModel.selectionEnd
        assertEquals("""{"error":"E01"}""", sourceEditor.selectionModel.selectedText)

        val presentation = myFixture.testAction(EditorShowJsonDiffAction())
        assertEquals(LocalizationBundle.message("editor.action.show.json.diff.selection"), presentation.text)

        assertDocumentMatchesGolden(host.leftDocument, "editor-entry/left-selection.json")
        assertEquals("The open comparison must keep its right baseline", rightBaselineText, host.rightDocument.text)
        assertSame("The open editor tab must be reused", diffFile, findOpenDiffFile())
        assertSame(host.session, awaitEditorTabHost().session)

        assertEquals(sourceText, sourceDocument.text)
        assertEquals(sourceStamp, sourceDocument.modificationStamp)
        assertEquals(selectionStart, sourceEditor.selectionModel.selectionStart)
        assertEquals(selectionEnd, sourceEditor.selectionModel.selectionEnd)
        host.close()
    }

    fun testEditorWithoutSelectionUsesWholeValidDocument() {
        myFixture.configureByText("source.json", inputText("editor-entry/source-document.json"))
        val sourceDocument = myFixture.editor.document
        val sourceText = sourceDocument.text
        val sourceStamp = sourceDocument.modificationStamp
        assertFalse(myFixture.editor.selectionModel.hasSelection())

        val presentation = myFixture.testAction(EditorShowJsonDiffAction())
        assertEquals(LocalizationBundle.message("editor.action.show.json.diff.document"), presentation.text)

        val host = awaitEditorTabHost()
        try {
            assertDocumentMatchesGolden(host.leftDocument, "editor-entry/left-document.json")
            assertEquals(sourceText, sourceDocument.text)
            assertEquals(sourceStamp, sourceDocument.modificationStamp)
        } finally {
            host.close()
        }
    }

    fun testInvalidSelectionDoesNotFallBackToDocument() {
        val hintedEditors = recordEditorHints()
        myFixture.configureByText("source.json", inputText("editor-entry/source-invalid-selection.json"))
        val sourceDocument = myFixture.editor.document
        val sourceText = sourceDocument.text
        assertEquals("""{"b":""", myFixture.editor.selectionModel.selectedText)

        myFixture.testAction(EditorShowJsonDiffAction())
        waitUntil("Invalid selection must show an error hint") { hintedEditors.contains(myFixture.editor) }
        pumpEvents(QUIET_PERIOD_MS)

        assertNull("Invalid selection must not open the whole document or another input", findOpenDiffFile())
        assertEquals(sourceText, sourceDocument.text)
    }

    // 전역 기준 6: 양쪽 이름 변경은 같은 세션의 Document와 정렬 상태를 유지한다.
    fun testEditorTabRenameSidesKeepsSessionState() = assertRenameKeepsSessionState(JsonDiffDisplayMode.EDITOR_TAB)

    fun testWindowRenameSidesKeepsSessionState() = assertRenameKeepsSessionState(JsonDiffDisplayMode.WINDOW)

    // 전역 기준 7: 두 번째 입력은 열린 host를 재사용하고 오른쪽 기준과 선택을 유지한다.
    fun testSecondInputReusesOpenEditorTabAndKeepsRightBaseline() {
        val host = openHost(JsonDiffDisplayMode.EDITOR_TAB, inputText("reuse/first-input.json"), defaultSortKeys = false)
        try {
            replaceWithoutAutoFormatting(host.rightDocument, inputText("reuse/right-input.json"))
            perform(host, host.contextAction<SortJsonDiffKeysOnceAction>())
            assertDocumentMatchesGolden(host.rightDocument, "reuse/right-baseline.json")
            perform(host, host.contextAction<ToggleJsonDiffArrayOrderAction>())
            renameSides(host, "개발 서버", "운영 서버", isConfirmed = true)
            pumpEvents(QUIET_PERIOD_MS)
            assertTrue(update(host, host.contextAction<RestoreJsonDiffSortAction>()).isEnabled)

            val diffFile = checkNotNull(findOpenDiffFile())
            val rightBaselineText = host.rightDocument.text
            diffService.openDiff(JsonDiffDisplayMode.EDITOR_TAB, inputText("reuse/second-input.json"), true)

            assertDocumentMatchesGolden(host.leftDocument, "reuse/left-second.json")
            assertEquals(rightBaselineText, host.rightDocument.text)
            assertSame(diffFile, findOpenDiffFile())
            assertEquals(1, manager!!.openFiles.count { it is JsonDiffVirtualFile })

            val reusedHost = awaitEditorTabHost()
            assertSame(host.session, reusedHost.session)
            assertTrue(reusedHost.session.shouldIgnoreArrayOrder)
            assertFalse("A reused session keeps its own auto-sort choice", reusedHost.session.shouldAutoSort)
            assertTitleLabels(reusedHost, "개발 서버", "운영 서버")
            assertFalse(
                "Replacing the input pair must discard the old restore snapshot",
                update(reusedHost, reusedHost.contextAction<RestoreJsonDiffSortAction>()).isEnabled
            )
        } finally {
            host.close()
        }
    }

    fun testDisplayModeSwitchKeepsSessionAndRightBaseline() {
        val tabHost = openHost(JsonDiffDisplayMode.EDITOR_TAB, inputText("reuse/first-input.json"), defaultSortKeys = false)
        replaceWithoutAutoFormatting(tabHost.rightDocument, golden("reuse/right-baseline.json").trim())
        val rightBaselineText = tabHost.rightDocument.text

        val windowHost = windowHost(interceptWindowDialog {
            diffService.openDiff(JsonDiffDisplayMode.WINDOW, inputText("reuse/second-input.json"), false)
        })
        try {
            assertNull("Switching to window must close the editor tab host", findOpenDiffFile())
            assertSame(tabHost.session, windowHost.session)
            assertDocumentMatchesGolden(windowHost.leftDocument, "reuse/left-second.json")
            assertEquals(rightBaselineText, windowHost.rightDocument.text)
        } finally {
            windowHost.close()
        }
    }

    // 전역 기준 8: 대기 중인 포맷팅은 최신 입력, 최신 모드, 열린 host에서만 적용된다.
    fun testRapidEditsKeepLatestInput() {
        val host = openHost(JsonDiffDisplayMode.EDITOR_TAB, """{"count":0}""", defaultSortKeys = false)
        try {
            awaitAutoFormatter(host)
            typeText(host.leftDocument, """{"count":1}""")
            pumpEvents(DEBOUNCE_EDGE_MS)
            typeText(host.leftDocument, """{"count":2}""")

            assertDocumentMatchesGolden(host.leftDocument, "pending/count-2.json")
            pumpEvents(QUIET_PERIOD_MS)
            assertSameLinesWithFile(goldenPath("pending/count-2.json"), host.leftDocument.text)
        } finally {
            host.close()
        }
    }

    fun testModeChangeRejectsPendingResultOfPreviousMode() {
        val host = openHost(JsonDiffDisplayMode.EDITOR_TAB, inputText("mode-change/right-input.json"), defaultSortKeys = true)
        try {
            assertTrue(host.session.shouldAutoSort)
            awaitAutoFormatter(host)
            replaceWithoutAutoFormatting(host.rightDocument, inputText("mode-change/right-input.json"))

            typeText(host.leftDocument, inputText("mode-change/left-edit.json"))
            selectSortMode(host, JsonDiffSortMode.LEFT_ORDER)

            assertDocumentMatchesGolden(host.rightDocument, "mode-change/right-aligned.json")
            pumpEvents(QUIET_PERIOD_MS)
            assertEquals(
                "The pending KEY_ASCENDING result must not rewrite the new LEFT_ORDER reference",
                inputText("mode-change/left-edit.json"),
                host.leftDocument.text
            )
        } finally {
            host.close()
        }
    }

    fun testEditorTabCloseDiscardsPendingFormatting() =
        assertClosingHostDiscardsPendingFormatting(JsonDiffDisplayMode.EDITOR_TAB)

    fun testWindowCloseDiscardsPendingFormatting() =
        assertClosingHostDiscardsPendingFormatting(JsonDiffDisplayMode.WINDOW)

    // 기존 계약: Boolean createDiffRequest는 사전순 자동 정렬 의미를 유지하고, 표시 없는 IDE diff에는 개입하지 않는다.
    fun testLegacyRequestKeepsBooleanContractAndUnmarkedDiffStaysUntouched() {
        val leftDocument = EditorFactory.getInstance().createDocument("{}")
        val rightDocument = EditorFactory.getInstance().createDocument("{}")
        val legacyRequest = diffService.createDiffRequest(leftDocument, rightDocument, semantic = true)

        assertEquals(LocalizationBundle.message("dialog.json.diff.title"), legacyRequest.title)
        assertEquals(
            listOf(LocalizationBundle.message("dialog.json.diff.left"), LocalizationBundle.message("dialog.json.diff.right")),
            legacyRequest.contentTitles
        )
        assertEquals(true, legacyRequest.getUserData(JsonDiffKeys.JSON_DIFF_REQUEST_MARKER))
        assertEquals(true, legacyRequest.getUserData(JsonDiffKeys.JSON_DIFF_SORT_KEYS))
        val legacySession = checkNotNull(legacyRequest.getUserData(JsonDiffKeys.JSON_DIFF_SESSION))
        assertTrue(legacySession.shouldAutoSort)
        assertEquals(JsonDiffSortMode.KEY_ASCENDING, legacySession.sortMode)
        assertTrue(checkNotNull(legacyRequest.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS)).first() is SortJsonDiffKeysOnceAction)

        DiffManager.getInstance().createRequestPanel(project, testRootDisposable, null).setRequest(legacyRequest)
        awaitAutoFormatter(rightDocument, "legacy request")
        typeText(leftDocument, """{"b":1,"a":2}""")
        assertDocumentMatchesGolden(leftDocument, "legacy/auto-sorted.json")

        val plainLeftDocument = EditorFactory.getInstance().createDocument("{}")
        val plainRightDocument = EditorFactory.getInstance().createDocument("{}")
        val contentFactory = DiffContentFactory.getInstance()
        val plainRequest = SimpleDiffRequest(
            "Plain IDE diff",
            contentFactory.create(project, plainLeftDocument, JsonFileType.INSTANCE),
            contentFactory.create(project, plainRightDocument, JsonFileType.INSTANCE),
            "Before",
            "After"
        )
        DiffManager.getInstance().createRequestPanel(project, testRootDisposable, null).setRequest(plainRequest)
        typeText(plainLeftDocument, """{"b":1,"a":2}""")
        pumpEvents(QUIET_PERIOD_MS)
        assertEquals("""{"b":1,"a":2}""", plainLeftDocument.text)
    }

    private fun assertObjectOrderModes(displayMode: JsonDiffDisplayMode) {
        val host = openHost(displayMode, inputText("object-order/left-input.json"), defaultSortKeys = false)
        try {
            assertDocumentMatchesGolden(host.leftDocument, "object-order/left-prepared.json")
            replaceWithoutAutoFormatting(host.rightDocument, inputText("object-order/right-input.json"))
            val referenceText = host.leftDocument.text

            selectSortMode(host, JsonDiffSortMode.LEFT_ORDER)
            assertDocumentMatchesGolden(host.rightDocument, "object-order/left-order-right.json")
            assertEquals("LEFT_ORDER must not rewrite the reference", referenceText, host.leftDocument.text)
            assertEquals(
                selectedModeText(JsonDiffSortMode.LEFT_ORDER),
                update(host, host.contextAction<SelectJsonDiffSortModeAction>()).text
            )

            selectSortMode(host, JsonDiffSortMode.KEY_ASCENDING)
            assertDocumentMatchesGolden(host.leftDocument, "object-order/key-ascending-left.json")
            assertDocumentMatchesGolden(host.rightDocument, "object-order/key-ascending-right.json")
        } finally {
            host.close()
        }
    }

    private fun assertAutoLeftOrderFollowsReferenceEdits(displayMode: JsonDiffDisplayMode) {
        val host = openHost(displayMode, inputText("object-order/left-input.json"), defaultSortKeys = false)
        try {
            awaitAutoFormatter(host)
            replaceWithoutAutoFormatting(host.rightDocument, inputText("object-order/right-input.json"))
            selectSortMode(host, JsonDiffSortMode.LEFT_ORDER)
            assertDocumentMatchesGolden(host.rightDocument, "object-order/left-order-right.json")

            perform(host, host.contextAction<ToggleJsonDiffAutoSortAction>())
            assertTrue(host.session.shouldAutoSort)
            assertTrue(Toggleable.isSelected(update(host, host.contextAction<ToggleJsonDiffAutoSortAction>())))
            pumpEvents(QUIET_PERIOD_MS)

            typeText(host.leftDocument, inputText("auto-left-order/left-edit.json"))
            assertDocumentMatchesGolden(host.leftDocument, "auto-left-order/left-after-auto.json")
            assertDocumentMatchesGolden(host.rightDocument, "auto-left-order/right-after-auto.json")
            assertFalse("Toolbar auto sort must stay session-local", settings.diffSortKeys)
        } finally {
            host.close()
        }
    }

    private fun assertRestoreFlow(displayMode: JsonDiffDisplayMode) {
        val host = openHost(displayMode, inputText("restore/input.json"), defaultSortKeys = false)
        try {
            assertDocumentMatchesGolden(host.leftDocument, "restore/left-prepared.json")
            replaceWithoutAutoFormatting(host.rightDocument, inputText("restore/input.json"))
            val preSortLeftText = host.leftDocument.text
            val preSortRightText = host.rightDocument.text
            val restoreAction = host.contextAction<RestoreJsonDiffSortAction>()
            assertFalse(update(host, restoreAction).isEnabled)

            perform(host, host.contextAction<SortJsonDiffKeysOnceAction>())
            assertDocumentMatchesGolden(host.leftDocument, "restore/sorted.json")
            assertDocumentMatchesGolden(host.rightDocument, "restore/sorted.json")
            assertTrue(update(host, restoreAction).isEnabled)

            perform(host, restoreAction)
            assertEquals(preSortLeftText, host.leftDocument.text)
            assertEquals(preSortRightText, host.rightDocument.text)
            assertFalse(update(host, restoreAction).isEnabled)
            pumpEvents(QUIET_PERIOD_MS)
            assertEquals(preSortLeftText, host.leftDocument.text)
            assertEquals(preSortRightText, host.rightDocument.text)

            perform(host, host.contextAction<SortJsonDiffKeysOnceAction>())
            assertDocumentMatchesGolden(host.rightDocument, "restore/sorted.json")
            typeText(host.rightDocument, golden("restore/edited-after-sort.json").trim())
            assertFalse("A later edit must disable the dedicated restore", update(host, restoreAction).isEnabled)
            assertFalse(diffService.restoreSort(host.session) { true })
            pumpEvents(QUIET_PERIOD_MS)
            assertSameLinesWithFile(goldenPath("restore/edited-after-sort.json"), host.rightDocument.text)
        } finally {
            host.close()
        }
    }

    private fun assertArrayOrderOption(displayMode: JsonDiffDisplayMode) {
        val host = openHost(displayMode, inputText("array/left-input.json"), defaultSortKeys = false)
        try {
            assertDocumentMatchesGolden(host.leftDocument, "array/left-prepared.json")
            replaceWithoutAutoFormatting(host.rightDocument, inputText("array/right-input.json"))
            val arrayAction = host.contextAction<ToggleJsonDiffArrayOrderAction>()
            assertFalse(Toggleable.isSelected(update(host, arrayAction)))

            perform(host, host.contextAction<SortJsonDiffKeysOnceAction>())
            assertDocumentMatchesGolden(host.rightDocument, "array/right-default.json")

            perform(host, arrayAction)
            assertTrue(host.session.shouldIgnoreArrayOrder)
            assertTrue(Toggleable.isSelected(update(host, arrayAction)))
            assertDocumentMatchesGolden(host.rightDocument, "array/right-ignored.json")
            assertSameLinesWithFile(goldenPath("array/left-prepared.json"), host.leftDocument.text)
        } finally {
            host.close()
        }
    }

    private fun assertRenameKeepsSessionState(displayMode: JsonDiffDisplayMode) {
        val host = openHost(displayMode, inputText("restore/input.json"), defaultSortKeys = false)
        try {
            replaceWithoutAutoFormatting(host.rightDocument, inputText("restore/input.json"))
            perform(host, host.contextAction<SortJsonDiffKeysOnceAction>())
            assertDocumentMatchesGolden(host.rightDocument, "restore/sorted.json")
            perform(host, host.contextAction<ToggleJsonDiffArrayOrderAction>())
            pumpEvents(QUIET_PERIOD_MS)

            val session = host.session
            val leftDocument = session.leftDocument
            val rightDocument = session.rightDocument
            val leftText = leftDocument.text
            val rightText = rightDocument.text
            val restoreAction = host.contextAction<RestoreJsonDiffSortAction>()
            assertTrue(update(host, restoreAction).isEnabled)
            val defaultLeftTitle = LocalizationBundle.message("dialog.json.diff.left")
            val defaultRightTitle = LocalizationBundle.message("dialog.json.diff.right")
            assertTitleLabels(host, defaultLeftTitle, defaultRightTitle)

            val firstDialogState = renameSides(host, "개발 서버", "운영 서버", isConfirmed = true)
            assertEquals(TitleDialogState("", "", defaultLeftTitle, defaultRightTitle), firstDialogState)
            assertTitleLabels(host, "개발 서버", "운영 서버")

            val secondDialogState = renameSides(host, "배포 전", "배포 후", isConfirmed = true)
            assertEquals("개발 서버", secondDialogState.leftFieldText)
            assertEquals("운영 서버", secondDialogState.rightFieldText)
            assertTitleLabels(host, "배포 전", "배포 후")

            renameSides(host, "취소된 왼쪽", "취소된 오른쪽", isConfirmed = false)
            assertTitleLabels(host, "배포 전", "배포 후")

            renameSides(host, "   ", "배포 후", isConfirmed = true)
            assertTitleLabels(host, defaultLeftTitle, "배포 후")

            pumpEvents(QUIET_PERIOD_MS)
            assertSame(leftDocument, host.session.leftDocument)
            assertSame(rightDocument, host.session.rightDocument)
            assertSame(leftDocument, host.viewer.editors[0].document)
            assertSame(rightDocument, host.viewer.editors[1].document)
            assertEquals(leftText, leftDocument.text)
            assertEquals(rightText, rightDocument.text)
            assertEquals(JsonDiffSortMode.KEY_ASCENDING, session.sortMode)
            assertTrue(session.shouldIgnoreArrayOrder)
            assertFalse(session.shouldAutoSort)
            assertTrue("Renaming must keep a still-valid restore", update(host, restoreAction).isEnabled)
            assertEquals(LocalizationBundle.message("dialog.json.diff.title"), host.request.title)
        } finally {
            host.close()
        }
    }

    private fun assertClosingHostDiscardsPendingFormatting(displayMode: JsonDiffDisplayMode) {
        val host = openHost(displayMode, """{"open":true}""", defaultSortKeys = false)
        val document = host.leftDocument
        awaitAutoFormatter(host)

        typeText(document, """{"late":true}""")
        host.close()
        pumpEvents(QUIET_PERIOD_MS)

        assertEquals("A closed host must not receive pending formatting", """{"late":true}""", document.text)
    }

    private inner class DiffHost(
        val displayMode: JsonDiffDisplayMode,
        val viewer: EditorDiffViewer,
        val toolbar: ActionToolbar,
        val request: DiffRequest,
        private val closeHost: () -> Unit
    ) {
        val session: JsonDiffSession = checkNotNull(request.getUserData(JsonDiffKeys.JSON_DIFF_SESSION))

        val leftDocument: Document
            get() = session.leftDocument

        val rightDocument: Document
            get() = session.rightDocument

        fun dataContext(): DataContext {
            return IdeUiService.getInstance().createUiDataContext(toolbar.targetComponent ?: toolbar.component)
        }

        inline fun <reified T : AnAction> contextAction(): T {
            return checkNotNull(request.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS)).filterIsInstance<T>().single()
        }

        fun close() {
            closeHost()
            pumpEvents(HOST_CLOSE_SETTLE_MS)
        }
    }

    private data class TitleDialogState(
        val leftFieldText: String,
        val rightFieldText: String,
        val leftEmptyText: String,
        val rightEmptyText: String
    )

    private fun openHost(displayMode: JsonDiffDisplayMode, leftJson: String, defaultSortKeys: Boolean): DiffHost {
        return when (displayMode) {
            JsonDiffDisplayMode.EDITOR_TAB -> {
                diffService.openDiff(JsonDiffDisplayMode.EDITOR_TAB, leftJson, defaultSortKeys)
                awaitEditorTabHost()
            }

            JsonDiffDisplayMode.WINDOW -> windowHost(interceptWindowDialog {
                diffService.openDiff(JsonDiffDisplayMode.WINDOW, leftJson, defaultSortKeys)
            })
        }
    }

    private fun awaitEditorTabHost(): DiffHost {
        var processor: DiffRequestProcessor? = null
        waitUntil("JSON diff editor tab did not show a two-side JSONinja viewer") {
            val currentProcessor = findEditorTabProcessor() ?: return@waitUntil false
            processor = currentProcessor
            if (currentProcessor.activeViewer !is EditorDiffViewer) {
                // 실제 IDE에서는 탭 선택 시 호출되는 활성화 신호다.
                currentProcessor.fireProcessorActivated()
            }
            isJsonSessionViewer(currentProcessor.activeViewer, currentProcessor.activeRequest)
        }

        val activeProcessor = checkNotNull(processor)
        val diffFile = checkNotNull(findOpenDiffFile())
        return DiffHost(
            displayMode = JsonDiffDisplayMode.EDITOR_TAB,
            viewer = activeProcessor.activeViewer as EditorDiffViewer,
            toolbar = activeProcessor.toolbar,
            request = checkNotNull(activeProcessor.activeRequest)
        ) { manager!!.closeFile(diffFile) }
    }

    private fun interceptWindowDialog(openWindow: () -> Unit): JsonDiffWindowDialog {
        var shownDialog: JsonDiffWindowDialog? = null
        UiInterceptors.register(object : UiInterceptors.UiInterceptor<JsonDiffWindowDialog>(JsonDiffWindowDialog::class.java) {
            override fun doIntercept(component: JsonDiffWindowDialog) {
                shownDialog = component
            }
        })
        openWindow()
        waitUntil("JSON diff window was not shown") { shownDialog != null }
        return checkNotNull(shownDialog).also { openedDialogs += it }
    }

    private fun windowHost(dialog: JsonDiffWindowDialog): DiffHost {
        val focusedComponent = checkNotNull(dialog.preferredFocusedComponent) { "Window diff viewer was not created" }
        val rootComponent = generateSequence<Component>(focusedComponent) { it.parent }.last()
        val toolbar = UIUtil.uiTraverser(rootComponent)
            .filter(ActionToolbar::class.java)
            .first { toolbar -> flattenActions(toolbar.actionGroup).any { it is SortJsonDiffKeysOnceAction } }
        val dataContext = IdeUiService.getInstance().createUiDataContext(toolbar.targetComponent ?: toolbar.component)
        val viewer = dataContext.getData(DiffDataKeys.DIFF_VIEWER) as EditorDiffViewer
        val request = checkNotNull(dataContext.getData(DiffDataKeys.DIFF_REQUEST))
        assertTrue(isJsonSessionViewer(viewer, request))
        return DiffHost(JsonDiffDisplayMode.WINDOW, viewer, toolbar, request) {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    private fun isJsonSessionViewer(viewer: Any?, request: DiffRequest?): Boolean {
        return viewer is EditorDiffViewer &&
            viewer.editors.size == 2 &&
            request?.getUserData(JsonDiffKeys.JSON_DIFF_SESSION) != null
    }

    private fun findOpenDiffFile(): JsonDiffVirtualFile? {
        return manager!!.openFiles.filterIsInstance<JsonDiffVirtualFile>().singleOrNull()
    }

    private fun findEditorTabProcessor(): DiffRequestProcessor? {
        val diffFile = findOpenDiffFile() ?: return null
        return manager!!.getEditors(diffFile)
            .filterIsInstance<DiffEditorViewerFileEditor>()
            .firstOrNull()
            ?.editorViewer as? DiffRequestProcessor
    }

    private fun flattenActions(group: ActionGroup): List<AnAction> {
        val children = runCatching { group.getChildren(null).toList() }.getOrDefault(emptyList())
        return children.flatMap { action ->
            if (action is ActionGroup) listOf(action) + flattenActions(action) else listOf(action)
        }
    }

    private fun update(host: DiffHost, action: AnAction): Presentation {
        val event = createToolbarEvent(host, action)
        ActionUtil.performDumbAwareUpdate(action, event, false)
        return event.presentation
    }

    private fun perform(host: DiffHost, action: AnAction) {
        val event = createToolbarEvent(host, action)
        ActionUtil.performDumbAwareUpdate(action, event, true)
        assertTrue(
            "${action.templatePresentation.text} must be enabled in the ${host.displayMode} toolbar",
            event.presentation.isEnabledAndVisible
        )
        ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }

    private fun createToolbarEvent(host: DiffHost, action: AnAction): AnActionEvent {
        return AnActionEvent.createEvent(
            action,
            host.dataContext(),
            null,
            ActionPlaces.DIFF_TOOLBAR,
            ActionUiKind.TOOLBAR,
            null
        )
    }

    private fun selectSortMode(host: DiffHost, sortMode: JsonDiffSortMode) {
        val modeAction = host.contextAction<SelectJsonDiffSortModeAction>()
        val optionAction = modeAction.createPopupActionGroup(JPanel(), host.dataContext())
            .getChildren(null)
            .single { it.templatePresentation.text == sortModeText(sortMode) }
        perform(host, optionAction)
    }

    private fun renameSides(
        host: DiffHost,
        leftTitle: String,
        rightTitle: String,
        isConfirmed: Boolean
    ): TitleDialogState {
        var dialogState: TitleDialogState? = null
        UiInterceptors.register(object : UiInterceptors.UiInterceptor<JsonDiffTitlesDialog>(JsonDiffTitlesDialog::class.java) {
            override fun doIntercept(component: JsonDiffTitlesDialog) {
                val leftField = component.preferredFocusedComponent as JBTextField
                val dialogRoot = generateSequence<Component>(leftField) { it.parent }.last()
                val titleFields = UIUtil.findComponentsOfType(dialogRoot as javax.swing.JComponent, JBTextField::class.java)
                assertEquals(2, titleFields.size)
                assertSame(leftField, titleFields[0])
                dialogState = TitleDialogState(
                    leftFieldText = titleFields[0].text,
                    rightFieldText = titleFields[1].text,
                    leftEmptyText = titleFields[0].emptyText.text,
                    rightEmptyText = titleFields[1].emptyText.text
                )
                titleFields[0].text = leftTitle
                titleFields[1].text = rightTitle
                component.close(if (isConfirmed) DialogWrapper.OK_EXIT_CODE else DialogWrapper.CANCEL_EXIT_CODE)
            }
        })
        perform(host, host.contextAction<RenameJsonDiffTitlesAction>())
        return checkNotNull(dialogState) { "Rename dialog was not shown" }
    }

    private fun recordEditorHints(): List<Editor> {
        val hintedEditors = mutableListOf<Editor>()
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
            .subscribe(EditorHintListener.TOPIC, object : EditorHintListener {
                override fun hintShown(editor: Editor, hint: LightweightHint, flags: Int, hintInfo: HintHint) {
                    hintedEditors += editor
                }
            })
        return hintedEditors
    }

    private fun assertTitleLabels(host: DiffHost, leftTitle: String, rightTitle: String) {
        assertEquals(leftTitle, host.session.getTitle(Side.LEFT))
        assertEquals(rightTitle, host.session.getTitle(Side.RIGHT))
        val visibleLabelTexts = UIUtil.findComponentsOfType(host.viewer.component, JBLabel::class.java).map { it.text }
        assertTrue("Left side title '$leftTitle' must be shown: $visibleLabelTexts", leftTitle in visibleLabelTexts)
        assertTrue("Right side title '$rightTitle' must be shown: $visibleLabelTexts", rightTitle in visibleLabelTexts)
    }

    /**
     * JsonDiffExtension은 JSON 감지를 background에서 마친 뒤 listener를 설치한다.
     * 오른쪽에 압축 JSON을 입력해 자동 포맷팅이 실제로 동작할 때까지 기다린다.
     */
    private fun awaitAutoFormatter(host: DiffHost) {
        awaitAutoFormatter(host.rightDocument, "${host.displayMode} viewer")
    }

    private fun awaitAutoFormatter(document: Document, targetName: String) {
        repeat(AUTO_FORMATTER_PROBE_ATTEMPTS) {
            typeText(document, PROBE_INPUT)
            if (waitUntilOrTimeout(AUTO_FORMATTER_PROBE_TIMEOUT_MS) { document.text == PROBE_FORMATTED }) return
        }
        fail("JSONinja automatic formatting was not installed on the $targetName")
    }

    private fun typeText(document: Document, text: String) {
        WriteCommandAction.runWriteCommandAction(project, "Type JSON", null, { document.setText(text) })
    }

    /**
     * 테스트 준비용 입력을 자동 포맷팅 없이 넣는다. JSONinja 자체 쓰기와 같은 guard를 사용한다.
     */
    private fun replaceWithoutAutoFormatting(document: Document, text: String) {
        document.putUserData(JsonDiffKeys.JSON_DIFF_CHANGE_GUARD, true)
        try {
            WriteCommandAction.runWriteCommandAction(project) { document.setText(text) }
        } finally {
            document.putUserData(JsonDiffKeys.JSON_DIFF_CHANGE_GUARD, false)
        }
    }

    private fun assertDocumentMatchesGolden(document: Document, goldenName: String) {
        val expectedText = golden(goldenName).trim()
        waitUntilOrTimeout(DOCUMENT_TIMEOUT_MS) { document.text.trim() == expectedText }
        assertSameLinesWithFile(goldenPath(goldenName), document.text)
    }

    private fun golden(name: String): String = File(testDataPath, name).readText()

    private fun goldenPath(name: String): String = File(testDataPath, name).path

    private fun inputText(name: String): String = golden(name).trim()

    private fun sortModeText(sortMode: JsonDiffSortMode): String {
        return when (sortMode) {
            JsonDiffSortMode.KEY_ASCENDING -> LocalizationBundle.message("action.diff.sort.mode.key.ascending")
            JsonDiffSortMode.LEFT_ORDER -> LocalizationBundle.message("action.diff.sort.mode.left.order")
        }
    }

    private fun selectedModeText(sortMode: JsonDiffSortMode): String {
        return LocalizationBundle.message("action.diff.sort.mode.selected", sortModeText(sortMode))
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        if (!waitUntilOrTimeout(DOCUMENT_TIMEOUT_MS, condition)) {
            fail(message)
        }
    }

    private fun waitUntilOrTimeout(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadlineMs = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (condition()) return true
            if (System.currentTimeMillis() > deadlineMs) return false
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(EVENT_POLL_INTERVAL_MS)
        }
    }

    private fun pumpEvents(durationMs: Long) {
        waitUntilOrTimeout(durationMs) { false }
    }

    private companion object {
        const val DEFAULT_INDENT_SIZE = 2
        const val DOCUMENT_TIMEOUT_MS = 10_000L
        // 300ms debounce, background 계산, EDT 적용이 모두 끝날 만큼의 관찰 시간
        const val QUIET_PERIOD_MS = 1_200L
        // debounce가 막 끝나 첫 포맷팅 작업이 시작될 무렵
        const val DEBOUNCE_EDGE_MS = 320L
        const val HOST_CLOSE_SETTLE_MS = 100L
        const val EVENT_POLL_INTERVAL_MS = 10L
        const val AUTO_FORMATTER_PROBE_ATTEMPTS = 8
        const val AUTO_FORMATTER_PROBE_TIMEOUT_MS = 1_500L
        const val PROBE_INPUT = """{"probe":1}"""
        const val PROBE_FORMATTED = "{\n  \"probe\": 1\n}"
    }
}
