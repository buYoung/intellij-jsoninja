package com.livteam.jsoninja.ui.diff

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.livteam.jsoninja.LocalizationBundle
import java.awt.Component
import javax.swing.JComponent

/**
 * JSON Diff 양쪽 이름을 함께 편집하는 dialog.
 * 확인했을 때만 호출자가 두 이름을 적용하며, 비어 있는 이름은 기본 이름을 뜻합니다.
 */
class JsonDiffTitlesDialog(
    project: Project,
    parentComponent: Component?,
    initialLeftTitle: String?,
    initialRightTitle: String?,
    defaultLeftTitle: String,
    defaultRightTitle: String
) : DialogWrapper(project, parentComponent, false, IdeModalityType.IDE) {

    private val leftTitleField = JBTextField(initialLeftTitle.orEmpty(), 24)
    private val rightTitleField = JBTextField(initialRightTitle.orEmpty(), 24)

    val leftTitle: String
        get() = leftTitleField.text

    val rightTitle: String
        get() = rightTitleField.text

    init {
        title = LocalizationBundle.message("dialog.json.diff.titles.title")
        leftTitleField.emptyText.text = defaultLeftTitle
        rightTitleField.emptyText.text = defaultRightTitle
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row(LocalizationBundle.message("dialog.json.diff.titles.left")) {
                cell(leftTitleField).align(AlignX.FILL)
            }
            row(LocalizationBundle.message("dialog.json.diff.titles.right")) {
                cell(rightTitleField).align(AlignX.FILL)
            }
            row {
                comment(LocalizationBundle.message("dialog.json.diff.titles.comment"))
            }
        }
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return leftTitleField
    }
}
