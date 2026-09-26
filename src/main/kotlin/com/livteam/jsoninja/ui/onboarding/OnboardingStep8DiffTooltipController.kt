package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.ui.GotItTooltip
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.services.JsonDiffService
import com.livteam.jsoninja.services.JsoninjaCoroutineScopeService
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import com.livteam.jsoninja.ui.diff.JsonDiffWindowDialog
import com.livteam.jsoninja.utils.JsonHelperUtils
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.Timer
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingStep8DiffTooltipController(
    private val project: Project,
    private val rootComponent: JComponent,
    private val tooltipParent: Disposable,
    private val isDisposed: () -> Boolean,
    private val isStep8Active: () -> Boolean,
    private val onStep8UiUpdated: () -> Unit
) {
    private var actionTooltip: GotItTooltip? = null
    private var sortKeysTooltip: GotItTooltip? = null
    private var retryTimer: Timer? = null
    private val tooltipSessionId = System.nanoTime()
    private var tooltipSequence = 0
    private var ownedDiffDialog: JsonDiffWindowDialog? = null
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()

    init {
        Disposer.register(tooltipParent) {
            dispose()
        }
    }

    fun maybeOpenDiff(
        showTooltip: Boolean,
        stepChanged: Boolean,
        stepTitleKey: String,
        stepBodyKey: String,
        anchorTargetName: String?
    ) {
        if (!showTooltip || !stepChanged || anchorTargetName == null) return

        closeDiffWindow()
        val sequence = ++tooltipSequence
        val actionTooltipId = "com.livteam.jsoninja.onboarding.step8.action.$tooltipSessionId.$sequence"
        val sortTooltipId = "com.livteam.jsoninja.onboarding.step8.sort.$tooltipSessionId.$sequence"

        coroutineScope.launch {
            val input = withContext(Dispatchers.EDT) {
                if (isDisposed() || project.isDisposed || !isStep8Active()) return@withContext null
                Pair(
                    JsonHelperUtils.getCurrentJsonFromToolWindow(project) ?: "{}",
                    JsoninjaSettingsState.getInstance(project).diffSortKeys,
                )
            } ?: return@launch
            val diffService = project.service<JsonDiffService>()
            val leftText = withContext(Dispatchers.Default) {
                diffService.validateAndFormat(input.first, input.second).second ?: input.first
            }
            withContext(Dispatchers.EDT) {
                if (isDisposed() || project.isDisposed || !isStep8Active() || sequence != tooltipSequence) {
                    return@withContext
                }
                val request = diffService.createDiffRequest(
                    leftDocument = EditorFactory.getInstance().createDocument(leftText),
                    rightDocument = EditorFactory.getInstance().createDocument("{}"),
                    semantic = input.second,
                )
                lateinit var dialog: JsonDiffWindowDialog
                dialog = JsonDiffWindowDialog(project, request) {
                    if (ownedDiffDialog === dialog) ownedDiffDialog = null
                }
                ownedDiffDialog = dialog
                dialog.showOrFocus()
                showTooltips(
                    stepTitleKey = stepTitleKey,
                    stepBodyKey = stepBodyKey,
                    anchorTargetName = anchorTargetName,
                    actionTooltipId = actionTooltipId,
                    sortTooltipId = sortTooltipId
                )
                notifyUiUpdated()
            }
        }
    }

    fun maybeCloseDiffWindow(stepNumber: Int, stepChanged: Boolean) {
        if (!stepChanged || stepNumber == STEP8_STEP_NUMBER) return
        closeDiffWindow()
    }

    fun clearTooltips() {
        retryTimer?.stop()
        retryTimer = null

        actionTooltip?.hidePopup()
        actionTooltip = null

        sortKeysTooltip?.hidePopup()
        sortKeysTooltip = null
    }

    fun dispose() {
        coroutineScope.cancel()
        clearTooltips()
        closeDiffWindow()
    }

    private fun showTooltips(
        stepTitleKey: String,
        stepBodyKey: String,
        anchorTargetName: String,
        actionTooltipId: String,
        sortTooltipId: String,
        attempt: Int = 0
    ) {
        actionTooltip?.hidePopup()
        actionTooltip = null
        sortKeysTooltip?.hidePopup()
        sortKeysTooltip = null

        val actionButton = OnboardingTutorialComponentFactory.createAnchorComponent(rootComponent, anchorTargetName)
        val sortButton = findSortKeysActionButton()

        if (actionButton == null || !actionButton.isShowing || sortButton == null || !sortButton.isShowing) {
            scheduleRetry(
                stepTitleKey = stepTitleKey,
                stepBodyKey = stepBodyKey,
                anchorTargetName = anchorTargetName,
                actionTooltipId = actionTooltipId,
                sortTooltipId = sortTooltipId,
                attempt = attempt
            )
            notifyUiUpdated()
            return
        }

        val actionGuideTooltip = GotItTooltip(
            actionTooltipId,
            LocalizationBundle.message(stepBodyKey),
            tooltipParent
        )
            .withHeader(LocalizationBundle.message(stepTitleKey))
            .withPosition(Balloon.Position.below)
            .withTimeout(3000)

        val sortGuideTooltip = GotItTooltip(
            sortTooltipId,
            LocalizationBundle.message("onboarding.tutorial.step8.sort.tooltip.body"),
            tooltipParent
        )
            .withHeader(LocalizationBundle.message("onboarding.tutorial.step8.sort.tooltip.title"))
            .withPosition(Balloon.Position.below)
            .withTimeout(10000)

        val actionShown = runCatching {
            actionGuideTooltip.show(actionButton, GotItTooltip.BOTTOM_MIDDLE)
            true
        }.getOrDefault(false)

        val sortShown = runCatching {
            sortGuideTooltip.show(sortButton, GotItTooltip.BOTTOM_MIDDLE)
            true
        }.getOrDefault(false)

        if (actionShown && sortShown) {
            actionTooltip = actionGuideTooltip
            sortKeysTooltip = sortGuideTooltip
            notifyUiUpdated()
            return
        }

        actionGuideTooltip.hidePopup()
        sortGuideTooltip.hidePopup()
        scheduleRetry(
            stepTitleKey = stepTitleKey,
            stepBodyKey = stepBodyKey,
            anchorTargetName = anchorTargetName,
            actionTooltipId = actionTooltipId,
            sortTooltipId = sortTooltipId,
            attempt = attempt
        )
        notifyUiUpdated()
    }

    private fun scheduleRetry(
        stepTitleKey: String,
        stepBodyKey: String,
        anchorTargetName: String,
        actionTooltipId: String,
        sortTooltipId: String,
        attempt: Int
    ) {
        if (isDisposed() || attempt >= MAX_TOOLTIP_RETRY || !isStep8Active()) return

        retryTimer?.stop()
        retryTimer = Timer(TOOLTIP_RETRY_DELAY_MS) {
            if (isDisposed() || !isStep8Active()) return@Timer
            showTooltips(
                stepTitleKey = stepTitleKey,
                stepBodyKey = stepBodyKey,
                anchorTargetName = anchorTargetName,
                actionTooltipId = actionTooltipId,
                sortTooltipId = sortTooltipId,
                attempt = attempt + 1
            )
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun closeDiffWindow() {
        tooltipSequence++
        val dialog = ownedDiffDialog ?: return
        ownedDiffDialog = null
        dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
    }

    private fun findSortKeysActionButton(): JComponent? {
        val window = ownedDiffDialog?.window ?: return null
        return if (window.isShowing) findSortKeysActionButton(window) else null
    }

    private fun findSortKeysActionButton(component: Component): JComponent? {
        if (component is JComponent && isSortKeysActionButton(component)) {
            return component
        }
        if (component !is Container) return null

        component.components.forEach { child ->
            val found = findSortKeysActionButton(child)
            if (found != null) return found
        }
        return null
    }

    private fun isSortKeysActionButton(component: JComponent): Boolean {
        val sortLabel = LocalizationBundle.message("action.diff.sort.keys.once")
        val sortDescription = LocalizationBundle.message("action.diff.sort.keys.once.description")
        val tooltipText = component.toolTipText?.trim()

        if (!tooltipText.isNullOrEmpty()) {
            if (tooltipText.contains(sortLabel, ignoreCase = true)) return true
            if (tooltipText.contains(sortDescription, ignoreCase = true)) return true
        }

        return false
    }

    private fun notifyUiUpdated() {
        if (!isDisposed() && !project.isDisposed && isStep8Active()) {
            onStep8UiUpdated()
        }
    }

    companion object {
        private const val STEP8_STEP_NUMBER = 6
        private const val MAX_TOOLTIP_RETRY = 20
        private const val TOOLTIP_RETRY_DELAY_MS = 250
    }
}
