package com.livteam.jsoninja.ui.onboarding

import java.awt.Component
import java.awt.Container
import javax.swing.JComponent

object OnboardingTutorialComponentFactory {

    private val toolbarActionTargetIds = listOf(
        OnboardingTutorialTargetIds.ACTION_ADD_TAB,
        OnboardingTutorialTargetIds.ACTION_OPEN_FILE,
        OnboardingTutorialTargetIds.ACTION_BEAUTIFY,
        OnboardingTutorialTargetIds.ACTION_MINIFY,
        OnboardingTutorialTargetIds.ACTION_ESCAPE,
        OnboardingTutorialTargetIds.ACTION_UNESCAPE,
        OnboardingTutorialTargetIds.ACTION_RANDOM_DATA,
        OnboardingTutorialTargetIds.ACTION_DIFF
    )

    fun createAnchorComponent(root: JComponent, targetName: String): JComponent? {
        val namedComponent = findComponentByName(root, targetName)
        if (namedComponent != null) return namedComponent
        return findToolbarActionComponent(root, targetName)
    }

    private fun findToolbarActionComponent(root: JComponent, targetName: String): JComponent? {
        val actionIndex = toolbarActionTargetIds.indexOf(targetName)
        if (actionIndex < 0) return null

        val toolbar = findComponentByName(root, OnboardingTutorialTargetIds.TOOLBAR) ?: return null
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

    private fun findComponentByName(component: Component, targetName: String): JComponent? {
        if (component is JComponent && component.name == targetName) {
            return component
        }

        if (component !is Container) return null

        component.components.forEach { child ->
            val found = findComponentByName(child, targetName)
            if (found != null) return found
        }
        return null
    }
}
