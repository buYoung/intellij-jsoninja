package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItTooltip
import com.livteam.jsoninja.actions.ShowJsonDiffAction
import com.livteam.jsoninja.actions.SortJsonDiffKeysOnceAction
import com.livteam.jsoninja.LocalizationBundle
import com.intellij.openapi.project.Project
import java.awt.Component
import java.awt.Container
import java.awt.Window
import javax.swing.JComponent
import javax.swing.Timer

class OnboardingTutorialDialogPresenter(
    private val project: Project,
    private val rootComponent: JComponent,
    private val tooltipParent: Disposable,
    private val onCancelRequested: () -> Unit
) : Disposable {

    private data class TutorialStep(
        val stepNumber: Int,
        val titleKey: String,
        val bodyKey: String,
        val anchorTargetName: String? = null
    )

    private val steps = listOf(
        TutorialStep(1, "onboarding.tutorial.step1.title", "onboarding.tutorial.step1.body", OnboardingTutorialTargetIds.ACTION_ADD_TAB),
        TutorialStep(2, "onboarding.tutorial.step2.title", "onboarding.tutorial.step2.body", OnboardingTutorialTargetIds.ACTION_OPEN_FILE),
        TutorialStep(3, "onboarding.tutorial.step3.title", "onboarding.tutorial.step3.body", OnboardingTutorialTargetIds.ACTION_BEAUTIFY),
        TutorialStep(4, "onboarding.tutorial.step4.title", "onboarding.tutorial.step4.body", OnboardingTutorialTargetIds.ACTION_MINIFY),
        TutorialStep(5, "onboarding.tutorial.step5.title", "onboarding.tutorial.step5.body", OnboardingTutorialTargetIds.ACTION_ESCAPE),
        TutorialStep(6, "onboarding.tutorial.step6.title", "onboarding.tutorial.step6.body", OnboardingTutorialTargetIds.ACTION_UNESCAPE),
        TutorialStep(7, "onboarding.tutorial.step7.title", "onboarding.tutorial.step7.body", OnboardingTutorialTargetIds.ACTION_RANDOM_DATA),
        TutorialStep(8, "onboarding.tutorial.step8.title", "onboarding.tutorial.step8.body", OnboardingTutorialTargetIds.ACTION_DIFF),
        TutorialStep(9, "onboarding.tutorial.step9.title", "onboarding.tutorial.step9.body", OnboardingTutorialTargetIds.QUERY_FIELD),
        TutorialStep(10, "onboarding.tutorial.step10.title", "onboarding.tutorial.step10.body", OnboardingTutorialTargetIds.JSON_EDITOR)
    )

    private val view = OnboardingTutorialDialogView(
        onCancelRequested = onCancelRequested,
        onPrevRequested = ::moveToPrevStep,
        onNextRequested = ::moveToNextStep
    )

    private var currentStepIndex = 0
    private var lastRenderedStepIndex: Int? = null
    private var currentTooltip: GotItTooltip? = null
    private var sortKeysTooltip: GotItTooltip? = null
    private var tooltipRetryTimer: Timer? = null
    private var sortKeysTooltipRetryTimer: Timer? = null
    private val tooltipSessionId = System.nanoTime()
    private var step8SortTooltipSequence = 0
    private var disposed = false

    fun createCenterPanel(): JComponent {
        return view.createCenterPanel()
    }

    fun createSouthPanel(): JComponent {
        return view.createSouthPanel()
    }

    fun refreshStep(showTooltip: Boolean = true) {
        val step = steps.getOrNull(currentStepIndex) ?: return
        val supplement = OnboardingTutorialStepSupplementProvider.get(step.stepNumber)
        val stepChanged = lastRenderedStepIndex != currentStepIndex
        lastRenderedStepIndex = currentStepIndex
        tooltipRetryTimer?.stop()
        tooltipRetryTimer = null

        view.renderStep(
            stepCounterText = LocalizationBundle.message(
                "onboarding.tutorial.step.counter",
                currentStepIndex + 1,
                steps.size
            ),
            stepTitleText = LocalizationBundle.message(step.titleKey),
            stepBodyText = LocalizationBundle.message(step.bodyKey),
            stepDetailText = resolveMessage(supplement.detailKey),
            beforeText = resolveMessage(supplement.beforeKey),
            afterText = resolveMessage(supplement.afterKey),
            imagePath = supplement.imagePath,
            imageCaptionText = resolveMessage(supplement.imageCaptionKey),
            hasPrev = currentStepIndex > 0,
            isLastStep = currentStepIndex == steps.lastIndex
        )
        maybeResetSortKeysTooltip(step, stepChanged)
        maybeOpenStep8Diff(step, showTooltip, stepChanged)

        if (showTooltip) {
            showAnchorTooltip(step)
        }
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        tooltipRetryTimer?.stop()
        tooltipRetryTimer = null
        currentTooltip?.hidePopup()
        currentTooltip = null
        sortKeysTooltipRetryTimer?.stop()
        sortKeysTooltipRetryTimer = null
        sortKeysTooltip?.hidePopup()
        sortKeysTooltip = null
    }

    private fun moveToPrevStep() {
        if (currentStepIndex <= 0) return
        currentStepIndex--
        refreshStep()
    }

    private fun moveToNextStep() {
        if (currentStepIndex < steps.lastIndex) {
            currentStepIndex++
            refreshStep()
        }
    }

    private fun showAnchorTooltip(step: TutorialStep, attempt: Int = 0) {
        currentTooltip?.hidePopup()
        currentTooltip = null

        val anchorTargetName = step.anchorTargetName ?: return
        val anchorComponent = OnboardingTutorialComponentFactory.createAnchorComponent(rootComponent, anchorTargetName)
        if (anchorComponent == null || !anchorComponent.isShowing) {
            scheduleTooltipRetry(step, attempt)
            return
        }

        val tooltip = GotItTooltip(
            "com.livteam.jsoninja.onboarding.step.$tooltipSessionId.$currentStepIndex",
            LocalizationBundle.message(step.bodyKey),
            tooltipParent
        )
            .withHeader(LocalizationBundle.message(step.titleKey))
            .withTimeout(10000)

        val isJsonEditorStep = anchorTargetName == OnboardingTutorialTargetIds.JSON_EDITOR
        tooltip.withPosition(
            if (isJsonEditorStep) Balloon.Position.atRight else Balloon.Position.below
        )

        val tooltipAnchorPosition = if (isJsonEditorStep) {
            GotItTooltip.RIGHT_MIDDLE
        } else {
            GotItTooltip.BOTTOM_MIDDLE
        }

        if (tooltip.canShow()) {
            tooltip.show(anchorComponent, tooltipAnchorPosition)
            currentTooltip = tooltip
        } else {
            scheduleTooltipRetry(step, attempt)
        }
    }

    private fun scheduleTooltipRetry(step: TutorialStep, attempt: Int) {
        if (disposed || attempt >= MAX_TOOLTIP_RETRY || step != steps.getOrNull(currentStepIndex)) return

        tooltipRetryTimer?.stop()
        tooltipRetryTimer = Timer(TOOLTIP_RETRY_DELAY_MS) {
            if (disposed || step != steps.getOrNull(currentStepIndex)) return@Timer
            showAnchorTooltip(step, attempt + 1)
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun resolveMessage(key: String?): String? {
        if (key.isNullOrBlank()) return null
        return LocalizationBundle.message(key)
    }

    private fun maybeOpenStep8Diff(step: TutorialStep, showTooltip: Boolean, stepChanged: Boolean) {
        if (!showTooltip) return
        if (step.stepNumber != STEP8_STEP_NUMBER) return
        if (!stepChanged) return

        val sortTooltipId = "com.livteam.jsoninja.onboarding.step8.sort.$tooltipSessionId.${step8SortTooltipSequence++}"

        invokeLater(ModalityState.any()) {
            if (disposed || project.isDisposed) return@invokeLater
            ShowJsonDiffAction.openDiffForCurrentJson(project, forceWindow = true)
            showSortKeysTooltip(sortTooltipId)
        }
    }

    private fun maybeResetSortKeysTooltip(step: TutorialStep, stepChanged: Boolean) {
        if (!stepChanged || step.stepNumber == STEP8_STEP_NUMBER) return
        sortKeysTooltipRetryTimer?.stop()
        sortKeysTooltipRetryTimer = null
        sortKeysTooltip?.hidePopup()
        sortKeysTooltip = null
    }

    private fun showSortKeysTooltip(tooltipId: String, attempt: Int = 0) {
        sortKeysTooltip?.hidePopup()
        sortKeysTooltip = null

        val anchorComponent = findSortKeysActionButton()
        if (anchorComponent == null || !anchorComponent.isShowing) {
            scheduleSortKeysTooltipRetry(tooltipId, attempt)
            return
        }

        val tooltip = GotItTooltip(
            tooltipId,
            LocalizationBundle.message("onboarding.tutorial.step8.sort.tooltip.body"),
            tooltipParent
        )
            .withHeader(LocalizationBundle.message("onboarding.tutorial.step8.sort.tooltip.title"))
            .withPosition(Balloon.Position.below)
            .withTimeout(12000)

        if (tooltip.canShow()) {
            tooltip.show(anchorComponent, GotItTooltip.BOTTOM_MIDDLE)
            sortKeysTooltip = tooltip
        } else {
            scheduleSortKeysTooltipRetry(tooltipId, attempt)
        }
    }

    private fun scheduleSortKeysTooltipRetry(tooltipId: String, attempt: Int) {
        if (disposed || attempt >= MAX_SORT_TOOLTIP_RETRY) return
        if (steps.getOrNull(currentStepIndex)?.stepNumber != STEP8_STEP_NUMBER) return

        sortKeysTooltipRetryTimer?.stop()
        sortKeysTooltipRetryTimer = Timer(SORT_TOOLTIP_RETRY_DELAY_MS) {
            if (disposed || steps.getOrNull(currentStepIndex)?.stepNumber != STEP8_STEP_NUMBER) return@Timer
            showSortKeysTooltip(tooltipId, attempt + 1)
        }.apply {
            isRepeats = false
            start()
        }
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
        val action = extractAction(component) as? AnAction ?: return false

        if (action.javaClass.name == SortJsonDiffKeysOnceAction::class.java.name) return true

        val actionText = action.templatePresentation.text?.trim() ?: return false
        return actionText == LocalizationBundle.message("action.diff.sort.keys.once")
    }

    private fun extractAction(component: JComponent): Any? {
        val method = component.javaClass.methods.firstOrNull {
            it.name == "getAction" && it.parameterCount == 0
        } ?: return null
        return runCatching { method.invoke(component) }.getOrNull()
    }

    companion object {
        private const val MAX_TOOLTIP_RETRY = 10
        private const val TOOLTIP_RETRY_DELAY_MS = 150
        private const val STEP8_STEP_NUMBER = 8
        private const val MAX_SORT_TOOLTIP_RETRY = 16
        private const val SORT_TOOLTIP_RETRY_DELAY_MS = 250
    }
}
