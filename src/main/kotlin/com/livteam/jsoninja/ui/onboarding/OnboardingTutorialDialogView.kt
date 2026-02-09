package com.livteam.jsoninja.ui.onboarding

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

class OnboardingTutorialDialogView(
    onCancelRequested: () -> Unit,
    onPrevRequested: () -> Unit,
    onNextRequested: () -> Unit
) {
    private val stepCounterLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBFont.small()
    }

    private val stepTitleLabel = JBLabel().apply {
        font = JBFont.h3().asBold()
    }

    private val stepBodyArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        border = JBUI.Borders.empty()
        font = JBFont.regular()
        margin = JBUI.insets(10, 0)
    }

    private val skipButton = JButton(LocalizationBundle.message("onboarding.tutorial.close")).apply {
        addActionListener { onCancelRequested() }
    }

    private val prevButton = JButton(LocalizationBundle.message("onboarding.tutorial.prev")).apply {
        addActionListener { onPrevRequested() }
    }

    private val nextButton = JButton(LocalizationBundle.message("onboarding.tutorial.next")).apply {
        addActionListener { onNextRequested() }
    }

    fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(20, 24, 10, 24)
            preferredSize = Dimension(460, 180)

            val header = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(stepTitleLabel, BorderLayout.WEST)
                add(stepCounterLabel, BorderLayout.EAST)
            }

            val bodyScroll = JBScrollPane(stepBodyArea).apply {
                border = JBUI.Borders.empty()
                isOpaque = false
                viewport.isOpaque = false
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            }

            add(header, BorderLayout.NORTH)
            add(bodyScroll, BorderLayout.CENTER)
        }
    }

    fun createSouthPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 24, 16, 24)

            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(skipButton)
            }

            val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                isOpaque = false
                add(prevButton)
                add(nextButton)
            }

            add(leftPanel, BorderLayout.WEST)
            add(rightPanel, BorderLayout.EAST)
        }
    }

    fun renderStep(
        stepCounterText: String,
        stepTitleText: String,
        stepBodyText: String,
        hasPrev: Boolean,
        isLastStep: Boolean
    ) {
        stepCounterLabel.text = stepCounterText
        stepTitleLabel.text = stepTitleText
        stepBodyArea.text = stepBodyText
        stepBodyArea.caretPosition = 0
        prevButton.isEnabled = hasPrev
        nextButton.text = LocalizationBundle.message("onboarding.tutorial.next")
        nextButton.isEnabled = !isLastStep
    }
}
