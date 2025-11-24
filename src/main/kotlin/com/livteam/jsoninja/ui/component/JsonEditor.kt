package com.livteam.jsoninja.ui.component

import com.intellij.json.JsonFileType
import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.livteam.jsoninja.utils.JsonHelperUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.EditorTextField
import com.intellij.util.LocalTimeCounter
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import javax.swing.JPanel
import java.awt.BorderLayout
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.livteam.jsoninja.util.JsonPathHelper
import com.intellij.openapi.util.Key
import javax.swing.SwingUtilities

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.components.service
import com.intellij.ui.PopupHandler
import com.livteam.jsoninja.actions.CopyJsonQueryAction
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.services.JsonObjectMapperService

/**
 * JSON Document 생성을 위한 인터페이스
 */
interface JsonDocumentCreator {
    fun createDocument(value: String, project: Project?, extension: String? = null): Document
}

/**
 * PsiFile 기반의 JSON Document 생성 구현체
 */
class SimpleJsonDocumentCreator : JsonDocumentCreator {
    override fun createDocument(value: String, project: Project?, extension: String?): Document {
        return JsonEditor.createJsonDocument(value, project, extension, this)
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
    private val extension: String? = null,
    private val initialContent: String = "",
    private val documentCreator: JsonDocumentCreator = SimpleJsonDocumentCreator()
) : JPanel(), Disposable {
    companion object {
        private const val EMPTY_TEXT = ""

        val JSONINJA_EDITOR_KEY = Key.create<Boolean>("JSONINJA_EDITOR_KEY")

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
            extension: String?,
            documentCreator: SimpleJsonDocumentCreator
        ): Document {
            val language = JsonLanguage.INSTANCE
            val defaultJsonFileType = language.associatedFileType ?: JsonFileType.INSTANCE
            val jsonObjectMapperService = service<JsonObjectMapperService>()

            // 확장자가 지정되지 않은 경우 콘텐츠 기반 감지 또는 기본값(JSON5) 사용
            var targetExtension = extension
            if (targetExtension == null) {
                targetExtension = if (JsonHelperUtils.isJsonL(value, jsonObjectMapperService)) "jsonl" else "json5"
            }

            val fileTypeCandidate = FileTypeManager.getInstance().getFileTypeByExtension(targetExtension)

            // 해당 확장자의 FileType이 없으면(Unknown) 기본 JSON으로 폴백
            val (finalFileType, finalExtension) = if (fileTypeCandidate is UnknownFileType) {
                defaultJsonFileType to defaultJsonFileType.defaultExtension
            } else {
                fileTypeCandidate to targetExtension
            }

            val notNullProject = project ?: ProjectManager.getInstance().defaultProject
            val factory = PsiFileFactory.getInstance(notNullProject)

            val stamp = LocalTimeCounter.currentTime()
            val psiFile = ReadAction.compute<PsiFile, RuntimeException> {
                factory.createFileFromText(
                    "Dummy.$finalExtension",
                    finalFileType,
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

            val finalDocument = document ?: EditorFactory.getInstance().createDocument(value)
            finalDocument.putUserData(JSONINJA_EDITOR_KEY, true)
            return finalDocument
        }
    }

    private var originalJson: String = ""
    private var onContentChangeCallback: ((String) -> Unit)? = null
    private var isSettingText = false

    private val editor: EditorTextField = createJsonEditor()
    private val settings = JsoninjaSettingsState.getInstance(project)
    private val jsonObjectMapperService = service<JsonObjectMapperService>()

    init {
        initializeUI()
        setupContentChangeListener()
        setupMouseListener()
    }

    private fun setupMouseListener() {
        EditorFactory.getInstance().eventMulticaster.addEditorMouseMotionListener(object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) {
                if (e.editor.project != project) return

                // Check if the event comes from our editor
                // We check if the editor component is a child of this JsonEditor panel
                if (!SwingUtilities.isDescendingFrom(e.editor.component, this@JsonEditor)) {
                    return
                }

                val event = e.mouseEvent
                val isModifierDown = if (SystemInfo.isMac) event.isMetaDown else event.isControlDown

                if (isModifierDown) {
                    val offset = e.offset
                    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(e.editor.document)
                    if (psiFile != null) {
                        val element = psiFile.findElementAt(offset)

                        if (element != null) {
                            val queryType = JsonQueryType.fromString(settings.jsonQueryType)
                            val path = when (queryType) {
                                JsonQueryType.JMESPATH -> JsonPathHelper.getJmesPath(element)
                                JsonQueryType.JAYWAY_JSONPATH -> JsonPathHelper.getJsonPath(element)
                            }

                            if (path != null) {
                                val label = when (queryType) {
                                    JsonQueryType.JMESPATH -> "JMESPath"
                                    JsonQueryType.JAYWAY_JSONPATH -> "Jayway JsonPath"
                                }
                                val text = "<html>$label: <b>$path</b></html>"
                                (e.editor as? EditorEx)?.contentComponent?.toolTipText = text
                            } else {
                                (e.editor as? EditorEx)?.contentComponent?.toolTipText = null
                            }
                        }
                    } else {
                        (e.editor as? EditorEx)?.contentComponent?.toolTipText = null
                    }
                } else {
                    (e.editor as? EditorEx)?.contentComponent?.toolTipText = null
                }
            }
        }, this)
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
            // 상위 탭 Disposable 체인 덕분에 listener는 탭 수명과 함께 해제된다.
            editor.removeDocumentListener(contentChangeListener)
        }
    }


    private fun createJsonEditor(): EditorTextField {
        // 확장자 결정 로직: 명시된 확장자가 없으면 콘텐츠 기반 감지 또는 기본값(JSON5)
        val targetExtension =
            extension ?: if (JsonHelperUtils.isJsonL(initialContent, jsonObjectMapperService)) "jsonl" else "json5"

        // 결정된 확장자로 Document 생성
        val document = documentCreator.createDocument(initialContent, project, targetExtension)

        // EditorTextField 생성을 위한 FileType 결정
        val fileTypeCandidate = FileTypeManager.getInstance().getFileTypeByExtension(targetExtension)
        val fileType = if (fileTypeCandidate is UnknownFileType) {
            JsonLanguage.INSTANCE.associatedFileType ?: JsonFileType.INSTANCE
        } else {
            fileTypeCandidate
        }

        return EditorTextField(document, project, fileType, false, false).apply {

            addSettingsProvider { editor ->
                editor.settings.applyEditorSettings()
                editor.colorsScheme = EditorColorsManager.getInstance().globalScheme
                editor.backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground
                editor.highlighter = HighlighterFactory.createHighlighter(project, fileType)

                editor.isEmbeddedIntoDialogWrapper = true
                editor.setHorizontalScrollbarVisible(true)
                editor.setVerticalScrollbarVisible(true)

                // Install context menu handler
                val actionManager = ActionManager.getInstance()
                val group = DefaultActionGroup()

                // Add Copy and Paste actions
                val copyAction = actionManager.getAction(IdeActions.ACTION_COPY)
                if (copyAction != null) {
                    group.add(copyAction)
                }

                val pasteAction = actionManager.getAction(IdeActions.ACTION_PASTE)
                if (pasteAction != null) {
                    group.add(pasteAction)
                }

                // Add Copy JSON Query action
                val copyJsonQueryAction = CopyJsonQueryAction()
                copyJsonQueryAction.templatePresentation.text = LocalizationBundle.message("action.copy.json.query")
                copyJsonQueryAction.templatePresentation.description =
                    LocalizationBundle.message("action.copy.json.query.description")

                if (group.childrenCount > 0) {
                    group.addSeparator()
                }
                group.add(copyJsonQueryAction)

                if (group.childrenCount > 0) {
                    PopupHandler.installPopupMenu(
                        editor.contentComponent,
                        group,
                        "com.livteam.jsoninja.action.group.EditorPopup"
                    )
                }
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
        WriteCommandAction.runWriteCommandAction(project) {
            editor.text = text
            isSettingText = false
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
