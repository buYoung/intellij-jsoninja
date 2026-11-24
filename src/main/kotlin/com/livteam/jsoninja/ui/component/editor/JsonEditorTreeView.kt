package com.livteam.jsoninja.ui.component.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class JsonEditorTreeView : JPanel(), Disposable {
    companion object {
        private const val TREE_ROOT_LABEL = "ROOT"
        private const val TREE_LINE_STYLE_KEY = "JTree.lineStyle"
        private const val TREE_LINE_STYLE_NONE = "None"
        private const val TREE_BRANCH_PREFIX_MIDDLE = "├─ "
        private const val TREE_BRANCH_PREFIX_LAST = "└─ "
        private const val TREE_VERTICAL_LINE = "│  "
        private const val TREE_EMPTY_INDENT = "   "
        private const val MAX_INITIAL_EXPAND_ROWS = 500
    }

    private val editorScheme = EditorColorsManager.getInstance().globalScheme
    private val treeFont = editorScheme.getFont(EditorFontType.PLAIN)
    private val treeView = Tree(DefaultTreeModel(DefaultMutableTreeNode(TREE_ROOT_LABEL))).apply {
        isRootVisible = false
        showsRootHandles = true
        isEditable = false
        putClientProperty(TREE_LINE_STYLE_KEY, TREE_LINE_STYLE_NONE)
        background = editorScheme.defaultBackground
        font = treeFont
        cellRenderer = JsonTreeBranchRenderer()
    }

    private val treeScrollPane = JBScrollPane(treeView).apply {
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
        expandInitialRows(treeModel)
    }

    private fun expandInitialRows(treeModel: DefaultTreeModel) {
        val initialRowCount = treeView.rowCount
        if (initialRowCount == 0 || initialRowCount >= MAX_INITIAL_EXPAND_ROWS) {
            return
        }

        val pendingPaths = ArrayDeque<TreePath>(initialRowCount)
        for (rowIndex in 0 until initialRowCount) {
            val path = treeView.getPathForRow(rowIndex) ?: continue
            val node = path.lastPathComponent
            if (!treeModel.isLeaf(node)) {
                pendingPaths.addLast(path)
            }
        }

        var expandedNodeCount = 0
        while (pendingPaths.isNotEmpty() &&
            treeView.rowCount < MAX_INITIAL_EXPAND_ROWS &&
            expandedNodeCount < MAX_INITIAL_EXPAND_ROWS
        ) {
            val path = pendingPaths.removeFirst()
            val node = path.lastPathComponent
            if (treeModel.isLeaf(node)) {
                continue
            }

            val childCount = treeModel.getChildCount(node)
            if (childCount <= 0 || treeView.rowCount + childCount > MAX_INITIAL_EXPAND_ROWS) {
                continue
            }

            treeView.expandPath(path)
            expandedNodeCount += 1

            for (childIndex in 0 until childCount) {
                val childNode = treeModel.getChild(node, childIndex) ?: continue
                if (!treeModel.isLeaf(childNode)) {
                    pendingPaths.addLast(path.pathByAddingChild(childNode))
                }
            }
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
            text = when (value) {
                is LazyJsonTreeNode -> buildTreeBranchPrefix(value) + value.displayText
                is DefaultMutableTreeNode -> buildTreeBranchPrefix(value) + (value.userObject?.toString() ?: "")
                else -> return@also
            }
        }
    }

    private fun buildTreeBranchPrefix(node: LazyJsonTreeNode): String {
        val depth = node.depth
        if (depth <= 1) {
            return ""
        }

        val prefixBuilder = StringBuilder((depth - 1) * 3)
        val ancestorLastFlags = BooleanArray(depth - 2)
        var currentAncestor = node.parentNode
        var flagIndex = ancestorLastFlags.lastIndex
        while (flagIndex >= 0 && currentAncestor != null) {
            ancestorLastFlags[flagIndex] = currentAncestor.isLastSibling()
            currentAncestor = currentAncestor.parentNode
            flagIndex -= 1
        }

        for (isAncestorLast in ancestorLastFlags) {
            prefixBuilder.append(if (isAncestorLast) TREE_EMPTY_INDENT else TREE_VERTICAL_LINE)
        }
        prefixBuilder.append(
            if (node.isLastSibling()) {
                TREE_BRANCH_PREFIX_LAST
            } else {
                TREE_BRANCH_PREFIX_MIDDLE
            }
        )
        return prefixBuilder.toString()
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
        val parentNode = node.parent ?: return true
        return parentNode.getChildAt(parentNode.childCount - 1) == node
    }
}
