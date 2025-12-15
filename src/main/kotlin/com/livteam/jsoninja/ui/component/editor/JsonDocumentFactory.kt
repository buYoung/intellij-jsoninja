package com.livteam.jsoninja.ui.component.editor

import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.util.LocalTimeCounter

/**
 * JSON Document 생성을 위한 인터페이스
 */
interface JsonDocumentCreator {
    fun createDocument(value: String, project: Project?, fileExtension: String? = null): Document
}

/**
 * PsiFile 기반의 JSON Document 생성 구현체
 */
class SimpleJsonDocumentCreator : JsonDocumentCreator {
    override fun createDocument(value: String, project: Project?, fileExtension: String?): Document {
        return JsonDocumentFactory.createJsonDocument(value, project, this, fileExtension)
    }

    fun customizePsiFile(file: PsiFile) {
        // 하위 클래스에서 필요시 구현
    }
}

object JsonDocumentFactory {
    val JSONINJA_EDITOR_KEY = Key.create<Boolean>("JSONINJA_EDITOR_KEY")

    /**
     * PsiFile 기반 JSON Document 생성
     */
    fun createJsonDocument(
        value: String,
        project: Project?,
        documentCreator: SimpleJsonDocumentCreator,
        fileExtension: String? = null
    ): Document {
        val extensionToUse = fileExtension ?: "json5"
        var fileType = FileTypeManager.getInstance().getFileTypeByExtension(extensionToUse)

        if (fileType is UnknownFileType) {
            fileType = JsonLanguage.INSTANCE.associatedFileType ?: JsonFileType.INSTANCE
        }

        val notNullProject = project ?: ProjectManager.getInstance().defaultProject
        val factory = PsiFileFactory.getInstance(notNullProject)

        val stamp = LocalTimeCounter.currentTime()
        val psiFile = ReadAction.compute<PsiFile, RuntimeException> {
            factory.createFileFromText(
                "Dummy." + (fileType.defaultExtension.takeIf { it.isNotEmpty() } ?: extensionToUse),
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

        val finalDocument = document ?: EditorFactory.getInstance().createDocument(value)
        finalDocument.putUserData(JSONINJA_EDITOR_KEY, true)
        return finalDocument
    }
}
