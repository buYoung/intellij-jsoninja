package com.livteam.jsoninja.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "JsoninjaOnboardingState",
    storages = [Storage("jsoninja-onboarding.xml")],
    category = SettingsCategory.PLUGINS
)
class OnboardingStateService : SimplePersistentStateComponent<OnboardingStateService.State>(State()) {

    class State : BaseState() {
        var isWelcomeDialogSeen by property(false)
    }

    val isWelcomeDialogSeen: Boolean
        get() = state.isWelcomeDialogSeen

    fun markWelcomeDialogSeen() {
        state.isWelcomeDialogSeen = true
    }

    companion object {
        fun getInstance(): OnboardingStateService {
            return ApplicationManager.getApplication().getService(OnboardingStateService::class.java)
        }
    }
}
