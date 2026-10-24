package com.cst3492.assistant.actions;

import com.cst3492.assistant.chat.ChatRequest;
import com.cst3492.assistant.ui.AssistantChatPanel;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

/** Stateless Action: editor/project references are never retained by this long-lived object. */
public final class AskAssistantAction extends DumbAwareAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean javaEditor = file != null && "java".equalsIgnoreCase(file.getExtension());
        Project project = event.getProject();
        event.getPresentation().setVisible(javaEditor);
        event.getPresentation().setEnabled(javaEditor && project != null && !project.isDisposed()
                && editor != null && !editor.isDisposed()
                && ChatRequest.hasSelectedCode(editor.getSelectionModel().getSelectedText()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || project.isDisposed() || editor == null || editor.isDisposed()
                || file == null || !"java".equalsIgnoreCase(file.getExtension())) {
            return;
        }
        // Capture before focus switches; later selections cannot change this snapshot.
        String selectedCode = editor.getSelectionModel().getSelectedText();
        if (!ChatRequest.hasSelectedCode(selectedCode)) {
            return;
        }
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CST3492 Assistant");
        if (toolWindow == null) {
            return;
        }
        toolWindow.activate(() -> {
            if (project.isDisposed() || toolWindow.isDisposed()) {
                return;
            }
            // Activation initializes lazy Tool Window content. Reuse its existing panel.
            for (Content content : toolWindow.getContentManager().getContents()) {
                if (content.getComponent() instanceof AssistantChatPanel panel) {
                    toolWindow.getContentManager().setSelectedContent(content);
                    panel.setEditorContext(selectedCode);
                    return;
                }
            }
        });
    }
}
