package com.cst3492.ta.rag;

import com.cst3492.ta.core.RetrievalResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Standalone, in-memory course knowledge base. No IDE, API key, or LLM is required.
 * Import on a background thread: parsing and index rebuilding can take time.
 * Searches use immutable snapshots; successful imports/removals publish atomically.
 */
public final class KnowledgeBaseService {
    public static final int DEFAULT_TOP_K = 5;

    private final Map<Path, List<PageChunker.Chunk>> documents = new HashMap<>();
    private final DocumentExtractor extractor = new DocumentExtractor();
    private final PageChunker chunker = new PageChunker();
    private volatile KeywordIndex index = new KeywordIndex(List.of());

    /** Reimport replaces this file's old chunks only after extraction succeeds. */
    public IngestionReport ingest(Path path) throws DocumentIngestionException {
        Objects.requireNonNull(path, "path");
        Path realPath;
        try {
            realPath = path.toRealPath();
        } catch (IOException exception) {
            throw new DocumentIngestionException(DocumentIngestionException.Reason.UNREADABLE_FILE,
                    "Cannot read document: " + path, exception);
        }
        var document = extractor.extract(realPath);
        var chunks = chunker.split(realPath, document);
        synchronized (this) {
            Map<Path, List<PageChunker.Chunk>> updated = new HashMap<>(documents);
            updated.put(realPath, chunks);
            KeywordIndex replacement = new KeywordIndex(updated.values());
            documents.clear();
            documents.putAll(updated);
            index = replacement;
        }
        return new IngestionReport(realPath, realPath.toString(), document.pages().size(), chunks.size());
    }

    public RetrievalResult retrieve(String question) {
        return retrieve(question, DEFAULT_TOP_K);
    }

    /** Null, blank, stopword-only and unmatched questions all produce an empty result. */
    public RetrievalResult retrieve(String question, int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        return index.retrieve(question, topK);
    }

    /** Returns whether an imported document was removed. Also works after the file is deleted. */
    public synchronized boolean removeDocument(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path absolute = path.toAbsolutePath().normalize();
        // A deleted file cannot resolve itself, but its parent can still expand Windows
        // short-path aliases (and directory symlinks) used by the original import.
        Path identity = Files.exists(path) ? path.toRealPath()
                : Files.exists(absolute.getParent())
                ? absolute.getParent().toRealPath().resolve(absolute.getFileName()) : absolute;
        if (!documents.containsKey(identity)) {
            return false;
        }
        Map<Path, List<PageChunker.Chunk>> updated = new HashMap<>(documents);
        updated.remove(identity);
        KeywordIndex replacement = new KeywordIndex(updated.values());
        documents.clear();
        documents.putAll(updated);
        index = replacement;
        return true;
    }
}
