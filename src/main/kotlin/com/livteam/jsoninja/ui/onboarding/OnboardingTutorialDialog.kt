package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.GotItTooltip
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.livteam.jsoninja.LocalizationBundle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Font
import java.awt.Window
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.Timer

class OnboardingTutorialDialog(
    private val project: Project,
    private val rootComponent: JComponent,
    private val onClosed: () -> Unit
) : JDialog(WindowManager.getInstance().getFrame(project) as? Window), Disposable {

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

    private val stepCounterLabel = JBLabel()
    private val stepTitleLabel = JBLabel()
    private val stepBodyArea = JBTextArea()

    private val prevButton = JButton(LocalizationBundle.message("onboarding.tutorial.prev"))
    private val nextButton = JButton(LocalizationBundle.message("onboarding.tutorial.next"))
    private val closeButton = JButton(LocalizationBundle.message("onboarding.tutorial.close"))

    private var currentStepIndex = 0
    private val tooltipSessionId = System.nanoTime()
    private var currentTooltip: GotItTooltip? = null
    private var tooltipRetryTimer: Timer? = null
    private var closed = false

    init {
        title = LocalizationBundle.message("onboarding.tutorial.title")
        modalityType = Dialog.ModalityType.MODELESS
        isAlwaysOnTop = true
        isAutoRequestFocus = false
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(420, 260)

        contentPane = createContentPanel()
        bindActions()
        refreshStep(showTooltip = false)

        pack()
        setLocationRelativeTo(owner)
    }

    fun open() {
        if (!isVisible) {
            isVisible = true
        } else {
            toFront()
        }
        refreshStep()
    }

    override fun dispose() {
        if (closed) {
            super.dispose()
            return
        }
        closed = true
        tooltipRetryTimer?.stop()
        tooltipRetryTimer = null
        currentTooltip?.hidePopup()
        currentTooltip = null
        onClosed()
        super.dispose()
    }

    private fun createContentPanel(): JComponent {
        stepTitleLabel.font = stepTitleLabel.font.deriveFont(Font.BOLD)
        stepBodyArea.apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            border = JBUI.Borders.empty()
        }

        val bodyScroll = JBScrollPane(stepBodyArea).apply {
            border = JBUI.Borders.emptyTop(8)
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        val topPanel = JPanel(BorderLayout(0, 6)).apply {
            border = JBUI.Borders.empty(12)
            add(stepCounterLabel, BorderLayout.NORTH)
            add(stepTitleLabel, BorderLayout.CENTER)
            add(bodyScroll, BorderLayout.SOUTH)
        }

        val navPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 12, 12, 12)

            val leftButtons = JPanel().apply {
                add(prevButton)
                add(nextButton)
            }
            add(leftButtons, BorderLayout.WEST)
            add(closeButton, BorderLayout.EAST)
        }

        return JPanel(BorderLayout()).apply {
            add(topPanel, BorderLayout.CENTER)
            add(navPanel, BorderLayout.SOUTH)
        }
    }

    private fun bindActions() {
        prevButton.addActionListener {
            if (currentStepIndex > 0) {
                currentStepIndex--
                refreshStep()
            }
        }

        nextButton.addActionListener {
            if (currentStepIndex < steps.lastIndex) {
                currentStepIndex++
                refreshStep()
            }
        }

        closeButton.addActionListener {
            dispose()
        }
    }

    private fun refreshStep(showTooltip: Boolean = true) {
        val step = steps[currentStepIndex]
        tooltipRetryTimer?.stop()
        tooltipRetryTimer = null

        stepCounterLabel.text = LocalizationBundle.message(
            "onboarding.tutorial.step.counter",
            currentStepIndex + 1,
            steps.size
        )
        stepTitleLabel.text = LocalizationBundle.message(step.titleKey)
        stepBodyArea.text = LocalizationBundle.message(step.bodyKey)
        stepBodyArea.caretPosition = 0

        prevButton.isEnabled = currentStepIndex > 0
        nextButton.isEnabled = currentStepIndex < steps.lastIndex

        if (showTooltip) {
            showAnchorTooltip(step)
        }
    }

    private fun showAnchorTooltip(step: TutorialStep, attempt: Int = 0) {
        currentTooltip?.hidePopup()
        currentTooltip = null

        val anchorTargetName = step.anchorTargetName ?: return
        val anchorComponent = resolveAnchorComponent(anchorTargetName)
        if (anchorComponent == null || !anchorComponent.isShowing) {
            scheduleTooltipRetry(step, attempt)
            return
        }

        val tooltip = GotItTooltip(
            "com.livteam.jsoninja.onboarding.step.$tooltipSessionId.$currentStepIndex",
            LocalizationBundle.message(step.bodyKey),
            project
        )
            .withHeader(LocalizationBundle.message(step.titleKey))
            .withPosition(Balloon.Position.below)
            .withTimeout(7000)

        if (tooltip.canShow()) {
            tooltip.show(anchorComponent, GotItTooltip.BOTTOM_MIDDLE)
            currentTooltip = tooltip
        } else {
            scheduleTooltipRetry(step, attempt)
        }
    }

    private fun resolveAnchorComponent(targetName: String): JComponent? {
        val namedComponent = findComponentByName(rootComponent, targetName)
        if (namedComponent != null) return namedComponent

        return findToolbarActionComponentByTarget(targetName)
    }

    private fun findToolbarActionComponentByTarget(targetName: String): JComponent? {
        val actionIndex = TOOLBAR_ACTION_TARGET_IDS.indexOf(targetName)
        if (actionIndex < 0) return null

        val toolbar = findComponentByName(rootComponent, OnboardingTutorialTargetIds.TOOLBAR) ?: return null
        val actionButtons = mutableListOf<JComponent>()
        collectActionButtons(toolbar, actionButtons)
        return actionButtons.getOrNull(actionIndex)
    }

    private fun collectActionButtons(component: Component, collector: MutableList<JComponent>) {
        if (component is JComponent && component.javaClass.name.contains("ActionButton")) {
            collector.add(component)
        }

        if (component !is Container) return

        component.components.forEach { child ->
            collectActionButtons(child, collector)
        }
    }

    private fun scheduleTooltipRetry(step: TutorialStep, attempt: Int) {
        if (closed || attempt >= MAX_TOOLTIP_RETRY || step != steps.getOrNull(currentStepIndex)) return

        tooltipRetryTimer?.stop()
        tooltipRetryTimer = Timer(TOOLTIP_RETRY_DELAY_MS) {
            if (closed || step != steps.getOrNull(currentStepIndex)) return@Timer
            showAnchorTooltip(step, attempt + 1)
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun findComponentByName(component: Component, targetName: String): JComponent? {
        if (component is JComponent && component.name == targetName) {
            return component
        }

        if (component !is Container) {
            return null
        }

        component.components.forEach { child ->
            val found = findComponentByName(child, targetName)
            if (found != null) {
                return found
            }
        }
        return null
    }

    companion object {
        private const val MAX_TOOLTIP_RETRY = 8
        private const val TOOLTIP_RETRY_DELAY_MS = 120
        private val TOOLBAR_ACTION_TARGET_IDS = listOf(
            OnboardingTutorialTargetIds.ACTION_ADD_TAB,
            OnboardingTutorialTargetIds.ACTION_OPEN_FILE,
            OnboardingTutorialTargetIds.ACTION_BEAUTIFY,
            OnboardingTutorialTargetIds.ACTION_MINIFY,
            OnboardingTutorialTargetIds.ACTION_ESCAPE,
            OnboardingTutorialTargetIds.ACTION_UNESCAPE,
            OnboardingTutorialTargetIds.ACTION_RANDOM_DATA,
            OnboardingTutorialTargetIds.ACTION_DIFF
        )
    }
}
