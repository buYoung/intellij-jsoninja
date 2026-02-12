package com.livteam.jsoninja.ui.onboarding

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.livteam.jsoninja.LocalizationBundle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.ImageIcon
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
        font = JBFont.h4().asBold()
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

    private var heroIcon: Icon? = null

    private val heroImagePanel = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val icon = heroIcon ?: return
            if (icon is ImageIcon) {
                val image = icon.image ?: return
                val sourceWidth = image.getWidth(this)
                val sourceHeight = image.getHeight(this)
                if (sourceWidth <= 0 || sourceHeight <= 0) {
                    icon.paintIcon(this, g, 0, 0)
                    return
                }
                val scale = minOf(
                    width.toDouble() / sourceWidth.toDouble(),
                    height.toDouble() / sourceHeight.toDouble()
                )
                val drawWidth = maxOf(1, (sourceWidth * scale).toInt())
                val drawHeight = maxOf(1, (sourceHeight * scale).toInt())
                val offsetX = (width - drawWidth) / 2
                val offsetY = (height - drawHeight) / 2
                g.drawImage(image, offsetX, offsetY, drawWidth, drawHeight, this)
                return
            }

            val sourceWidth = icon.iconWidth
            val sourceHeight = icon.iconHeight
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                icon.paintIcon(this, g, 0, 0)
                return
            }
            val scale = minOf(
                width.toDouble() / sourceWidth.toDouble(),
                height.toDouble() / sourceHeight.toDouble()
            )
            val drawWidth = maxOf(1, (sourceWidth * scale).toInt())
            val drawHeight = maxOf(1, (sourceHeight * scale).toInt())
            val offsetX = (width - drawWidth) / 2
            val offsetY = (height - drawHeight) / 2
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.translate(offsetX.toDouble(), offsetY.toDouble())
            g2.scale(
                drawWidth.toDouble() / sourceWidth.toDouble(),
                drawHeight.toDouble() / sourceHeight.toDouble()
            )
            icon.paintIcon(this, g2, 0, 0)
            g2.dispose()
        }
    }.apply {
        isOpaque = false
        preferredSize = Dimension(HERO_IMAGE_WIDTH, HERO_IMAGE_HEIGHT)
        minimumSize = preferredSize
        maximumSize = preferredSize
        alignmentX = Component.CENTER_ALIGNMENT
    }

    private val heroImageCard = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1),
            JBUI.Borders.empty(6)
        )
        alignmentX = Component.CENTER_ALIGNMENT
        preferredSize = Dimension(HERO_IMAGE_WIDTH + 12, HERO_IMAGE_HEIGHT + 12)
        minimumSize = preferredSize
        maximumSize = preferredSize
        add(heroImagePanel, BorderLayout.CENTER)
    }

    private val heroTitleLabel = JBLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
        font = JBFont.h2().asBold()
        alignmentX = Component.CENTER_ALIGNMENT
    }

    private val heroDescriptionLabel = JBLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
        font = JBFont.regular().deriveFont(13f)
        foreground = UIUtil.getContextHelpForeground()
        alignmentX = Component.CENTER_ALIGNMENT
    }

    private val heroPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        add(heroImageCard)
        add(Box.createVerticalStrut(16))
        add(heroTitleLabel)
        add(Box.createVerticalStrut(8))
        add(heroDescriptionLabel)
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
        add(heroPanel)
    }

    private val skipButton = JButton(LocalizationBundle.message("onboarding.tutorial.close")).apply {
        addActionListener { onCancelRequested() }
    }

    private val prevButton = JButton(LocalizationBundle.message("onboarding.tutorial.prev")).apply {
        addActionListener { onPrevRequested() }
    }

    private val nextButton = JButton(LocalizationBundle.message("onboarding.tutorial.next")).apply {
        putClientProperty("JButton.buttonType", "default")
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
        stepNumber: Int,
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
        detailArea.text = stepDetailText.orEmpty()
        detailArea.caretPosition = 0

        val hasBeforeAfter = !beforeText.isNullOrBlank() && !afterText.isNullOrBlank()
        beforeArea.text = beforeText.orEmpty()
        beforeArea.caretPosition = 0
        afterArea.text = afterText.orEmpty()
        afterArea.caretPosition = 0

        val imageIcon = loadIcon(imagePath)
        val hasImage = imageIcon != null
        val isStep1Hero = stepNumber == STEP1_NUMBER && hasImage
        stepBodyArea.isVisible = !isStep1Hero
        detailPanel.isVisible = !isStep1Hero && hasDetail
        beforeAfterPanel.isVisible = !isStep1Hero && hasBeforeAfter
        imagePanel.isVisible = !isStep1Hero && hasImage
        heroPanel.isVisible = isStep1Hero

        if (isStep1Hero) {
            heroIcon = imageIcon
            heroImagePanel.repaint()
            heroTitleLabel.text = LocalizationBundle.message("onboarding.tutorial.step1.hero.title")
            heroDescriptionLabel.text = toCenteredHtml(stepBodyText, HERO_DESCRIPTION_WIDTH)
            stepImageLabel.icon = null
            imageCaptionLabel.text = ""
            imageCaptionLabel.isVisible = false
        } else {
            heroIcon = null
            heroImagePanel.repaint()
            heroTitleLabel.text = ""
            heroDescriptionLabel.text = ""
            stepImageLabel.icon = imageIcon
            stepImageLabel.text = ""
            imageCaptionLabel.text = imageCaptionText.orEmpty()
            imageCaptionLabel.isVisible = hasImage && !imageCaptionText.isNullOrBlank()
        }

        prevButton.isEnabled = hasPrev
        nextButton.text = LocalizationBundle.message("onboarding.tutorial.next")
        nextButton.isEnabled = !isLastStep
        SwingUtilities.getRootPane(nextButton)?.defaultButton = if (nextButton.isEnabled) nextButton else null

        updateDialogLayout(hasDetail, hasBeforeAfter, hasImage, isStep1Hero)
    }

    fun focusNextButtonIfEnabled() {
        if (!nextButton.isEnabled) return
        SwingUtilities.invokeLater {
            if (!nextButton.isShowing || !nextButton.isEnabled) return@invokeLater
            nextButton.requestFocusInWindow()
        }
    }

    private fun updateDialogLayout(
        hasDetail: Boolean,
        hasBeforeAfter: Boolean,
        hasImage: Boolean,
        isStep1Hero: Boolean
    ) {
        if (!::centerPanel.isInitialized) return

        val targetHeight = if (isStep1Hero) {
            STEP1_DIALOG_HEIGHT
        } else {
            var calculatedHeight = BASE_DIALOG_HEIGHT
            if (hasDetail) calculatedHeight += DETAIL_SECTION_HEIGHT
            if (hasBeforeAfter) calculatedHeight += BEFORE_AFTER_SECTION_HEIGHT
            if (hasImage) calculatedHeight += IMAGE_SECTION_HEIGHT
            calculatedHeight
        }

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

    private fun loadIcon(path: String?): Icon? {
        if (path.isNullOrBlank()) return null
        if (path.endsWith(".gif", ignoreCase = true)) {
            val gifUrl = OnboardingTutorialDialogView::class.java.getResource(path) ?: return null
            return ImageIcon(gifUrl)
        }

        return runCatching { IconLoader.getIcon(path, OnboardingTutorialDialogView::class.java) }.getOrNull()
    }

    private fun toCenteredHtml(text: String, width: Int): String {
        return "<html><div style='text-align:center; width:${width}px;'>${StringUtil.escapeXmlEntities(text)}</div></html>"
    }

    companion object {
        private const val DIALOG_WIDTH = 560
        private const val BASE_DIALOG_HEIGHT = 210
        private const val STEP1_DIALOG_HEIGHT = 540
        private const val STEP1_NUMBER = 1
        private const val DETAIL_SECTION_HEIGHT = 70
        private const val BEFORE_AFTER_SECTION_HEIGHT = 170
        private const val IMAGE_SECTION_HEIGHT = 260
        private const val HERO_IMAGE_WIDTH = 498
        private const val HERO_IMAGE_HEIGHT = 332
        private const val HERO_DESCRIPTION_WIDTH = 460
    }
}
