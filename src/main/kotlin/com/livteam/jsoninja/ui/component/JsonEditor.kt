package com.livteam.jsoninja.ui.component

import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.LanguageTextField
import com.livteam.jsoninja.LocalizationBundle
import javax.swing.JPanel
import java.awt.BorderLayout

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

    private val editor: EditorTextField = createJsonEditor()
    private var originalJson: String = ""
    private var onContentChangeCallback: ((String) -> Unit)? = null
    private var isSettingText = false

    init {
        initializeUI()
        setupContentChangeListener()
    }

    /**
     * 에디터 내용 변경 리스너 설정
     */
    private fun setupContentChangeListener() {
        editor.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                val content = editor.text
                // setText 메서드에 의한 변경은 무시
                if (!isSettingText) {
                    onContentChangeCallback?.invoke(content)
                }
            }
        })
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
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().invokeLater({
                WriteCommandAction.runWriteCommandAction(project) {
                    editor.text = text
                    isSettingText = false
                }
            }, ModalityState.any())
        }
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
        removeAll()
    }
}