package com.livteam.jsoninja.ui.component.main

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.livteam.jsoninja.ui.component.tab.JsonTabsView

class JsoninjaPanelView(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {
    companion object {
        val JSONINJA_PANEL_DATA_KEY: DataKey<JsoninjaPanelView> = DataKey.create("com.livteam.jsoninja.panel")
    }

    private val tabsView = JsonTabsView(project, this)

    val presenter = JsoninjaPanelPresenter(project, this, tabsView)

    init {
        setupUI()
    }

    private fun setupUI() {
        // 초기 탭 추가
        presenter.initialize()

        // Setup toolbar and content
        toolbar = JsoninjaToolbarFactory.create(this)
        setContent(tabsView)
    }

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink[JSONINJA_PANEL_DATA_KEY] = this
    }

    override fun dispose() {
        // JsonHelperTabbedPane와 하위 탭 Disposable 들의 부모 역할만 수행하므로 별도 작업 없음
    }
}
