package com.cst3492.assistant.knowledge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;

/** IntelliJ creates one light service per project and disposes it on project close. */
@Service(Service.Level.PROJECT)
public final class KnowledgeBaseProjectService implements Disposable {
    private final ProjectKnowledgeBase knowledgeBase = new ProjectKnowledgeBase();
    public ProjectKnowledgeBase knowledgeBase() { return knowledgeBase; }
    @Override public void dispose() { knowledgeBase.close(); }
}
