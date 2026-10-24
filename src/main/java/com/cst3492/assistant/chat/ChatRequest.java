package com.cst3492.assistant.chat;

import java.util.Objects;

/** Immutable send-time snapshot; an empty editorContext means no code was attached. */
public record ChatRequest(String question, String editorContext) {
    public ChatRequest {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(editorContext, "editorContext");
        question = question.strip();
        if (question.isBlank()) {
            throw new IllegalArgumentException("A question is required before sending");
        }
        if (!editorContext.isEmpty() && !hasSelectedCode(editorContext)) {
            throw new IllegalArgumentException("Selected code must not be whitespace-only");
        }
        // Do not strip, indent, normalize line endings, or otherwise modify selected code.
    }

    public static boolean hasSelectedCode(String selectedCode) {
        return selectedCode != null && !selectedCode.isBlank();
    }
}
