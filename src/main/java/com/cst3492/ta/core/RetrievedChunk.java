package com.cst3492.ta.core;

import java.util.Objects;

/**
 * A single knowledge chunk retrieved from the course materials, together with
 * its source citation.
 *
 * <p>Produced by the retrieval (RAG) module and consumed by the LLM
 * answer-generation module. The {@code documentName} and {@code pageRange} are
 * shown verbatim in the citations, so the retrieval module must fill them with
 * the real source identity.</p>
 */
public final class RetrievedChunk {

    private final String documentName; // e.g. "Lecture3_JDBC.pdf"
    private final String pageRange;    // e.g. "p.12" or "slides 12-14"; may be null when unknown
    private final String content;      // the chunk text
    private final double score;        // retrieval relevance score (higher = more relevant)

    public RetrievedChunk(String documentName, String pageRange, String content, double score) {
        this.documentName = Objects.requireNonNull(documentName, "documentName");
        this.pageRange = pageRange;
        this.content = Objects.requireNonNull(content, "content");
        this.score = score;
    }

    public String getDocumentName() {
        return documentName;
    }

    public String getPageRange() {
        return pageRange;
    }

    public String getContent() {
        return content;
    }

    public double getScore() {
        return score;
    }
}
