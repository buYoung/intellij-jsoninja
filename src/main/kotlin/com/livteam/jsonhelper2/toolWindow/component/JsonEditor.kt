package com.livteam.jsonhelper2.toolWindow.component

import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.components.panels.NonOpaquePanel
import java.awt.BorderLayout
import javax.swing.JComponent

class JsonEditor(private val project: Project) : NonOpaquePanel(), Disposable {
    private val psiFile: PsiFile
    private val virtualFile: VirtualFile
    private val document: Document
    private val editor: EditorEx
    private val fileEditor: FileEditor

    init {
        layout = BorderLayout()
        
        // 1. PSI 파일 생성
        psiFile = createPsiFile()
        virtualFile = psiFile.virtualFile
        
        // 2. Document 생성
        document = EditorFactory.getInstance().createDocument("")
        
        // 3. Editor 생성
        editor = EditorFactory.getInstance().createEditor(
            document,
            project,
            JsonFileType.INSTANCE,
            false
        ) as EditorEx
        
        // 4. FileEditor 생성
        fileEditor = PsiAwareTextEditorProvider().createEditor(project, virtualFile)
        
        // 5. 에디터 설정
        setupEditor()
        
        // 6. UI 구성
        add(editor.component, BorderLayout.CENTER)
    }

    private fun setupEditor() {
        editor.settings.apply {
            isLineNumbersShown = true
            isWhitespacesShown = true
            isLineMarkerAreaShown = true
            isIndentGuidesShown = true
            isFoldingOutlineShown = true
            isRightMarginShown = true
            additionalLinesCount = 0
            additionalColumnsCount = 0
            isUseSoftWraps = true
        }
    }

    private fun createFileEditor(virtualFile: VirtualFile): FileEditor {
        val fileEditor = PsiAwareTextEditorProvider().createEditor(project, virtualFile)

    }

    private fun createPsiFile(): PsiFile {
        val fileType = JsonFileType.INSTANCE

        return PsiFileFactory.getInstance(project).createFileFromText(
            "tmp.${fileType.defaultExtension}",
            fileType.language,
            "",
            true,
            false,
            false
        )
    }

    fun getComponent(): JComponent = this

    fun setText(text: String) {
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                document.setText(text)
            }
        }
    }


    fun getText(): String = document.text

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
        removeAll()
    }
}