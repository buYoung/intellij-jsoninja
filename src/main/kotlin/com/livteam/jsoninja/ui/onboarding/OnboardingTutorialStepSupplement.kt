package com.livteam.jsoninja.ui.onboarding

data class OnboardingTutorialStepSupplement(
    val detailKey: String? = null,
    val beforeKey: String? = null,
    val afterKey: String? = null,
    val imagePath: String? = null,
    val imageCaptionKey: String? = null
)

object OnboardingTutorialStepSupplementProvider {
    private val supplements = mapOf(
        1 to OnboardingTutorialStepSupplement(
            imagePath = createStepImagePath(1)
        ),
        2 to OnboardingTutorialStepSupplement(
            imagePath = createStepImagePath(2),
            detailKey = "onboarding.tutorial.step2.detail"
        ),
        3 to OnboardingTutorialStepSupplement(
            imagePath = createStepImagePath(3),
            beforeKey = "onboarding.tutorial.step3.before",
            afterKey = "onboarding.tutorial.step3.after"
        ),
        4 to OnboardingTutorialStepSupplement(
            imagePath = createStepImagePath(4),
            beforeKey = "onboarding.tutorial.step5.before",
            afterKey = "onboarding.tutorial.step5.after"
        ),
        5 to OnboardingTutorialStepSupplement(
            imagePath = createStepImagePath(5),
            beforeKey = "onboarding.tutorial.step7.before",
            afterKey = "onboarding.tutorial.step7.after"
        ),
        6 to OnboardingTutorialStepSupplement(
            imagePath = createStepImagePath(6),
            imageCaptionKey = "onboarding.tutorial.step8.image.caption"
        ),
        7 to OnboardingTutorialStepSupplement(
            imagePath = createStepImagePath(7),
            beforeKey = "onboarding.tutorial.step9.before",
            afterKey = "onboarding.tutorial.step9.after"
        )
    )

    fun get(stepNumber: Int): OnboardingTutorialStepSupplement {
        return supplements[stepNumber] ?: OnboardingTutorialStepSupplement()
    }

    private fun createStepImagePath(stepNumber: Int): String {
        return "/images/onboarding/step_${stepNumber}.gif"
    }
}
