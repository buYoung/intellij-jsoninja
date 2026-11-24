package com.livteam.jsoninja.ui.toolWindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.livteam.jsoninja.actions.OpenSettingsAction;
import com.livteam.jsoninja.ui.component.main.JsoninjaPanelView;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class JsoninjaToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JsoninjaPanelView jsonHelperPanel = new JsoninjaPanelView(project);
        Disposer.register(toolWindow.getDisposable(), jsonHelperPanel);
        Content content = ContentFactory.getInstance().createContent(jsonHelperPanel, "", false);
        toolWindow.getContentManager().addContent(content);

        ActionManager actionManager = ActionManager.getInstance();
        AnAction closeTabAction = actionManager.getAction("com.livteam.jsoninja.action.CloseTabAction");
        if (closeTabAction != null) {
            ShortcutSet closeShortcutSet = closeTabAction.getShortcutSet();
            if (!closeShortcutSet.hasShortcuts()) {
                AnAction ideCloseAction = actionManager.getAction(IdeActions.ACTION_CLOSE);
                if (ideCloseAction != null) {
                    closeShortcutSet = ideCloseAction.getShortcutSet();
                }
            }
            closeTabAction.registerCustomShortcutSet(closeShortcutSet, jsonHelperPanel);
        }

        AnAction openSettingsAction = actionManager.getAction("com.livteam.jsoninja.action.OpenSettingsAction");
        toolWindow.setTitleActions(List.of(openSettingsAction != null ? openSettingsAction : new OpenSettingsAction()));
    }
}
