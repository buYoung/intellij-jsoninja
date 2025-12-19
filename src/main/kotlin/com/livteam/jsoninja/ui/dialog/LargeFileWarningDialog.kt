package com.livteam.jsoninja.ui.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import java.awt.Dimension
import java.text.DecimalFormat
import javax.swing.JComponent

/**
 * 성능에 영향을 줄 수 있는 대용량 JSON 파일에 대해 사용자에게 경고하는 Dialog.
 * 사용자의 선호도를 기억하고 다시 경고를 표시하지 않는 옵션을 제공합니다.
 */
class LargeFileWarningDialog(
    private val project: Project,
    private val fileSizeBytes: Long,
    private val fileName: String? = null
) : DialogWrapper(project) {

    private val dontShowAgainCheckBox = JBCheckBox(LocalizationBundle.message("warning.large.file.dont.show.again"))
    private val settings = JsoninjaSettingsState.getInstance(project)

    companion object {
        /**
         * setting에서 warning이 활성된 경우 대용량 파일 경고 dialog를 표시합니다.
         *
         * @param project 현재 project
         * @param fileSizeBytes 파일 크기 (byte 단위)
         * @param fileName context를 위한 선택적 파일 이름
         * @return 사용자가 계속 진행을 선택한 경우 true, 취소하거나 warning이 비활성화된 경우 false
         */
        fun showWarningIfNeeded(
            project: Project,
            fileSizeBytes: Long,
            fileName: String? = null
        ): Boolean {
            val settings = JsoninjaSettingsState.getInstance(project)
            val thresholdBytes = settings.largeFileThresholdMB * 1024 * 1024L

            // 파일이 임계값보다 작거나 warning이 비활성화된 경우 계속 진행
            if (fileSizeBytes < thresholdBytes || !settings.showLargeFileWarning) {
                return true
            }

            val dialog = LargeFileWarningDialog(project, fileSizeBytes, fileName)
            dialog.show()

            return dialog.isOK
        }
    }

    init {
        title = LocalizationBundle.message("warning.large.file.title")
        setOKButtonText(LocalizationBundle.message("warning.large.file.proceed"))
        setCancelButtonText(LocalizationBundle.message("warning.large.file.cancel"))
        init()
    }

    override fun createCenterPanel(): JComponent {
        val fileSizeMB = fileSizeBytes / (1024.0 * 1024.0)
        val formatter = DecimalFormat("#.##")
        val formattedSize = formatter.format(fileSizeMB)

        val message = LocalizationBundle.message("warning.large.file.message", formattedSize)

        return panel {
            row {
                icon(com.intellij.icons.AllIcons.General.WarningDialog)
                    .align(com.intellij.ui.dsl.builder.AlignY.TOP)
                cell(JBLabel("<html><body style='width: 300px'>$message</body></html>"))
                    .align(com.intellij.ui.dsl.builder.AlignY.TOP)
            }.layout(com.intellij.ui.dsl.builder.RowLayout.PARENT_GRID)

            if (fileName != null) {
                row {
                    text("File: $fileName")
                        .comment("Size: ${formattedSize} MB")
                }
            } else {
                row {
                    text("Size: ${formattedSize} MB")
                }
            }

            separator()

            row {
                cell(dontShowAgainCheckBox)
            }
        }.apply {
            preferredSize = Dimension(400, 130)
        }
    }

    override fun doOKAction() {
        // 사용자가 "다시 표시하지 않기"를 체크한 경우, setting에서 warning 비활성화
        if (dontShowAgainCheckBox.isSelected) {
            settings.showLargeFileWarning = false
        }
        super.doOKAction()
    }
}
