package com.cst3492.assistant.knowledge;

import com.cst3492.ta.core.RetrievalResult;
import com.cst3492.ta.rag.DocumentIngestionException;
import com.cst3492.ta.rag.IngestionReport;
import com.cst3492.ta.rag.KnowledgeBaseService;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Project-owned snapshots. Disk restoration and mutations run only on background threads. */
public final class ProjectKnowledgeBase implements AutoCloseable {
    public enum State { UNINITIALIZED, EMPTY, READY, CLOSED }
    public record Snapshot(State state, boolean importing, List<IngestionReport> documents,
                           DocumentIngestionException.Reason lastImportFailure) { }

    private final Object mutations = new Object();
    private final LinkedHashMap<Path, IngestionReport> documents = new LinkedHashMap<>();
    private final Path snapshotFile;
    private boolean restored;
    private KnowledgeBaseService backend = new KnowledgeBaseService();
    private volatile Snapshot snapshot = new Snapshot(State.UNINITIALIZED, false, List.of(), null);

    public ProjectKnowledgeBase() { snapshotFile = null; restored = true; }

    /** Construction is safe on the EDT: actual restore is deferred to background work. */
    public ProjectKnowledgeBase(Path snapshotFile) {
        this.snapshotFile = java.util.Objects.requireNonNull(snapshotFile).toAbsolutePath().normalize();
    }

    public Snapshot snapshot() { return snapshot; }

    public IngestionReport ingest(Path path) throws IOException {
        backgroundOnly();
        restore();
        synchronized (mutations) {
            KnowledgeBaseService active;
            synchronized (this) {
                active = open();
                snapshot = new Snapshot(snapshot.state(), true, snapshot.documents(), null);
            }
            try {
                IngestionReport report = active.ingest(path);
                synchronized (this) {
                    ensureNotClosed();
                    documents.put(report.path(), report);
                    publish(null);
                }
                return report;
            } catch (IOException | RuntimeException exception) {
                synchronized (this) {
                    if (snapshot.state() != State.CLOSED) {
                        snapshot = new Snapshot(snapshot.state(), false, snapshot.documents(),
                                exception instanceof DocumentIngestionException failure ? failure.getReason() : null);
                    }
                }
                throw exception;
            }
        }
    }

    public boolean removeDocument(Path path) throws IOException {
        backgroundOnly();
        restore();
        synchronized (mutations) {
            KnowledgeBaseService active;
            synchronized (this) { active = open(); }
            Path absolute = path.toAbsolutePath().normalize();
            Path identity = Files.exists(path) ? path.toRealPath()
                    : Files.exists(absolute.getParent())
                    ? absolute.getParent().toRealPath().resolve(absolute.getFileName()) : absolute;
            boolean removed = active.removeDocument(identity);
            synchronized (this) {
                ensureNotClosed();
                if (removed) {
                    // UI supplies the canonical identity in IngestionReport, including deleted files.
                    documents.remove(identity);
                }
                publish(null);
            }
            return removed;
        }
    }

    public RetrievalResult retrieve(String question, int topK) {
        backgroundOnly();
        try { restore(); }
        catch (IOException failure) { throw new UncheckedIOException("Cannot restore saved course knowledge", failure); }
        KnowledgeBaseService active;
        synchronized (this) { active = open(); }
        RetrievalResult result = active.retrieve(question, topK);
        synchronized (this) { open(); }
        return result;
    }

    private void restore() throws IOException {
        synchronized (this) {
            open();
            if (restored) return;
        }
        synchronized (mutations) {
            synchronized (this) {
                open();
                if (restored) return;
            }
            // Never hold the monitor used by close() while accessing disk or building the index.
            var replacement = new KnowledgeBaseService(snapshotFile);
            synchronized (this) {
                open();
                backend = replacement;
                for (var report : replacement.documents()) documents.put(report.path(), report);
                restored = true;
                publish(null);
            }
        }
    }

    private KnowledgeBaseService open() {
        ensureNotClosed();
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Knowledge base is closed or request cancelled");
        }
        return backend;
    }

    private void ensureNotClosed() {
        if (snapshot.state() == State.CLOSED) throw new CancellationException("Knowledge base is closed");
    }

    private void publish(DocumentIngestionException.Reason failure) {
        List<IngestionReport> reports = List.copyOf(documents.values());
        boolean content = reports.stream().anyMatch(IngestionReport::hasExtractableText);
        snapshot = new Snapshot(content ? State.READY : State.EMPTY, false, reports, failure);
    }

    @Override public synchronized void close() {
        // Never wait for parsing on the EDT. An in-flight parser owns only its detached instance.
        if (backend != null) backend.close();
        backend = null;
        documents.clear();
        snapshot = new Snapshot(State.CLOSED, false, List.of(), null);
    }

    private static void backgroundOnly() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Knowledge base operations must run off the EDT");
        }
    }
}
