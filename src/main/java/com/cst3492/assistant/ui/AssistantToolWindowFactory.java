package com.cst3492.assistant.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.cst3492.assistant.chat.ChatServiceFactory;
import com.cst3492.ta.rag.KnowledgeBaseService;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** Entry point for the assistant UI. Keep future application logic outside this factory. */
public final class AssistantToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        KnowledgeBaseService knowledge = new KnowledgeBaseService();
        ChatServiceFactory.Selection selection = ChatServiceFactory.create(System.getenv(), knowledge);
        AssistantChatPanel panel = new AssistantChatPanel(project, selection.service(), selection.description());
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setDisposer(panel);
        content.setPreferredFocusableComponent(panel.getInput());
        toolWindow.getContentManager().addContent(content);
    }
}
