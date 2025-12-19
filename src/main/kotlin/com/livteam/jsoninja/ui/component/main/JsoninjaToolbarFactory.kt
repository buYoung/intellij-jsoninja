package com.livteam.jsoninja.ui.component.main

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.livteam.jsoninja.actions.AddTabAction
import com.livteam.jsoninja.actions.EscapeJsonAction
import com.livteam.jsoninja.actions.GenerateRandomJsonAction
import com.livteam.jsoninja.actions.OpenJsonFileAction
import com.livteam.jsoninja.actions.PrettifyJsonAction
import com.livteam.jsoninja.actions.ShowJsonDiffAction
import com.livteam.jsoninja.actions.UglifyJsonAction
import com.livteam.jsoninja.actions.UnescapeJsonAction
import javax.swing.JComponent

object JsoninjaToolbarFactory {
    fun create(targetComponent: JComponent): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            isPopup = true

            // 기본 액션 추가
            add(AddTabAction())
            add(OpenJsonFileAction())

            addSeparator()

            // JSON 변환 관련 액션 추가
            add(PrettifyJsonAction())
            add(UglifyJsonAction())
            addSeparator()
            add(EscapeJsonAction())
            add(UnescapeJsonAction())
            addSeparator()
            add(GenerateRandomJsonAction())
            // JSON Diff 액션 추가
            add(ShowJsonDiffAction())
        }

        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("JsonHelperToolbar", actionGroup, true)

        actionToolbar.targetComponent = targetComponent

        return actionToolbar.component
    }
}
