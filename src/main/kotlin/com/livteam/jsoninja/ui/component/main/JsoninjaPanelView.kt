package com.livteam.jsoninja.ui.component.main

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.livteam.jsoninja.ui.component.tab.JsonTabsView
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

class JsoninjaPanelView(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {
    private val tabsView = JsonTabsView()

    val presenter = JsoninjaPanelPresenter(project, this, tabsView)

    init {
        setupUI()
    }

    private fun setupUI() {
        // 초기 탭 추가
        presenter.initialize()

        // Add content
        val contentPanel = JPanel(BorderLayout()).apply {
            add(JSeparator(SwingConstants.VERTICAL), BorderLayout.WEST)
            add(tabsView, BorderLayout.CENTER)
        }

        // Setup toolbar and content
        toolbar = JsoninjaToolbarFactory.create(this)
        setContent(contentPanel)
    }

    override fun dispose() {
        // JsonHelperTabbedPane와 하위 탭 Disposable 들의 부모 역할만 수행하므로 별도 작업 없음
    }
}
