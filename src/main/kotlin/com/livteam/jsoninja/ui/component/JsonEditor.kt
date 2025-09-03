package com.livteam.jsoninja.ui.component

import com.intellij.json.JsonFileType
import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.EditorTextField
import com.intellij.util.LocalTimeCounter
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * JSON Document 생성을 위한 인터페이스
 */
interface JsonDocumentCreator {
    fun createDocument(value: String, project: Project?): Document
}

/**
 * PsiFile 기반의 JSON Document 생성 구현체
 */
class SimpleJsonDocumentCreator : JsonDocumentCreator {
    override fun createDocument(value: String, project: Project?): Document {
        return JsonEditor.createJsonDocument(value, project, this)
    }

    fun customizePsiFile(file: PsiFile) {
        // 하위 클래스에서 필요시 구현
    }
}

/**
 * JSON 편집을 위한 커스텀 에디터 컴포넌트
 * IntelliJ의 EditorTextField를 활용하여 JSON 문법 지원 및 편집 기능을 제공
 */
class JsonEditor(
    private val project: Project,
    private val documentCreator: JsonDocumentCreator = SimpleJsonDocumentCreator()
) : JPanel(), Disposable {
    companion object {
        private const val EMPTY_TEXT = ""

        /**
         * 국제화를 지원하기위해 const val 대신 var로 사용
         */
        private var PLACEHOLDER_TEXT = LocalizationBundle.message("enterJsonHere")

        /**
         * PsiFile 기반 JSON Document 생성
         */
        fun createJsonDocument(
            value: String,
            project: Project?,
            documentCreator: SimpleJsonDocumentCreator
        ): Document {
            val language = JsonLanguage.INSTANCE
            val fileType = language.associatedFileType ?: JsonFileType.INSTANCE

            val notNullProject = project ?: ProjectManager.getInstance().defaultProject
            val factory = PsiFileFactory.getInstance(notNullProject)

            val stamp = LocalTimeCounter.currentTime()
            val psiFile = ReadAction.compute<PsiFile, RuntimeException> {
                factory.createFileFromText(
                    "Dummy." + fileType.defaultExtension,
                    fileType,
                    value,
                    stamp,
                    true,
                    false
                )
            }

            documentCreator.customizePsiFile(psiFile)

            val document = ReadAction.compute<Document?, RuntimeException> {
                PsiDocumentManager.getInstance(notNullProject).getDocument(psiFile)
            }

            return document ?: EditorFactory.getInstance().createDocument(value)
        }
    }

    private var originalJson: String = ""
    private var onContentChangeCallback: ((String) -> Unit)? = null
    private var isSettingText = false
    private var lastClipboardContent = ""

    private val editor: EditorTextField = createJsonEditor()
    private val formatterService = project.getService(JsonFormatterService::class.java)
    private val settings = JsoninjaSettingsState.getInstance(project)

    init {
        initializeUI()
        setupContentChangeListener()
        setupClipboardMonitoring()
    }

    /**
     * 향상된 자동 포맷팅 설정 - Document listener 기반
     */
    private fun setupClipboardMonitoring() {
        // 추가적인 document listener를 통해 paste 감지
        val documentListener = object : com.intellij.openapi.editor.event.DocumentListener {
            private var pendingFormatting = false

            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                if (isSettingText || pendingFormatting) return

                val changeLength = event.newFragment.length

                if (changeLength > 6) {
                    val insertedText = event.newFragment.toString()
                    ApplicationManager.getApplication().invokeLater({
                        handlePotentialPasteContent(insertedText)
                    }, ModalityState.defaultModalityState())
                }

            }
        }

        // Document listener 등록
        editor.addDocumentListener(documentListener)

        // Disposer 등록으로 정리
        com.intellij.openapi.util.Disposer.register(this) {
            editor.removeDocumentListener(documentListener)
        }
    }

    /**
     * 붙여넣기 가능성이 있는 내용에 대한 포맷팅 처리
     */
    private fun handlePotentialPasteContent(insertedText: String) {
        if (insertedText.isBlank()) return

        try {
            // JSON인지 간단히 확인
            val trimmed = insertedText.trim()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                return
            }

            val pasteFormatState = JsonFormatState.fromString(settings.pasteFormatState)
            val formattedText = formatterService.formatJson(insertedText, pasteFormatState)

            // 포맷팅이 실제로 변경되었을 때만 적용
            if (formattedText != insertedText && formattedText.trim() != insertedText.trim()) {
                WriteCommandAction.runWriteCommandAction(project) {
                    val currentText = editor.text
                    val newText = currentText.replace(insertedText, formattedText)
                    if (newText != currentText) {
                        isSettingText = true
                        editor.text = newText
                        isSettingText = false
                    }
                }
            }
        } catch (e: Exception) {
            // 포맷팅 실패 시 무시
        }
    }

    /**
     * 에디터 내용 변경 리스너 설정
     */
    private fun setupContentChangeListener() {
        val contentChangeListener = object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                val content = editor.text
                if (isSettingText) {
                    return
                }
                onContentChangeCallback?.invoke(content)
            }
        }

        editor.addDocumentListener(contentChangeListener)

        // Disposer 등록으로 정리
        com.intellij.openapi.util.Disposer.register(this) {
            editor.removeDocumentListener(contentChangeListener)
        }
    }


    private fun createJsonEditor(): EditorTextField {
        val document = documentCreator.createDocument(EMPTY_TEXT, project)
        val fileType = JsonLanguage.INSTANCE.associatedFileType ?: JsonFileType.INSTANCE

        return EditorTextField(document, project, fileType, false, false).apply {

            addSettingsProvider { editor ->
                editor.settings.applyEditorSettings()
                editor.colorsScheme = EditorColorsManager.getInstance().globalScheme
                editor.backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground
                editor.highlighter = HighlighterFactory.createHighlighter(project, fileType)

                editor.isEmbeddedIntoDialogWrapper = true
                editor.setHorizontalScrollbarVisible(true)
                editor.setVerticalScrollbarVisible(true)
            }
            setPlaceholder(PLACEHOLDER_TEXT)
            putClientProperty(EditorTextField.SUPPLEMENTARY_KEY, true)

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
        removeAll()
    }
}
