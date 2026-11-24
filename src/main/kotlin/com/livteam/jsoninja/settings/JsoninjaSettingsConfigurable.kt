package com.livteam.jsoninja.settings

import com.intellij.CommonBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.icons.JsoninjaIcons
import com.livteam.jsoninja.model.JsonDiffDisplayMode
import com.livteam.jsoninja.model.JsonFormatState
import com.livteam.jsoninja.model.JsonIconPack
import com.livteam.jsoninja.model.JsonQueryType
import com.livteam.jsoninja.services.OnboardingService
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities

class JsoninjaSettingsConfigurable(private val project: Project) : BoundSearchableConfigurable(
    displayName = LocalizationBundle.message("settings.display.name"),
    helpTopic = CONFIGURABLE_ID,
    _id = CONFIGURABLE_ID
) {

    private var settingsSyncEnabled: Boolean = false
    private var indentSize: Int = 2
    private var sortKeys: Boolean = false
    private var defaultFormatState: JsonFormatState = JsonFormatState.PRETTIFY
    private var pasteFormatState: JsonFormatState = JsonFormatState.PRETTIFY
    private var diffDisplayMode: JsonDiffDisplayMode = JsonDiffDisplayMode.EDITOR_TAB
    private var diffSortKeys: Boolean = false
    private var jsonQueryType: JsonQueryType = JsonQueryType.JAYWAY_JSONPATH
    private var iconPack: JsonIconPack = JsonIconPack.VERSION_3
    private var showLargeFileWarning: Boolean = true
    private var largeFileThresholdMB: Int = 10

    private var mainDialogPanel: DialogPanel? = null

    init {
        loadFromSettings()
    }

    override fun createPanel(): DialogPanel {
        val defaultFormatStates = JsonFormatState.entries.filter { it != JsonFormatState.UGLIFY }
        val allFormatStates = JsonFormatState.entries

        return panel {
            row {
                checkBox(LocalizationBundle.message("settings.sync.enabled.label"))
                    .bindSelected(::settingsSyncEnabled)
            }

            group(LocalizationBundle.message("settings.group.formatting")) {
                row(LocalizationBundle.message("settings.indent.label")) {
                    spinner(0..32, step = 1)
                        .bindIntValue(::indentSize)
                }
                row {
                    checkBox(LocalizationBundle.message("settings.sortkeys.label"))
                        .bindSelected(::sortKeys)
                }
                row(LocalizationBundle.message("settings.format.label")) {
                    comboBox(defaultFormatStates, createFormatStateRenderer())
                        .bindItem(::defaultFormatState.toNullableProperty())
                }
                row(LocalizationBundle.message("settings.paste.format.label")) {
                    comboBox(allFormatStates, createFormatStateRenderer())
                        .bindItem(::pasteFormatState.toNullableProperty())
                }
            }

            group(LocalizationBundle.message("settings.group.diff")) {
                row(LocalizationBundle.message("settings.diff.display.label")) {
                    comboBox(JsonDiffDisplayMode.entries, createDiffDisplayModeRenderer())
                        .bindItem(::diffDisplayMode.toNullableProperty())
                }
                row {
                    checkBox(LocalizationBundle.message("settings.diff.sort.label"))
                        .bindSelected(::diffSortKeys)
                }
            }

            group(LocalizationBundle.message("settings.group.query")) {
                row(LocalizationBundle.message("settings.query.type.label")) {
                    comboBox(JsonQueryType.entries, createQueryTypeRenderer())
                        .bindItem(::jsonQueryType.toNullableProperty())
                }
            }

            group(LocalizationBundle.message("settings.group.appearance")) {
                row(LocalizationBundle.message("settings.icon.pack.label")) {
                    comboBox(JsonIconPack.entries, createIconPackRenderer())
                        .bindItem(::iconPack.toNullableProperty())
                }
            }

            group(LocalizationBundle.message("settings.group.performance")) {
                lateinit var showWarningCheckBox: Cell<JBCheckBox>
                row {
                    showWarningCheckBox = checkBox(LocalizationBundle.message("settings.show.large.file.warning.label"))
                        .bindSelected(::showLargeFileWarning)
                }
                row(LocalizationBundle.message("settings.large.file.threshold.label")) {
                    spinner(1..100, step = 1)
                        .bindIntValue(::largeFileThresholdMB)
                }.enabledIf(showWarningCheckBox.selected)
            }

            separator()

            row {
                button(LocalizationBundle.message("settings.onboarding.start.tutorial")) {
                    startTutorialFromSettings()
                }
            }
        }.also { mainDialogPanel = it }
    }

    override fun apply() {
        super.apply()

        val activeSettings = JsoninjaSettingsState.setSettingsSyncEnabled(project, settingsSyncEnabled)
        activeSettings.indentSize = indentSize
        activeSettings.sortKeys = sortKeys
        activeSettings.jsonFormatState = defaultFormatState.name
        activeSettings.pasteFormatState = pasteFormatState.name
        activeSettings.diffDisplayMode = diffDisplayMode.name
        activeSettings.diffSortKeys = diffSortKeys
        activeSettings.jsonQueryType = jsonQueryType.name
        activeSettings.iconPack = iconPack.name
        activeSettings.showLargeFileWarning = showLargeFileWarning
        activeSettings.largeFileThresholdMB = largeFileThresholdMB

        ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .forEach { openProject ->
                openProject.messageBus.syncPublisher(JsoninjaSettingsListener.TOPIC).onSettingsChanged(activeSettings)
            }
    }

    override fun reset() {
        loadFromSettings()
        super.reset()
    }

    override fun disposeUIResources() {
        mainDialogPanel = null
        super.disposeUIResources()
    }

    private fun loadFromSettings() {
        val activeSettings = JsoninjaSettingsState.getInstance(project)
        settingsSyncEnabled = JsoninjaSettingsState.isSettingsSyncEnabled()
        indentSize = activeSettings.indentSize.coerceIn(0, 32)
        sortKeys = activeSettings.sortKeys
        val parsedDefaultFormat = JsonFormatState.fromString(activeSettings.jsonFormatState)
        defaultFormatState = if (parsedDefaultFormat == JsonFormatState.UGLIFY) {
            JsonFormatState.PRETTIFY
        } else {
            parsedDefaultFormat
        }
        pasteFormatState = JsonFormatState.fromString(activeSettings.pasteFormatState)
        diffDisplayMode = runCatching { JsonDiffDisplayMode.valueOf(activeSettings.diffDisplayMode) }
            .getOrDefault(JsonDiffDisplayMode.EDITOR_TAB)
        diffSortKeys = activeSettings.diffSortKeys
        jsonQueryType = JsonQueryType.fromString(activeSettings.jsonQueryType)
        iconPack = JsonIconPack.fromPersistedValue(activeSettings.iconPack)
        showLargeFileWarning = activeSettings.showLargeFileWarning
        largeFileThresholdMB = activeSettings.largeFileThresholdMB.coerceIn(1, 100)
    }

    private fun startTutorialFromSettings() {
        if (isModified) {
            val choice = Messages.showYesNoCancelDialog(
                project,
                LocalizationBundle.message("settings.unsaved.changes.message"),
                LocalizationBundle.message("settings.unsaved.changes.title"),
                LocalizationBundle.message("settings.unsaved.changes.apply"),
                LocalizationBundle.message("settings.unsaved.changes.discard"),
                CommonBundle.getCancelButtonText(),
                Messages.getQuestionIcon()
            )
            when (choice) {
                Messages.YES -> apply()
                Messages.NO -> reset()
                else -> return
            }
        }

        val sourceComponent = mainDialogPanel
        val dialogWrapper = sourceComponent?.let { DialogWrapper.findInstance(it) }
        if (dialogWrapper != null) {
            dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
        } else {
            sourceComponent?.let { SwingUtilities.getWindowAncestor(it)?.dispose() }
        }
        project.service<OnboardingService>().startTutorial()
    }

    private fun createFormatStateRenderer() = textListCellRenderer<JsonFormatState?> { value ->
        value?.let(::formatJsonFormatState).orEmpty()
    }

    private fun createDiffDisplayModeRenderer() = textListCellRenderer<JsonDiffDisplayMode?> { value ->
        when (value) {
            JsonDiffDisplayMode.EDITOR_TAB -> LocalizationBundle.message("settings.diff.display.editor.tab")
            JsonDiffDisplayMode.WINDOW -> LocalizationBundle.message("settings.diff.display.window")
            null -> ""
        }
    }

    private fun createQueryTypeRenderer() = textListCellRenderer<JsonQueryType?> { value ->
        when (value) {
            JsonQueryType.JAYWAY_JSONPATH -> "Jayway JsonPath"
            JsonQueryType.JMESPATH -> "JMESPath"
            JsonQueryType.JACKSON_JQ -> "jq (jackson-jq)"
            null -> ""
        }
    }

    private fun createIconPackRenderer(): ListCellRenderer<JsonIconPack?> = object : ColoredListCellRenderer<JsonIconPack?>() {
        override fun customizeCellRenderer(
            list: JList<out JsonIconPack?>,
            value: JsonIconPack?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            if (value == null) return
            icon = when (value) {
                JsonIconPack.VERSION_1 -> JsoninjaIcons.PrettyIcon
                JsonIconPack.VERSION_2 -> JsoninjaIcons.PrettyIconV2
                JsonIconPack.VERSION_3 -> JsoninjaIcons.PrettyIconV3
            }
            append(
                when (value) {
                    JsonIconPack.VERSION_1 -> LocalizationBundle.message("settings.icon.pack.v1")
                    JsonIconPack.VERSION_2 -> LocalizationBundle.message("settings.icon.pack.v2")
                    JsonIconPack.VERSION_3 -> LocalizationBundle.message("settings.icon.pack.v3")
                }
            )
        }
    }

    private fun formatJsonFormatState(state: JsonFormatState): String {
        return when (state) {
            JsonFormatState.PRETTIFY -> LocalizationBundle.message("dialog.type.to.json.format.prettify")
            JsonFormatState.PRETTIFY_COMPACT -> LocalizationBundle.message("dialog.type.to.json.format.prettify.compact")
            JsonFormatState.PRETTIFY_SORTED -> LocalizationBundle.message("settings.format.prettify.sorted")
            JsonFormatState.UGLIFY -> LocalizationBundle.message("dialog.type.to.json.format.uglify")
        }
    }

    companion object {
        private const val CONFIGURABLE_ID = "com.livteam.jsoninja.settings.JsoninjaSettingsConfigurable"
    }
}
