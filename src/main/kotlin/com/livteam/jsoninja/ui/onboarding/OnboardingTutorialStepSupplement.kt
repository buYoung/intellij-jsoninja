package com.livteam.jsoninja.ui.onboarding

data class OnboardingTutorialStepSupplement(
    val detailKey: String? = null,
    val beforeKey: String? = null,
    val afterKey: String? = null,
    val imagePath: String? = null,
    val imageCaptionKey: String? = null
)

object OnboardingTutorialStepSupplementProvider {
    private const val STEP8_DIFF_OVERVIEW_IMAGE = "/images/onboarding/step8-diff-overview.svg"

    private val supplements = mapOf(
        1 to OnboardingTutorialStepSupplement(
            detailKey = "onboarding.tutorial.step1.detail"
        ),
        2 to OnboardingTutorialStepSupplement(
            detailKey = "onboarding.tutorial.step2.detail"
        ),
        3 to OnboardingTutorialStepSupplement(
            beforeKey = "onboarding.tutorial.step3.before",
            afterKey = "onboarding.tutorial.step3.after"
        ),
        4 to OnboardingTutorialStepSupplement(
            beforeKey = "onboarding.tutorial.step4.before",
            afterKey = "onboarding.tutorial.step4.after"
        ),
        5 to OnboardingTutorialStepSupplement(
            beforeKey = "onboarding.tutorial.step5.before",
            afterKey = "onboarding.tutorial.step5.after"
        ),
        6 to OnboardingTutorialStepSupplement(
            beforeKey = "onboarding.tutorial.step6.before",
            afterKey = "onboarding.tutorial.step6.after"
        ),
        7 to OnboardingTutorialStepSupplement(
            beforeKey = "onboarding.tutorial.step7.before",
            afterKey = "onboarding.tutorial.step7.after"
        ),
        8 to OnboardingTutorialStepSupplement(
            imagePath = STEP8_DIFF_OVERVIEW_IMAGE,
            imageCaptionKey = "onboarding.tutorial.step8.image.caption"
        ),
        9 to OnboardingTutorialStepSupplement(
            beforeKey = "onboarding.tutorial.step9.before",
            afterKey = "onboarding.tutorial.step9.after"
        )
    )

    fun get(stepNumber: Int): OnboardingTutorialStepSupplement {
        return supplements[stepNumber] ?: OnboardingTutorialStepSupplement()
    }
}
