package com.cst3492.ta.core;

import java.util.Objects;

/**
 * A question from the user together with optional editor context (the selected
 * code) and the retrieval results for that question.
 *
 * <p>This is the input contract between the UI module and the LLM module.</p>
 */
public final class AssistantQuery {

    private final String question;
    private final String selectedCode; // nullable: the Java code selected in the editor
    private final RetrievalResult retrieval;

    public AssistantQuery(String question, String selectedCode, RetrievalResult retrieval) {
        this.question = Objects.requireNonNull(question, "question");
        this.selectedCode = selectedCode;
        this.retrieval = retrieval == null ? RetrievalResult.empty() : retrieval;
    }

    public String getQuestion() {
        return question;
    }

    public String getSelectedCode() {
        return selectedCode;
    }

    public RetrievalResult getRetrieval() {
        return retrieval;
    }

    /** True if the user selected some non-blank code in the editor. */
    public boolean hasSelectedCode() {
        return selectedCode != null && !selectedCode.isBlank();
    }
}
