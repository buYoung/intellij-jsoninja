package com.livteam.jsonhelper2.toolWindow.component

import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.LanguageTextField
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * JSON 편집을 위한 커스텀 에디터 컴포넌트
 * IntelliJ의 LanguageTextField를 활용하여 JSON 문법 지원 및 편집 기능을 제공
 */
class JsonEditor(private val project: Project) : JPanel(), Disposable {
    companion object {
        private const val PLACEHOLDER_TEXT = "Enter JSON here..."
        private const val EMPTY_TEXT = ""
    }

    private val editor: EditorTextField = createJsonEditor()

    init {
        initializeUI()
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
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                editor.text = text
            }
        }
    }

    /**
     * 현재 에디터의 텍스트를 반환합니다.
     * @return 현재 JSON 텍스트
     */
    fun getText(): String = editor.text

    override fun dispose() {
        removeAll()
    }
}