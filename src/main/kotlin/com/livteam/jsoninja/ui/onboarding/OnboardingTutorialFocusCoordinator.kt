package com.livteam.jsoninja.ui.onboarding

class OnboardingTutorialFocusCoordinator(
    private val requestNextButtonFocus: () -> Unit
) {
    enum class Event {
        DIALOG_ACTIVATED,
        STEP_RENDERED,
        STEP8_UI_UPDATED
    }

    fun onEvent(event: Event, isLastStep: Boolean) {
        if (isLastStep) return
        when (event) {
            Event.DIALOG_ACTIVATED,
            Event.STEP_RENDERED,
            Event.STEP8_UI_UPDATED -> requestNextButtonFocus()
        }
    }
}
