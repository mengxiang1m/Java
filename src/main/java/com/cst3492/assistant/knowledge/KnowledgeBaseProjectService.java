package com.cst3492.assistant.knowledge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;

/** IntelliJ creates one light service per project and disposes it on project close. */
@Service(Service.Level.PROJECT)
public final class KnowledgeBaseProjectService implements Disposable {
    private final ProjectKnowledgeBase knowledgeBase;
    public KnowledgeBaseProjectService(Project project) {
        knowledgeBase = new ProjectKnowledgeBase(Path.of(PathManager.getSystemPath(),
                "cst3492-knowledge", project.getLocationHash(), "snapshot.bin"));
    }
    public ProjectKnowledgeBase knowledgeBase() { return knowledgeBase; }
    @Override public void dispose() { knowledgeBase.close(); }
}
