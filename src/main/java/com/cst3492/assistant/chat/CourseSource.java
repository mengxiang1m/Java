package com.cst3492.assistant.chat;

import java.util.Objects;
import java.util.List;

/** A one-based location supplied by a service; this model does not read any document. */
public record CourseSource(String documentName, Type type, int number, boolean testData,
                           List<Excerpt> excerpts) {
    public enum Type { PDF_PAGE, PPT_SLIDE }

    /** Text is copied verbatim from retrieval. Null score means no finite score was supplied. */
    public record Excerpt(String text, Double score) {
        public Excerpt {
            Objects.requireNonNull(text, "text");
            if (score != null && !Double.isFinite(score)) score = null;
        }
    }

    /** Existing metadata-only callers, including Mock fixtures, remain supported. */
    public CourseSource(String documentName, Type type, int number, boolean testData) {
        this(documentName, type, number, testData, List.of());
    }

    public String displayName() {
        // Source identity stays intact, even when a Windows path is rendered on another OS.
        int separator = Math.max(documentName.lastIndexOf('/'), documentName.lastIndexOf('\\'));
        return documentName.substring(separator + 1);
    }

    public CourseSource {
        Objects.requireNonNull(documentName, "documentName");
        Objects.requireNonNull(type, "type");
        excerpts = List.copyOf(excerpts);
        if (documentName.isBlank() || number < 1) {
            throw new IllegalArgumentException("A source needs a document name and a positive page/slide number");
        }
    }
}
