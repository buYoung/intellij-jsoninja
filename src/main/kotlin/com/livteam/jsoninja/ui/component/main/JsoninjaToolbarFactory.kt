package com.livteam.jsoninja.ui.component.main

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.livteam.jsoninja.actions.AddTabAction
import com.livteam.jsoninja.actions.EscapeJsonAction
import com.livteam.jsoninja.actions.GenerateRandomJsonAction
import com.livteam.jsoninja.actions.LoadJsonFromApiAction
import com.livteam.jsoninja.actions.OpenJsonFileAction
import com.livteam.jsoninja.actions.PrettifyJsonAction
import com.livteam.jsoninja.actions.ShowJsonDiffAction
import com.livteam.jsoninja.actions.TypeConversionAction
import com.livteam.jsoninja.actions.UglifyJsonAction
import com.livteam.jsoninja.actions.UnescapeJsonAction
import com.livteam.jsoninja.ui.onboarding.OnboardingTutorialTargetIds
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent

object JsoninjaToolbarFactory {
    private val tutorialActionTargetIds = listOf(
        OnboardingTutorialTargetIds.ACTION_ADD_TAB,
        OnboardingTutorialTargetIds.ACTION_OPEN_FILE,
        OnboardingTutorialTargetIds.ACTION_BEAUTIFY,
        OnboardingTutorialTargetIds.ACTION_MINIFY,
        OnboardingTutorialTargetIds.ACTION_ESCAPE,
        OnboardingTutorialTargetIds.ACTION_UNESCAPE,
        OnboardingTutorialTargetIds.ACTION_RANDOM_DATA,
        OnboardingTutorialTargetIds.ACTION_DIFF
    )

    fun create(targetComponent: JComponent): JComponent {
        val actionManager = ActionManager.getInstance()
        val actionGroup = DefaultActionGroup().apply {
            // 기본 액션 추가
            add(resolveAction(actionManager, "com.livteam.jsoninja.action.AddTabAction", ::AddTabAction))
            add(resolveAction(actionManager, "com.livteam.jsoninja.action.OpenJsonFileAction", ::OpenJsonFileAction))

            addSeparator()

            // JSON 변환 관련 액션 추가
            add(resolveAction(actionManager, "com.livteam.jsoninja.action.PrettifyJsonAction", ::PrettifyJsonAction))
            add(resolveAction(actionManager, "com.livteam.jsoninja.action.UglifyJsonAction", ::UglifyJsonAction))
            addSeparator()
            add(resolveAction(actionManager, "com.livteam.jsoninja.action.EscapeJsonAction", ::EscapeJsonAction))
            add(resolveAction(actionManager, "com.livteam.jsoninja.action.UnescapeJsonAction", ::UnescapeJsonAction))
            addSeparator()
            add(resolveAction(actionManager, "com.livteam.jsoninja.action.GenerateRandomJsonAction", ::GenerateRandomJsonAction))
            // JSON Diff 액션 추가
            add(resolveAction(actionManager, "com.livteam.jsoninja.action.ShowJsonDiffAction", ::ShowJsonDiffAction))
            add(resolveAction(actionManager, "com.livteam.jsoninja.action.LoadJsonFromApiAction", ::LoadJsonFromApiAction))
            add(resolveAction(actionManager, "com.livteam.jsoninja.action.TypeConversionAction", ::TypeConversionAction))
        }

        val actionToolbar = actionManager.createActionToolbar("JsonHelperToolbar", actionGroup, false)

        actionToolbar.targetComponent = targetComponent

        val toolbarComponent = actionToolbar.component
        toolbarComponent.name = OnboardingTutorialTargetIds.TOOLBAR
        bindTutorialTargets(toolbarComponent)

        return toolbarComponent
    }

    private fun resolveAction(
        actionManager: ActionManager,
        actionId: String,
        fallback: () -> AnAction,
    ): AnAction {
        return actionManager.getAction(actionId) ?: fallback()
    }

    private fun bindTutorialTargets(toolbarComponent: JComponent) {
        val actionButtons = mutableListOf<JComponent>()
        collectActionButtons(toolbarComponent, actionButtons)

        tutorialActionTargetIds.forEachIndexed { index, targetId ->
            actionButtons.getOrNull(index)?.name = targetId
        }
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
}
