package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItTooltip
import com.livteam.jsoninja.LocalizationBundle
import com.intellij.openapi.project.Project
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
        TutorialStep(
            1,
            "onboarding.tutorial.step1.title",
            "onboarding.tutorial.step1.body",
            OnboardingTutorialTargetIds.ACTION_ADD_TAB
        ),
        TutorialStep(
            2,
            "onboarding.tutorial.step2.title",
            "onboarding.tutorial.step2.body",
            OnboardingTutorialTargetIds.ACTION_OPEN_FILE
        ),
        TutorialStep(
            3,
            "onboarding.tutorial.step3.title",
            "onboarding.tutorial.step3.body",
            OnboardingTutorialTargetIds.ACTION_BEAUTIFY
        ),
        TutorialStep(
            4,
            "onboarding.tutorial.step5.title",
            "onboarding.tutorial.step5.body",
            OnboardingTutorialTargetIds.ACTION_ESCAPE
        ),
        TutorialStep(
            5,
            "onboarding.tutorial.step7.title",
            "onboarding.tutorial.step7.body",
            OnboardingTutorialTargetIds.ACTION_RANDOM_DATA
        ),
        TutorialStep(
            6,
            "onboarding.tutorial.step8.title",
            "onboarding.tutorial.step8.body",
            OnboardingTutorialTargetIds.ACTION_DIFF
        ),
        TutorialStep(
            7,
            "onboarding.tutorial.step9.title",
            "onboarding.tutorial.step9.body",
            OnboardingTutorialTargetIds.QUERY_FIELD
        )
    )

    private val view = OnboardingTutorialDialogView(
        onCancelRequested = onCancelRequested,
        onPrevRequested = ::moveToPrevStep,
        onNextRequested = ::moveToNextStep
    )
    private val focusCoordinator = OnboardingTutorialFocusCoordinator(
        requestNextButtonFocus = view::focusNextButtonIfEnabled
    )

    private var currentStepIndex = 0
    private var lastRenderedStepIndex: Int? = null
    private var currentTooltip: GotItTooltip? = null
    private var tooltipRetryTimer: Timer? = null
    private val tooltipSessionId = System.nanoTime()
    private val step8DiffTooltipController = OnboardingStep8DiffTooltipController(
        project = project,
        rootComponent = rootComponent,
        tooltipParent = tooltipParent,
        isDisposed = { disposed || project.isDisposed },
        isStep8Active = { steps.getOrNull(currentStepIndex)?.stepNumber == STEP8_STEP_NUMBER },
        onStep8UiUpdated = { onFocusEvent(OnboardingTutorialFocusCoordinator.Event.STEP8_UI_UPDATED) }
    )
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
        if (stepChanged) {
            clearAnchorTooltip()
            step8DiffTooltipController.clearTooltips()
        } else {
            tooltipRetryTimer?.stop()
            tooltipRetryTimer = null
        }

        view.renderStep(
            stepNumber = step.stepNumber,
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
        step8DiffTooltipController.maybeCloseDiffWindow(
            stepNumber = step.stepNumber,
            stepChanged = stepChanged
        )
        step8DiffTooltipController.maybeOpenDiff(
            showTooltip = showTooltip,
            stepChanged = stepChanged,
            stepTitleKey = step.titleKey,
            stepBodyKey = step.bodyKey,
            anchorTargetName = step.anchorTargetName
        )

        if (showTooltip) {
            if (step.stepNumber != STEP8_STEP_NUMBER) {
                showAnchorTooltip(step)
            }
        }

        onFocusEvent(OnboardingTutorialFocusCoordinator.Event.STEP_RENDERED)
    }

    fun onDialogActivated() {
        onFocusEvent(OnboardingTutorialFocusCoordinator.Event.DIALOG_ACTIVATED)
    }

    fun onEnterPressed() {
        moveToNextStep()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        clearAnchorTooltip()
        step8DiffTooltipController.dispose()
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

    private fun clearAnchorTooltip() {
        tooltipRetryTimer?.stop()
        tooltipRetryTimer = null
        currentTooltip?.hidePopup()
        currentTooltip = null
    }

    private fun onFocusEvent(event: OnboardingTutorialFocusCoordinator.Event) {
        focusCoordinator.onEvent(
            event = event,
            isLastStep = currentStepIndex == steps.lastIndex
        )
    }

    companion object {
        private const val MAX_TOOLTIP_RETRY = 10
        private const val TOOLTIP_RETRY_DELAY_MS = 150
        private const val STEP8_STEP_NUMBER = 8
    }
}
