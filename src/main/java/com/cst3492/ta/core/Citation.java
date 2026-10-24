package com.cst3492.ta.core;

import java.util.Objects;

/**
 * A single source citation for a generated answer: the source document plus an
 * optional page range/scope.
 */
public final class Citation {

    private final String documentName;
    private final String pageRange; // may be null when unknown

    public Citation(String documentName, String pageRange) {
        this.documentName = Objects.requireNonNull(documentName, "documentName");
        this.pageRange = pageRange;
    }

    public String getDocumentName() {
        return documentName;
    }

    public String getPageRange() {
        return pageRange;
    }

    @Override
    public String toString() {
        return pageRange == null || pageRange.isBlank()
                ? documentName
                : documentName + ", " + pageRange;
    }
}
