package com.cst3492.assistant.chat;

import java.util.Objects;

/** A one-based location supplied by a service; this model does not read any document. */
public record CourseSource(String documentName, Type type, int number, boolean testData) {
    public enum Type { PDF_PAGE, PPT_SLIDE }

    public CourseSource {
        Objects.requireNonNull(documentName, "documentName");
        Objects.requireNonNull(type, "type");
        if (documentName.isBlank() || number < 1) {
            throw new IllegalArgumentException("A source needs a document name and a positive page/slide number");
        }
    }
}
