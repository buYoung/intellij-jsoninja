package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItTooltip
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.actions.ShowJsonDiffAction
import com.livteam.jsoninja.actions.SortJsonDiffKeysOnceAction
import java.awt.Component
import java.awt.Container
import java.awt.Window
import javax.swing.JComponent
import javax.swing.Timer

class OnboardingStep8DiffTooltipController(
    private val project: Project,
    private val rootComponent: JComponent,
    private val tooltipParent: Disposable,
    private val isDisposed: () -> Boolean,
    private val isStep8Active: () -> Boolean
) {
    private var actionTooltip: GotItTooltip? = null
    private var sortKeysTooltip: GotItTooltip? = null
    private var retryTimer: Timer? = null
    private val tooltipSessionId = System.nanoTime()
    private var tooltipSequence = 0
    private var openedDiffWindow: Window? = null

    fun maybeOpenDiff(
        showTooltip: Boolean,
        stepChanged: Boolean,
        stepTitleKey: String,
        stepBodyKey: String,
        anchorTargetName: String?
    ) {
        if (!showTooltip || !stepChanged || anchorTargetName == null) return

        val sequence = tooltipSequence++
        val actionTooltipId = "com.livteam.jsoninja.onboarding.step8.action.$tooltipSessionId.$sequence"
        val sortTooltipId = "com.livteam.jsoninja.onboarding.step8.sort.$tooltipSessionId.$sequence"

        invokeLater(ModalityState.any()) {
            if (isDisposed() || project.isDisposed || !isStep8Active()) return@invokeLater
            ShowJsonDiffAction.openDiffForCurrentJson(project, forceWindow = true)
            showTooltips(
                stepTitleKey = stepTitleKey,
                stepBodyKey = stepBodyKey,
                anchorTargetName = anchorTargetName,
                actionTooltipId = actionTooltipId,
                sortTooltipId = sortTooltipId
            )
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
            return
        }

        openedDiffWindow = findWindowAncestor(sortButton)

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
        val stepWindow = openedDiffWindow
        if (stepWindow != null && stepWindow.isShowing) {
            stepWindow.dispose()
            openedDiffWindow = null
            return
        }

        val currentSortButton = findSortKeysActionButton()
        val detectedWindow = currentSortButton?.let { findWindowAncestor(it) }
        if (detectedWindow != null && detectedWindow.isShowing) {
            detectedWindow.dispose()
        }
        openedDiffWindow = null
    }

    private fun findSortKeysActionButton(): JComponent? {
        Window.getWindows().forEach { window ->
            if (!window.isShowing) return@forEach
            val found = findSortKeysActionButton(window)
            if (found != null) return found
        }
        return null
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
        if (!component.javaClass.name.contains("ActionButton")) return false
        val sortLabel = LocalizationBundle.message("action.diff.sort.keys.once")
        val sortDescription = LocalizationBundle.message("action.diff.sort.keys.once.description")
        val tooltipText = component.toolTipText?.trim()

        if (!tooltipText.isNullOrEmpty()) {
            if (tooltipText.contains(sortLabel, ignoreCase = true)) return true
            if (tooltipText.contains(sortDescription, ignoreCase = true)) return true
        }

        val action = extractAction(component) as? AnAction ?: return false

        if (action.javaClass.name == SortJsonDiffKeysOnceAction::class.java.name) return true

        val actionText = action.templatePresentation.text?.trim() ?: return false
        return actionText == sortLabel
    }

    private fun extractAction(component: JComponent): Any? {
        val publicMethod = component.javaClass.methods.firstOrNull {
            it.name == "getAction" && it.parameterCount == 0
        }
        if (publicMethod != null) {
            return runCatching { publicMethod.invoke(component) }.getOrNull()
        }

        val declaredMethod = component.javaClass.declaredMethods.firstOrNull {
            it.name == "getAction" && it.parameterCount == 0
        }
        if (declaredMethod != null) {
            return runCatching {
                declaredMethod.isAccessible = true
                declaredMethod.invoke(component)
            }.getOrNull()
        }

        val declaredField = component.javaClass.declaredFields.firstOrNull {
            it.name == "myAction" || it.name == "action"
        } ?: return null

        return runCatching {
            declaredField.isAccessible = true
            declaredField.get(component)
        }.getOrNull()
    }

    private fun findWindowAncestor(component: Component): Window? {
        var current: Component? = component
        while (current != null) {
            if (current is Window) return current
            current = current.parent
        }
        return null
    }

    companion object {
        private const val STEP8_STEP_NUMBER = 8
        private const val MAX_TOOLTIP_RETRY = 20
        private const val TOOLTIP_RETRY_DELAY_MS = 250
    }
}
