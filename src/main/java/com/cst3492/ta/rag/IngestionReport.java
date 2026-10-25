package com.cst3492.ta.rag;

import java.nio.file.Path;

/** Counts include blank pages/slides; a valid image-only document has zero chunks. */
public record IngestionReport(Path path, String documentName, int pageCount, int chunkCount) {
    public boolean hasExtractableText() {
        return chunkCount > 0;
    }
}
