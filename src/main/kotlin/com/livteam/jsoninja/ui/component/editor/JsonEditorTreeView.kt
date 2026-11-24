package com.livteam.jsoninja.ui.component.editor

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.services.JsonFormatterService
import com.livteam.jsoninja.services.JsonHelperService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.Point
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JsonEditorTreeView(
    private val project: Project,
) : JPanel(), Disposable {
    companion object {
        private const val TREE_ROOT_LABEL = "ROOT"
        private const val MAX_INITIAL_EXPAND_ROWS = 500
        private const val MAX_MANUAL_EXPAND_ROWS = 2_000
        private const val COPY_HINT_TIMEOUT_MS = 1_800
    }

    private var currentTreeFont: Font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
    private var currentBackground: Color = EditorColorsManager.getInstance().globalScheme.defaultBackground
    private var currentForeground: Color = EditorColorsManager.getInstance().globalScheme.defaultForeground
    private var keyAttributes: SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
    private var stringAttributes: SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
    private var numberAttributes: SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
    private var keywordAttributes: SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
    private var placeholderAttributes: SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
    private var onSwitchToTextRequested: ((line: Int?, column: Int?) -> Unit)? = null
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    @Volatile
    private var isDisposed = false

    private val treeView = Tree(DefaultTreeModel(DefaultMutableTreeNode(TREE_ROOT_LABEL))).apply {
        isRootVisible = false
        showsRootHandles = true
        isEditable = false
        background = currentBackground
        foreground = currentForeground
        font = currentTreeFont
        cellRenderer = JsonTreeSyntaxCellRenderer()
    }

    private val treeScrollPane = JBScrollPane(treeView).apply {
        border = JBUI.Borders.empty()
        background = currentBackground
        viewport.background = currentBackground
    }

    val focusTarget: JComponent
        get() = treeView

    init {
        layout = BorderLayout()
        background = currentBackground
        refreshSyntaxAttributes(EditorColorsManager.getInstance().globalScheme)
        add(treeScrollPane, BorderLayout.CENTER)
        installTreeInteractions()
        subscribeThemeChanges()
    }

    fun setOnSwitchToTextRequested(callback: (line: Int?, column: Int?) -> Unit) {
        onSwitchToTextRequested = callback
    }

    private fun installTreeInteractions() {
        val treeUIHelper = TreeUIHelper.getInstance()
        treeUIHelper.installTreeSpeedSearch(
            treeView,
            { path ->
                val last = path.lastPathComponent
                (last as? LazyJsonTreeNode)?.displayText ?: last?.toString().orEmpty()
            },
            true,
        )
        treeUIHelper.installSmartExpander(treeView)
        treeUIHelper.installSelectionSaver(treeView)

        treeView.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = treeView.getPathForLocation(e.x, e.y) ?: return
                    if (!treeView.isPathSelected(path)) {
                        treeView.selectionPath = path
                    }
                }
            }
        })

        PopupHandler.installPopupMenu(treeView, createTreePopupGroup(), ActionPlaces.POPUP)
    }

    private fun createTreePopupGroup(): DefaultActionGroup {
        return DefaultActionGroup().apply {
            add(object : DumbAwareAction(LocalizationBundle.messagePointer("tree.action.copy.key")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val key = selectedJsonNode()?.rawKeyName ?: return
                    copyToClipboardAndHint(key, LocalizationBundle.message("tree.copied.value"))
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = !selectedJsonNode()?.rawKeyName.isNullOrEmpty()
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : DumbAwareAction(LocalizationBundle.messagePointer("tree.action.copy.value")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val node = selectedJsonNode() ?: return
                    val scalarValue = node.rawCopyValue
                    if (scalarValue != null) {
                        copyToClipboardAndHint(scalarValue, LocalizationBundle.message("tree.copied.value"))
                        return
                    }
                    val subtree = node.jsonNode ?: return
                    copySerializedSubtreeAsync(subtree)
                }

                override fun update(e: AnActionEvent) {
                    val node = selectedJsonNode()
                    e.presentation.isEnabled = node != null && (node.rawCopyValue != null || node.jsonNode != null)
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : DumbAwareAction(LocalizationBundle.messagePointer("tree.action.copy.json")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val subtree = selectedJsonNode()?.jsonNode ?: return
                    copySerializedSubtreeAsync(subtree)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedJsonNode()?.jsonNode != null
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : DumbAwareAction(LocalizationBundle.messagePointer("action.copy.json.query")) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (project.isDisposed) return
                    val node = selectedJsonNode() ?: return
                    val queryType = JsonQueryType.fromString(JsoninjaSettingsState.getInstance(project).jsonQueryType)
                    val path = node.buildQueryPath(queryType)
                    copyToClipboardAndHint(path, LocalizationBundle.message("action.copy.json.query.copied", path))
                }

                override fun update(e: AnActionEvent) {
                    val node = selectedJsonNode()
                    e.presentation.isEnabled = !project.isDisposed && node != null
                    if (!project.isDisposed) {
                        val queryType = JsonQueryType.fromString(JsoninjaSettingsState.getInstance(project).jsonQueryType)
                        val engineLabel = when (queryType) {
                            JsonQueryType.JAYWAY_JSONPATH -> LocalizationBundle.message("query.engine.jsonpath")
                            JsonQueryType.JMESPATH -> LocalizationBundle.message("query.engine.jmespath")
                            JsonQueryType.JACKSON_JQ -> LocalizationBundle.message("query.engine.jq")
                        }
                        e.presentation.text = LocalizationBundle.message("action.copy.json.query.by.type", engineLabel)
                    }
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : DumbAwareAction(LocalizationBundle.messagePointer("action.com.livteam.jsoninja.action.CopyJsonArrayQueryAction.text")) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (project.isDisposed) return
                    val node = selectedJsonNode() ?: return
                    val queryType = JsonQueryType.fromString(JsoninjaSettingsState.getInstance(project).jsonQueryType)
                    val path = node.buildArrayWildcardQueryPath(queryType) ?: return
                    copyToClipboardAndHint(path, LocalizationBundle.message("action.copy.json.query.copied", path))
                }

                override fun update(e: AnActionEvent) {
                    if (project.isDisposed) {
                        e.presentation.isEnabledAndVisible = false
                        return
                    }
                    val node = selectedJsonNode()
                    val queryType = JsonQueryType.fromString(JsoninjaSettingsState.getInstance(project).jsonQueryType)
                    val wildcardPath = node?.buildArrayWildcardQueryPath(queryType)
                    if (wildcardPath == null) {
                        e.presentation.isEnabledAndVisible = false
                        return
                    }
                    val engineLabel = when (queryType) {
                        JsonQueryType.JAYWAY_JSONPATH -> LocalizationBundle.message("query.engine.jsonpath")
                        JsonQueryType.JMESPATH -> LocalizationBundle.message("query.engine.jmespath")
                        JsonQueryType.JACKSON_JQ -> LocalizationBundle.message("query.engine.jq")
                    }
                    e.presentation.text = LocalizationBundle.message(
                        "action.copy.json.array.query.by.type",
                        engineLabel,
                    )
                    e.presentation.isEnabledAndVisible = true
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            addSeparator()

            add(object : DumbAwareAction(LocalizationBundle.messagePointer("tree.action.expand.subtree")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val path = treeView.selectionPath ?: return
                    expandSubtreeBounded(path)
                }

                override fun update(e: AnActionEvent) {
                    val path = treeView.selectionPath
                    val model = treeView.model
                    e.presentation.isEnabled = path != null && model != null && !model.isLeaf(path.lastPathComponent)
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : DumbAwareAction(LocalizationBundle.messagePointer("tree.action.collapse.subtree")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val path = treeView.selectionPath ?: return
                    collapseSubtree(path)
                }

                override fun update(e: AnActionEvent) {
                    val path = treeView.selectionPath
                    val model = treeView.model
                    e.presentation.isEnabled = path != null && model != null && !model.isLeaf(path.lastPathComponent)
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })
        }
    }

    private fun selectedJsonNode(): LazyJsonTreeNode? {
        return treeView.selectionPath?.lastPathComponent as? LazyJsonTreeNode
    }

    private fun copySerializedSubtreeAsync(jsonNode: JsonNode) {
        if (isDisposed || project.isDisposed) return
        val formatState = project.service<JsonHelperService>().getJsonFormatState()
        val formatterService = project.service<JsonFormatterService>()
        val modalityState = ModalityState.stateForComponent(treeView)
        coroutineScope.launch {
            try {
                val serialized = withContext(Dispatchers.Default) {
                    formatterService.formatJsonNode(jsonNode, formatState)
                }
                withContext(Dispatchers.EDT + modalityState.asContextElement()) {
                    if (isDisposed || project.isDisposed) return@withContext
                    copyToClipboardAndHint(serialized, LocalizationBundle.message("tree.copied.value"))
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            }
        }
    }

    private fun copyToClipboardAndHint(text: String, hintMessage: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        if (!treeView.isShowing) return

        val selectedRow = treeView.leadSelectionRow
        val anchorPoint = if (selectedRow >= 0) {
            val rowBounds = treeView.getRowBounds(selectedRow)
            if (rowBounds != null) {
                Point(
                    (rowBounds.x + minOf(rowBounds.width / 2, JBUI.scale(160))).coerceAtLeast(JBUI.scale(8)),
                    rowBounds.y + rowBounds.height,
                )
            } else {
                Point(JBUI.scale(16), JBUI.scale(16))
            }
        } else {
            Point(JBUI.scale(16), JBUI.scale(16))
        }

        val hintLabel = HintUtil.createInformationLabel(hintMessage)
        HintManager.getInstance().showHint(
            hintLabel,
            RelativePoint(treeView, anchorPoint),
            HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_OTHER_HINT or HintManager.HIDE_BY_SCROLLING,
            COPY_HINT_TIMEOUT_MS,
        )
    }

    private fun subscribeThemeChanges() {
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { scheduleThemeRefresh() })
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { scheduleThemeRefresh() })
    }

    private fun scheduleThemeRefresh() {
        if (isDisposed) return
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            refreshThemeColors()
        } else {
            app.invokeLater({
                if (!isDisposed) {
                    refreshThemeColors()
                }
            }, ModalityState.stateForComponent(this))
        }
    }

    private fun refreshThemeColors() {
        val scheme = EditorColorsManager.getInstance().globalScheme
        currentTreeFont = scheme.getFont(EditorFontType.PLAIN)
        currentBackground = scheme.defaultBackground
        currentForeground = scheme.defaultForeground
        refreshSyntaxAttributes(scheme)

        background = currentBackground
        treeView.background = currentBackground
        treeView.foreground = currentForeground
        treeView.font = currentTreeFont
        treeScrollPane.background = currentBackground
        treeScrollPane.viewport.background = currentBackground
        treeView.revalidate()
        treeView.repaint()
        revalidate()
        repaint()
    }

    private fun refreshSyntaxAttributes(scheme: EditorColorsScheme) {
        keyAttributes = resolveSyntaxAttributes(scheme, DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        stringAttributes = resolveSyntaxAttributes(scheme, DefaultLanguageHighlighterColors.STRING)
        numberAttributes = resolveSyntaxAttributes(scheme, DefaultLanguageHighlighterColors.NUMBER)
        keywordAttributes = resolveSyntaxAttributes(scheme, DefaultLanguageHighlighterColors.KEYWORD)
        placeholderAttributes = resolveSyntaxAttributes(scheme, DefaultLanguageHighlighterColors.CONSTANT)
    }

    private fun resolveSyntaxAttributes(
        scheme: EditorColorsScheme,
        key: TextAttributesKey,
    ): SimpleTextAttributes {
        val textAttributes = scheme.getAttributes(key)
        return if (textAttributes != null) {
            SimpleTextAttributes.fromTextAttributes(textAttributes)
        } else {
            SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
    }

    fun setTreeModel(treeModel: DefaultTreeModel) {
        treeView.emptyText.clear()
        treeView.model = treeModel
        expandRowsBounded(treeModel, MAX_INITIAL_EXPAND_ROWS)
    }

    fun setEmptyState() {
        treeView.emptyText.clear()
        treeView.model = DefaultTreeModel(DefaultMutableTreeNode(TREE_ROOT_LABEL))
    }

    fun setInvalidJsonState(line: Int?, column: Int?) {
        treeView.model = DefaultTreeModel(DefaultMutableTreeNode(TREE_ROOT_LABEL))
        treeView.emptyText.clear()
        val errorMessage = if (line != null && column != null) {
            LocalizationBundle.message("tree.error.invalid.json.location", line, column)
        } else {
            LocalizationBundle.message("tree.error.invalid.json")
        }
        treeView.emptyText.appendText(errorMessage)
        treeView.emptyText.appendSecondaryText(
            LocalizationBundle.message("tree.error.switch.to.text"),
            SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
        ) {
            onSwitchToTextRequested?.invoke(line, column)
        }
        treeView.repaint()
    }

    fun expandAllBounded() {
        val treeModel = treeView.model as? DefaultTreeModel ?: return
        val maxTotalRows = maxOf(MAX_MANUAL_EXPAND_ROWS, treeView.rowCount + MAX_INITIAL_EXPAND_ROWS)
        expandRowsBounded(treeModel, maxTotalRows)
    }

    fun collapseAll() {
        TreeUtil.collapseAll(treeView, 0)
    }

    private fun expandSubtreeBounded(rootPath: TreePath) {
        val treeModel = treeView.model as? DefaultTreeModel ?: return
        val maxTotalRows = treeView.rowCount + MAX_MANUAL_EXPAND_ROWS
        val pendingPaths = ArrayDeque<TreePath>()
        pendingPaths.addLast(rootPath)
        expandPendingPaths(treeModel, pendingPaths, maxTotalRows)
    }

    private fun collapseSubtree(rootPath: TreePath) {
        val expandedDescendants = treeView.getExpandedDescendants(rootPath)
        val pathsToCollapse = ArrayList<TreePath>()
        if (expandedDescendants != null) {
            while (expandedDescendants.hasMoreElements()) {
                pathsToCollapse.add(expandedDescendants.nextElement())
            }
        }
        pathsToCollapse.sortByDescending { it.pathCount }
        for (path in pathsToCollapse) {
            treeView.collapsePath(path)
        }
        treeView.collapsePath(rootPath)
    }

    private fun expandRowsBounded(treeModel: DefaultTreeModel, maxTotalRows: Int) {
        val initialRowCount = treeView.rowCount
        if (initialRowCount == 0 || initialRowCount >= maxTotalRows) {
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

        expandPendingPaths(treeModel, pendingPaths, maxTotalRows)
    }

    private fun expandPendingPaths(
        treeModel: DefaultTreeModel,
        pendingPaths: ArrayDeque<TreePath>,
        maxTotalRows: Int,
    ) {
        var expandedNodeCount = 0
        while (pendingPaths.isNotEmpty() &&
            treeView.rowCount < maxTotalRows &&
            expandedNodeCount < maxTotalRows
        ) {
            val path = pendingPaths.removeFirst()
            val node = path.lastPathComponent
            if (treeModel.isLeaf(node)) {
                continue
            }

            val childCount = treeModel.getChildCount(node)
            if (childCount <= 0) {
                continue
            }

            val isAlreadyExpanded = treeView.isExpanded(path)
            if (!isAlreadyExpanded) {
                if (treeView.rowCount + childCount > maxTotalRows) {
                    continue
                }
                treeView.expandPath(path)
                expandedNodeCount += 1
            }

            for (childIndex in 0 until childCount) {
                val childNode = treeModel.getChild(node, childIndex) ?: continue
                if (!treeModel.isLeaf(childNode)) {
                    pendingPaths.addLast(path.pathByAddingChild(childNode))
                }
            }
        }
    }

    override fun dispose() {
        isDisposed = true
        coroutineScope.cancel()
        removeAll()
    }

    private inner class JsonTreeSyntaxCellRenderer : ColoredTreeCellRenderer() {
        init {
            isOpaque = false
        }

        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            font = currentTreeFont
            icon = null

            when (value) {
                is LazyJsonTreeNode -> renderJsonNode(value)
                is DefaultMutableTreeNode -> append(value.userObject?.toString().orEmpty(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                null -> Unit
                else -> append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }

            SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, false, selected)
        }

        private fun renderJsonNode(node: LazyJsonTreeNode) {
            if (node.keyText.isNotEmpty()) {
                append(node.keyText, keyAttributes)
            }

            when (node.valueKind) {
                JsonTreeValueKind.OBJECT -> {
                    append(" {${node.containerCount}}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                JsonTreeValueKind.ARRAY -> {
                    append(" [${node.containerCount}]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                JsonTreeValueKind.EMPTY_OBJECT -> {
                    append(" : ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append("{}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                JsonTreeValueKind.EMPTY_ARRAY -> {
                    append(" : ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append("[]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                JsonTreeValueKind.STRING -> {
                    append(" : ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(node.valueText.orEmpty(), stringAttributes)
                }
                JsonTreeValueKind.NUMBER -> {
                    append(" : ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(node.valueText.orEmpty(), numberAttributes)
                }
                JsonTreeValueKind.BOOLEAN,
                JsonTreeValueKind.NULL -> {
                    append(" : ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(node.valueText.orEmpty(), keywordAttributes)
                }
                JsonTreeValueKind.PLACEHOLDER -> {
                    append(" : ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(node.valueText.orEmpty(), placeholderAttributes)
                }
            }
        }
    }
}
