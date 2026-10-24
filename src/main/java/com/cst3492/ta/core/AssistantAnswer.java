package com.cst3492.ta.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The answer returned to the UI.
 *
 * <p>{@code content} is the answer text (with inline {@code [1]}, {@code [2]}
 * markers), {@code citations} lists the sources actually used, and
 * {@code basedOnCourseMaterial} tells whether the answer is grounded in the
 * course material or was generated from the model's general knowledge.</p>
 */
public final class AssistantAnswer {

    private final String content;
    private final List<Citation> citations;
    private final boolean basedOnCourseMaterial;

    public AssistantAnswer(String content, List<Citation> citations, boolean basedOnCourseMaterial) {
        this.content = Objects.requireNonNull(content, "content");
        this.citations = Collections.unmodifiableList(
                citations == null ? Collections.emptyList() : citations);
        this.basedOnCourseMaterial = basedOnCourseMaterial;
    }

    public String getContent() {
        return content;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public boolean isBasedOnCourseMaterial() {
        return basedOnCourseMaterial;
    }

    /** An answer that is explicitly not grounded in any course material. */
    public static AssistantAnswer generalKnowledge(String content) {
        return new AssistantAnswer(content, Collections.emptyList(), false);
    }
}
