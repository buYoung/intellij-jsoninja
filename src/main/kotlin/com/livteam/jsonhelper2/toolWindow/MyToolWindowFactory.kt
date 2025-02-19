package com.livteam.jsonhelper2.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.SearchTextField
import com.intellij.ui.content.ContentFactory
import com.livteam.jsonhelper2.MyBundle
import com.livteam.jsonhelper2.services.MyProjectService
import javax.swing.Icon
import javax.swing.JComponent
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBScrollPane
import com.livteam.jsonhelper2.toolWindow.component.JsonEditor
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindowTest = MyToolWindowTest(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindowTest.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindowTest(toolWindow: ToolWindow) {
        private val service = toolWindow.project.service<MyProjectService>()
        private val jmesPathField = SearchTextField()
        private val jsonEditor = JsonEditor(toolWindow.project)

        init {
            jmesPathField.textEditor.emptyText.text = "Enter JMES Path..."
            // 테스트용 JSON 데이터 설정
            jsonEditor.setText("""
                {
                    "name": "JSON Helper 2",
                    "version": "1.0.0",
                    "description": "JSON processing plugin for JetBrains IDEs",
                    "features": [
                        "JSON Prettify",
                        "JSON Uglify",
                        "JSON Escape",
                        "JSON Unescape",
                        "JMES Path"
                    ]
                }
            """.trimIndent())
        }

        fun getContent(): JComponent = panel {
            row {
                cell(createIconPanel())
                    .align(AlignX.LEFT)
                cell(createContentPanel())
                    .align(AlignX.FILL)
                    .align(AlignY.FILL)
                    .resizableColumn()
            }.resizableRow()
        }

        private fun createIconPanel(): JComponent = panel {
            row {
                icon(AllIcons.Actions.Find)
            }
            row {
                icon(AllIcons.Actions.Execute)
            }
            row {
                icon(AllIcons.Actions.Edit)
            }
        }

        private fun createContentPanel(): JComponent = panel {
            row {
                cell(jmesPathField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
            row {
                val editorComponent = jsonEditor.getComponent()
                editorComponent.preferredSize = Dimension(400, 300)
                cell(JBScrollPane(editorComponent))
                    .align(AlignX.FILL)
                    .align(AlignY.FILL)
                    .resizableColumn()
            }.resizableRow()
        }
    }
}
