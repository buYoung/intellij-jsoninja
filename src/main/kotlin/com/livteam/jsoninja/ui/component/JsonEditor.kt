package com.livteam.jsoninja.ui.component

import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.ui.EditorTextField
import com.intellij.ui.LanguageTextField
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.ide.CopyPasteManager
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor

/**
 * JSON 편집을 위한 커스텀 에디터 컴포넌트
 * IntelliJ의 LanguageTextField를 활용하여 JSON 문법 지원 및 편집 기능을 제공
 */
class JsonEditor(private val project: Project) : JPanel(), Disposable {
    companion object {
        private const val EMPTY_TEXT = ""

        /**
         * 국제화를 지원하기위해 const val 대신 var로 사용
         */
        private var PLACEHOLDER_TEXT = LocalizationBundle.message("enterJsonHere")
    }

    private var originalJson: String = ""
    private var onContentChangeCallback: ((String) -> Unit)? = null
    private var isSettingText = false

    // 원래 Paste 핸들러를 저장하기 위한 변수
    private var originalPasteHandler: EditorActionHandler? = null
    private val pasteActionId = IdeActions.ACTION_EDITOR_PASTE

    private val editor: EditorTextField = createJsonEditor()
    private val editorActionManager = EditorActionManager.getInstance()
    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val settings = JsoninjaSettingsState.getInstance(project)

    init {
        initializeUI()
        setupContentChangeListener()

    }

    /**
     * EditorActionHandler를 교체하여 붙여넣기 이벤트를 감지하는 내부 클래스
     */
    private inner class CustomPasteHandler(private val originalHandler: EditorActionHandler?) : EditorActionHandler() {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
            if (editor !== this@JsonEditor.editor.editor) {
                originalHandler?.execute(editor, caret, dataContext)
                return
            }

            val clipboardText = try {
                CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
            } catch (e: Exception) {
                println("Failed to get clipboard content: ${e.message}")
                originalHandler?.execute(editor, caret, dataContext)
                ""
            }

            if (clipboardText == null) {
                originalHandler?.execute(editor, caret, dataContext)
                return
            }

            val isEmptyClipboard = clipboardText.isBlank() || clipboardText.isEmpty()

            if (isEmptyClipboard) {
                // 포맷팅 대상 아니거나 실패 시 원래 핸들러 실행
                originalHandler?.execute(editor, caret, dataContext)
                return
            }

            try {
                // Use the paste format setting from settings
                val pasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)
                val formattedText = formatterService.formatJson(clipboardText, pasteFormatState)

                if (formattedText == clipboardText) {
                    originalHandler?.execute(editor, caret, dataContext)
                    return
                }

                // WriteCommandAction 내에서 Document API 사용하여 수정
                WriteCommandAction.runWriteCommandAction(project) {
                    val document: Document = editor.document
                    val selectionModel = editor.selectionModel

                    if (selectionModel.hasSelection()) {
                        val start: Int = selectionModel.selectionStart
                        val end: Int = selectionModel.selectionEnd
                        document.replaceString(start, end, formattedText)
                    } else {
                        val offset: Int = editor.caretModel.offset
                        document.insertString(offset, formattedText)
                        selectionModel.setSelection(offset, offset + formattedText.length)
                    }

                    // 선택 해제 (선택 교체 후 필요시)
                    selectionModel.removeSelection()
                }
                return
            } catch (e: Exception) {
                originalHandler?.execute(editor, caret, dataContext)
                return
            }
        }

        override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
            return originalHandler?.isEnabled(editor, caret, dataContext) ?: super.isEnabledForCaret(editor, caret, dataContext)
        }
    }

    /**
     * 에디터 내용 변경 리스너 설정
     */
    private fun setupContentChangeListener() {
        editor.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                val content = editor.text
                if (isSettingText) {
                    return
                }

                onContentChangeCallback?.invoke(content)
            }
        })
    }

    /**
     * 붙여넣기 액션 핸들러 설정 (ActionHandler 교체 방식)
     */
    private fun setupPasteListener() {
        // 이미 핸들러가 교체되었다면 중복 실행 방지
        if (originalPasteHandler == null) {
            originalPasteHandler = editorActionManager.getActionHandler(pasteActionId)
            editorActionManager.setActionHandler(pasteActionId, CustomPasteHandler(originalPasteHandler))
        }
    }

    /**
     * 원래 붙여넣기 핸들러 복원
     */
    private fun restoreOriginalPasteHandler() {
        if (originalPasteHandler != null) {
            // 현재 핸들러가 우리가 설치한 핸들러인지 확인 후 복원 (안전 장치)
            if (editorActionManager.getActionHandler(pasteActionId) is CustomPasteHandler) {
                editorActionManager.setActionHandler(pasteActionId, originalPasteHandler!!)
            }
            originalPasteHandler = null
        }
    }

    private fun createJsonEditor(): LanguageTextField =
        LanguageTextField(
            JsonLanguage.INSTANCE,
            project,
            EMPTY_TEXT
        ).apply {
            configureEditorSettings()
            setOneLineMode(false)
            setPlaceholder(PLACEHOLDER_TEXT)
            putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)
        }

    override fun addNotify() {
        super.addNotify()
        // 컴포넌트가 화면에 표시될 준비가 되면 내부 Editor가 생성되었을 가능성이 높음
        // 이 시점에 리스너 설정 시도
        if (editor.editor == null) {
            println("Editor not ready at addNotify for ActionHandler setup.")
            return
        }

        setupPasteListener()
    }

    // removeNotify는 컴포넌트가 화면 계층에서 제거될 때 호출됨
    override fun removeNotify() {
        // 핸들러 복원 시점을 dispose 대신 removeNotify로 변경하여
        // 컴포넌트가 화면에서 사라질 때 원래대로 돌려놓도록 함
        restoreOriginalPasteHandler()
        super.removeNotify()
    }


    private fun LanguageTextField.configureEditorSettings() {
        addSettingsProvider { editor ->
            editor.settings.applyEditorSettings()
            editor.colorsScheme = EditorColorsManager.getInstance().globalScheme
            editor.backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground
        }
    }

    private fun EditorSettings.applyEditorSettings() {
        isLineNumbersShown = true
        isWhitespacesShown = true
        isCaretRowShown = true
        isRightMarginShown = true
        isUseSoftWraps = true
        isIndentGuidesShown = true
        isFoldingOutlineShown = true
    }

    private fun initializeUI() {
        layout = BorderLayout()
        add(editor, BorderLayout.CENTER)
    }

    /**
     * 에디터의 텍스트를 설정합니다.
     * @param text 설정할 JSON 텍스트
     */
    fun setText(text: String) {
        isSettingText = true
        ApplicationManager.getApplication().invokeLater({
            WriteCommandAction.runWriteCommandAction(project) {
                editor.text = text
                isSettingText = false
            }
        }, ModalityState.any())
    }

    /**
     * 현재 에디터의 텍스트를 반환합니다.
     * @return 현재 JSON 텍스트
     */
    fun getText(): String = editor.text

    /**
     * 원본 JSON 설정
     * @param json 원본 JSON 문자열
     */
    fun setOriginalJson(json: String) {
        originalJson = json
    }

    /**
     * 원본 JSON 반환
     * @return 원본 JSON 문자열
     */
    fun getOriginalJson(): String {
        return originalJson
    }

    /**
     * 에디터 내용 변경 콜백 설정
     * @param callback 변경된 내용을 매개변수로 받는 함수
     */
    fun setOnContentChangeCallback(callback: (String) -> Unit) {
        onContentChangeCallback = callback
    }

    override fun dispose() {
        restoreOriginalPasteHandler()
        removeAll()
    }
}
