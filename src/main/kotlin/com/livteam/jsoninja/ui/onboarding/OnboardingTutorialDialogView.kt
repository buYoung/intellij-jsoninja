package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

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

    private val stepBodyArea = createTextArea().apply {
        margin = JBUI.insets(10, 0)
    }

    private val detailTitleLabel = JBLabel(LocalizationBundle.message("onboarding.tutorial.detail.title")).apply {
        font = JBFont.small().asBold()
    }

    private val detailArea = createTextArea().apply {
        margin = JBUI.insets(6, 0)
    }

    private val beforeTitleLabel = JBLabel(LocalizationBundle.message("onboarding.tutorial.before")).apply {
        font = JBFont.small().asBold()
    }

    private val beforeArea = createExampleTextArea()

    private val afterTitleLabel = JBLabel(LocalizationBundle.message("onboarding.tutorial.after")).apply {
        font = JBFont.small().asBold()
    }

    private val afterArea = createExampleTextArea()

    private val beforeAfterPanel = JPanel(GridLayout(1, 2, 12, 0)).apply {
        isOpaque = false
        add(createBeforeAfterCard(beforeTitleLabel, beforeArea))
        add(createBeforeAfterCard(afterTitleLabel, afterArea))
    }

    private val stepImageLabel = JBLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
    }

    private val imageCaptionLabel = JBLabel().apply {
        font = JBFont.small()
        foreground = UIUtil.getContextHelpForeground()
        horizontalAlignment = SwingConstants.CENTER
    }

    private val imagePanel = JPanel(BorderLayout(0, 8)).apply {
        isOpaque = false
        add(stepImageLabel, BorderLayout.CENTER)
        add(imageCaptionLabel, BorderLayout.SOUTH)
    }

    private val detailPanel = JPanel(BorderLayout(0, 4)).apply {
        isOpaque = false
        add(detailTitleLabel, BorderLayout.NORTH)
        add(detailArea, BorderLayout.CENTER)
    }

    private val contentPanel = JPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        isOpaque = false
        add(stepBodyArea)
        add(detailPanel)
        add(beforeAfterPanel)
        add(imagePanel)
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

    private lateinit var centerPanel: JPanel

    fun createCenterPanel(): JComponent {
        centerPanel = JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(20, 24, 10, 24)
            preferredSize = Dimension(DIALOG_WIDTH, BASE_DIALOG_HEIGHT)

            val header = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(stepTitleLabel, BorderLayout.WEST)
                add(stepCounterLabel, BorderLayout.EAST)
            }

            add(header, BorderLayout.NORTH)
            add(contentPanel, BorderLayout.CENTER)
        }

        return centerPanel
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
        stepDetailText: String?,
        beforeText: String?,
        afterText: String?,
        imagePath: String?,
        imageCaptionText: String?,
        hasPrev: Boolean,
        isLastStep: Boolean
    ) {
        stepCounterLabel.text = stepCounterText
        stepTitleLabel.text = stepTitleText

        stepBodyArea.text = stepBodyText
        stepBodyArea.caretPosition = 0

        val hasDetail = !stepDetailText.isNullOrBlank()
        detailPanel.isVisible = hasDetail
        detailArea.text = stepDetailText.orEmpty()
        detailArea.caretPosition = 0

        val hasBeforeAfter = !beforeText.isNullOrBlank() && !afterText.isNullOrBlank()
        beforeAfterPanel.isVisible = hasBeforeAfter
        beforeArea.text = beforeText.orEmpty()
        beforeArea.caretPosition = 0
        afterArea.text = afterText.orEmpty()
        afterArea.caretPosition = 0

        val imageIcon = loadIcon(imagePath)
        val hasImage = imageIcon != null
        imagePanel.isVisible = hasImage
        stepImageLabel.icon = imageIcon
        stepImageLabel.text = ""
        imageCaptionLabel.text = imageCaptionText.orEmpty()
        imageCaptionLabel.isVisible = hasImage && !imageCaptionText.isNullOrBlank()

        prevButton.isEnabled = hasPrev
        nextButton.text = LocalizationBundle.message("onboarding.tutorial.next")
        nextButton.isEnabled = !isLastStep

        updateDialogLayout(hasDetail, hasBeforeAfter, hasImage)
    }

    private fun updateDialogLayout(hasDetail: Boolean, hasBeforeAfter: Boolean, hasImage: Boolean) {
        if (!::centerPanel.isInitialized) return

        var targetHeight = BASE_DIALOG_HEIGHT
        if (hasDetail) targetHeight += DETAIL_SECTION_HEIGHT
        if (hasBeforeAfter) targetHeight += BEFORE_AFTER_SECTION_HEIGHT
        if (hasImage) targetHeight += IMAGE_SECTION_HEIGHT

        centerPanel.preferredSize = Dimension(DIALOG_WIDTH, targetHeight)
        centerPanel.revalidate()
        centerPanel.repaint()

        SwingUtilities.getWindowAncestor(centerPanel)?.pack()
    }

    private fun createBeforeAfterCard(titleLabel: JBLabel, textArea: JBTextArea): JPanel {
        return JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1),
                JBUI.Borders.empty(8)
            )
            add(titleLabel, BorderLayout.NORTH)
            add(textArea, BorderLayout.CENTER)
        }
    }

    private fun createTextArea(): JBTextArea {
        return JBTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            border = JBUI.Borders.empty()
            font = JBFont.regular()
            alignmentX = 0.0f
        }
    }

    private fun createExampleTextArea(): JBTextArea {
        return createTextArea().apply {
            margin = JBUI.insets(6, 0)
        }
    }

    private fun loadIcon(path: String?): javax.swing.Icon? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            IconLoader.getIcon(path, OnboardingTutorialDialogView::class.java)
        }.getOrNull()
    }

    companion object {
        private const val DIALOG_WIDTH = 560
        private const val BASE_DIALOG_HEIGHT = 210
        private const val DETAIL_SECTION_HEIGHT = 70
        private const val BEFORE_AFTER_SECTION_HEIGHT = 170
        private const val IMAGE_SECTION_HEIGHT = 260
    }
}
