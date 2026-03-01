package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreeNode

class JsonEditorTreeView : JPanel(), Disposable {
    companion object {
        private const val TREE_ROOT_LABEL = "ROOT"
        private const val TREE_LINE_STYLE_KEY = "JTree.lineStyle"
        private const val TREE_LINE_STYLE_NONE = "None"
        private const val TREE_BRANCH_PREFIX_MIDDLE = "├─ "
        private const val TREE_BRANCH_PREFIX_LAST = "└─ "
        private const val TREE_VERTICAL_LINE = "│  "
        private const val TREE_EMPTY_INDENT = "   "
    }

    private val editorScheme = EditorColorsManager.getInstance().globalScheme
    private val treeFont = editorScheme.getFont(EditorFontType.PLAIN)
    private val treeView = JTree(DefaultTreeModel(DefaultMutableTreeNode(TREE_ROOT_LABEL))).apply {
        isRootVisible = false
        showsRootHandles = true
        isEditable = false
        putClientProperty(TREE_LINE_STYLE_KEY, TREE_LINE_STYLE_NONE)
        background = editorScheme.defaultBackground
        font = treeFont
        cellRenderer = JsonTreeBranchRenderer()
    }

    private val treeScrollPane = JScrollPane(treeView).apply {
        border = JBUI.Borders.empty()
        background = editorScheme.defaultBackground
        viewport.background = editorScheme.defaultBackground
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

    private inner class JsonTreeBranchRenderer : DefaultTreeCellRenderer() {
        init {
            leafIcon = null
            openIcon = null
            closedIcon = null
            font = treeFont
        }

        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) = super.getTreeCellRendererComponent(
            tree,
            value,
            selected,
            expanded,
            leaf,
            row,
            hasFocus
        ).also {
            font = treeFont
            val node = value as? DefaultMutableTreeNode ?: return@also
            text = buildTreeBranchPrefix(node) + (node.userObject?.toString() ?: "")
        }
    }

    private fun buildTreeBranchPrefix(node: DefaultMutableTreeNode): String {
        val pathNodes = node.path
        val nodeDepthFromVisibleRoot = pathNodes.size - 2

        if (node.parent == null || nodeDepthFromVisibleRoot <= 0) {
            return ""
        }

        val prefixBuilder = StringBuilder()

        for (pathIndex in 2 until pathNodes.size - 1) {
            val ancestorNode = pathNodes[pathIndex] as? DefaultMutableTreeNode ?: continue
            prefixBuilder.append(
                if (isLastSibling(ancestorNode)) {
                    TREE_EMPTY_INDENT
                } else {
                    TREE_VERTICAL_LINE
                }
            )
        }

        prefixBuilder.append(
            if (isLastSibling(node)) {
                TREE_BRANCH_PREFIX_LAST
            } else {
                TREE_BRANCH_PREFIX_MIDDLE
            }
        )

        return prefixBuilder.toString()
    }

    private fun isLastSibling(node: DefaultMutableTreeNode): Boolean {
        val parentNode = node.parent as? TreeNode ?: return true
        return parentNode.getChildAt(parentNode.childCount - 1) == node
    }
}
