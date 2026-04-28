package com.livteam.jsoninja.ui.toolWindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
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

        AnAction closeTabAction = ActionManager.getInstance().getAction("com.livteam.jsoninja.action.CloseTabAction");
        if (closeTabAction != null) {
            closeTabAction.registerCustomShortcutSet(closeTabAction.getShortcutSet(), jsonHelperPanel);
        }

        toolWindow.setTitleActions(List.of(new OpenSettingsAction()));
    }
}
