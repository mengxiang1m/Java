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
 * Standalone course knowledge base with optional disk snapshots. No IDE or LLM is required.
 * Import on a background thread: parsing and index rebuilding can take time.
 * Searches use immutable snapshots; successful imports/removals publish atomically.
 */
public final class KnowledgeBaseService implements AutoCloseable {
    public static final int DEFAULT_TOP_K = 5;

    private final Map<Path, List<PageChunker.Chunk>> documents = new HashMap<>();
    private final Map<Path, IngestionReport> reports = new HashMap<>();
    private final Path snapshotFile;
    private volatile boolean closed;
    private final DocumentExtractor extractor = new DocumentExtractor();
    private final PageChunker chunker = new PageChunker();
    private volatile KeywordIndex index = new KeywordIndex(List.of());

    public KnowledgeBaseService() { snapshotFile = null; }

    /** Restore extracted content, including sources that have since moved or disappeared. */
    public KnowledgeBaseService(Path snapshotFile) throws IOException {
        this.snapshotFile = Objects.requireNonNull(snapshotFile).toAbsolutePath().normalize();
        for (var document : KnowledgeBaseStore.read(this.snapshotFile)) {
            documents.put(document.report().path(), document.chunks());
            reports.put(document.report().path(), document.report());
        }
        index = new KeywordIndex(documents.values());
    }

    public synchronized List<IngestionReport> documents() {
        return reports.values().stream().sorted(java.util.Comparator.comparing(r -> r.path().toString())).toList();
    }

    /** Reimport replaces this file's old chunks only after extraction succeeds. */
    public IngestionReport ingest(Path path) throws IOException {
        ensureOpen();
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
        var report = new IngestionReport(realPath, realPath.toString(), document.pages().size(), chunks.size());
        synchronized (this) {
            ensureOpen();
            Map<Path, List<PageChunker.Chunk>> updated = new HashMap<>(documents);
            updated.put(realPath, chunks);
            KeywordIndex replacement = new KeywordIndex(updated.values());
            var updatedReports = new HashMap<>(reports);
            updatedReports.put(realPath, report);
            persist(updated, updatedReports);
            ensureOpen();
            documents.clear();
            documents.putAll(updated);
            reports.clear();
            reports.putAll(updatedReports);
            index = replacement;
        }
        return report;
    }

    public RetrievalResult retrieve(String question) {
        return retrieve(question, DEFAULT_TOP_K);
    }

    /** Null, blank, stopword-only and unmatched questions all produce an empty result. */
    public RetrievalResult retrieve(String question, int topK) {
        ensureOpen();
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        return index.retrieve(question, topK);
    }

    /** Returns whether an imported document was removed. Also works after the file is deleted. */
    public synchronized boolean removeDocument(Path path) throws IOException {
        ensureOpen();
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
        var updatedReports = new HashMap<>(reports);
        updatedReports.remove(identity);
        persist(updated, updatedReports);
        ensureOpen();
        documents.clear();
        documents.putAll(updated);
        reports.clear();
        reports.putAll(updatedReports);
        index = replacement;
        return true;
    }

    private void persist(Map<Path, List<PageChunker.Chunk>> chunks, Map<Path, IngestionReport> metadata) throws IOException {
        if (snapshotFile != null) {
            KnowledgeBaseStore.write(snapshotFile, metadata.values().stream()
                    .sorted(java.util.Comparator.comparing(r -> r.path().toString()))
                    .map(report -> new KnowledgeBaseStore.Document(report, chunks.get(report.path()))).toList(),
                    () -> !closed && !Thread.currentThread().isInterrupted());
        }
    }

    private void ensureOpen() {
        if (closed || Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Knowledge base closed or operation cancelled");
        }
    }

    /** Does not acquire parsing, mutation, or disk locks; safe during project disposal. */
    @Override public void close() { closed = true; }
}
