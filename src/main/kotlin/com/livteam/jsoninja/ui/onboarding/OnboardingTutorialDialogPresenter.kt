package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItTooltip
import com.livteam.jsoninja.LocalizationBundle
import javax.swing.JComponent
import javax.swing.Timer

class OnboardingTutorialDialogPresenter(
    private val rootComponent: JComponent,
    private val tooltipParent: Disposable,
    private val onCancelRequested: () -> Unit,
    private val onCompleteRequested: () -> Unit
) : Disposable {

    private data class TutorialStep(
        val titleKey: String,
        val bodyKey: String,
        val anchorTargetName: String? = null
    )

    private val steps = listOf(
        TutorialStep("onboarding.tutorial.step1.title", "onboarding.tutorial.step1.body", OnboardingTutorialTargetIds.ACTION_ADD_TAB),
        TutorialStep("onboarding.tutorial.step2.title", "onboarding.tutorial.step2.body", OnboardingTutorialTargetIds.ACTION_OPEN_FILE),
        TutorialStep("onboarding.tutorial.step3.title", "onboarding.tutorial.step3.body", OnboardingTutorialTargetIds.ACTION_BEAUTIFY),
        TutorialStep("onboarding.tutorial.step4.title", "onboarding.tutorial.step4.body", OnboardingTutorialTargetIds.ACTION_MINIFY),
        TutorialStep("onboarding.tutorial.step5.title", "onboarding.tutorial.step5.body", OnboardingTutorialTargetIds.ACTION_ESCAPE),
        TutorialStep("onboarding.tutorial.step6.title", "onboarding.tutorial.step6.body", OnboardingTutorialTargetIds.ACTION_UNESCAPE),
        TutorialStep("onboarding.tutorial.step7.title", "onboarding.tutorial.step7.body", OnboardingTutorialTargetIds.ACTION_RANDOM_DATA),
        TutorialStep("onboarding.tutorial.step8.title", "onboarding.tutorial.step8.body", OnboardingTutorialTargetIds.ACTION_DIFF),
        TutorialStep("onboarding.tutorial.step9.title", "onboarding.tutorial.step9.body", OnboardingTutorialTargetIds.QUERY_FIELD),
        TutorialStep("onboarding.tutorial.step10.title", "onboarding.tutorial.step10.body", OnboardingTutorialTargetIds.JSON_EDITOR)
    )

    private val view = OnboardingTutorialDialogView(
        onCancelRequested = onCancelRequested,
        onPrevRequested = ::moveToPrevStep,
        onNextRequested = ::moveToNextStep
    )

    private var currentStepIndex = 0
    private var currentTooltip: GotItTooltip? = null
    private var tooltipRetryTimer: Timer? = null
    private val tooltipSessionId = System.nanoTime()
    private var disposed = false

    fun createCenterPanel(): JComponent {
        return view.createCenterPanel()
    }

    fun createSouthPanel(): JComponent {
        return view.createSouthPanel()
    }

    fun refreshStep(showTooltip: Boolean = true) {
        val step = steps.getOrNull(currentStepIndex) ?: return
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
            hasPrev = currentStepIndex > 0,
            isLastStep = currentStepIndex == steps.lastIndex
        )

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
            return
        }
        onCompleteRequested()
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

    companion object {
        private const val MAX_TOOLTIP_RETRY = 10
        private const val TOOLTIP_RETRY_DELAY_MS = 150
    }
}
