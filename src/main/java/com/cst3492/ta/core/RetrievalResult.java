package com.cst3492.ta.core;

import java.util.Collections;
import java.util.List;

/**
 * The outcome of retrieving knowledge chunks for a query.
 *
 * <p>An empty result means the retrieval module found nothing relevant (the
 * RAG module is expected to apply its own relevance/score threshold before
 * building this object).</p>
 */
public final class RetrievalResult {

    private final List<RetrievedChunk> chunks; // top-k relevant chunks, may be empty

    public RetrievalResult(List<RetrievedChunk> chunks) {
        // Own the snapshot: callers must not change what prompting/citation validation sees.
        this.chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public List<RetrievedChunk> getChunks() {
        return chunks;
    }

    /** True when at least one relevant chunk is available to cite. */
    public boolean isRelevant() {
        return !chunks.isEmpty();
    }

    /** True when no chunks were retrieved. */
    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public static RetrievalResult empty() {
        return new RetrievalResult(Collections.emptyList());
    }
}
