package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class JsonEditorTreeView : JPanel(), Disposable {
    companion object {
        private const val TREE_ROOT_LABEL = "ROOT"
    }

    private val treeView = JTree(DefaultTreeModel(DefaultMutableTreeNode(TREE_ROOT_LABEL))).apply {
        isRootVisible = false
        showsRootHandles = true
        isEditable = false
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
    }

    private val treeScrollPane = JScrollPane(treeView).apply {
        border = JBUI.Borders.empty()
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        viewport.background = EditorColorsManager.getInstance().globalScheme.defaultBackground
    }

    init {
        layout = BorderLayout()
        add(treeScrollPane, BorderLayout.CENTER)
    }

    fun setTreeModel(treeModel: DefaultTreeModel) {
        treeView.model = treeModel
        expandAllRows()
    }

    private fun expandAllRows() {
        var rowIndex = 0
        while (rowIndex < treeView.rowCount) {
            treeView.expandRow(rowIndex)
            rowIndex += 1
        }
    }

    override fun dispose() {
        removeAll()
    }
}
