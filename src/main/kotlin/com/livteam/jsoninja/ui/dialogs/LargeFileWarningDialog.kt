package com.livteam.jsoninja.ui.dialogs

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
 * Dialog to warn users about large JSON files that may impact performance.
 * Provides an option to remember the user's preference and not show the warning again.
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
         * Shows the large file warning dialog if warnings are enabled in settings.
         *
         * @param project The current project
         * @param fileSizeBytes Size of the file in bytes
         * @param fileName Optional file name for context
         * @return true if user chose to proceed, false if cancelled or warnings are disabled
         */
        fun showWarningIfNeeded(
            project: Project,
            fileSizeBytes: Long,
            fileName: String? = null
        ): Boolean {
            val settings = JsoninjaSettingsState.getInstance(project)
            val thresholdBytes = settings.largeFileThresholdMB * 1024 * 1024L

            // If file is smaller than threshold or warnings are disabled, proceed
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
        // If user checked "don't show again", disable warnings in settings
        if (dontShowAgainCheckBox.isSelected) {
            settings.showLargeFileWarning = false
        }
        super.doOKAction()
    }
}
