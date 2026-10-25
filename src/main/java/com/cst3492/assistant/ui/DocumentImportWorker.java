package com.cst3492.assistant.ui;

import com.cst3492.ta.rag.DocumentIngestionException;
import com.cst3492.ta.rag.KnowledgeBaseService;

import javax.swing.SwingWorker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Imports selected local documents off the EDT; one bad file does not discard the others. */
final class DocumentImportWorker extends SwingWorker<DocumentImportWorker.Summary, Void> {
    static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    record Summary(int documents, int chunks, int withoutText, List<String> problems) {
        Summary { problems = List.copyOf(problems); }

        String message() {
            return "Imported " + documents + " document(s), " + chunks + " chunk(s)."
                    + (withoutText == 0 ? "" : " " + withoutText + " document(s) have no extractable text.")
                    + (problems.isEmpty() ? "" : " " + problems.size() + " file(s) could not be imported.");
        }
    }

    private final KnowledgeBaseService knowledge;
    private final List<Path> paths;
    private final Predicate<DocumentImportWorker> active;
    private final Consumer<Summary> completed;
    private final Consumer<DocumentImportWorker> finished;

    DocumentImportWorker(KnowledgeBaseService knowledge, List<Path> paths,
                         Predicate<DocumentImportWorker> active, Consumer<Summary> completed,
                         Consumer<DocumentImportWorker> finished) {
        this.knowledge = knowledge;
        this.paths = List.copyOf(paths);
        this.active = active;
        this.completed = completed;
        this.finished = finished;
    }

    @Override
    protected Summary doInBackground() {
        int documents = 0;
        int chunks = 0;
        int withoutText = 0;
        List<String> problems = new ArrayList<>();
        for (Path path : paths) {
            if (isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException();
            }
            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            try {
                if (Files.size(path) > MAX_FILE_BYTES) {
                    problems.add(name + ": exceeds the 50 MiB file limit.");
                    continue;
                }
                var report = knowledge.ingest(path);
                documents++;
                chunks += report.chunkCount();
                if (!report.hasExtractableText()) {
                    withoutText++;
                }
            } catch (DocumentIngestionException exception) {
                String reason = switch (exception.getReason()) {
                    case UNSUPPORTED_FORMAT -> "choose a PDF, PPTX or PPT file.";
                    case UNREADABLE_FILE -> "file cannot be read.";
                    case ENCRYPTED_DOCUMENT -> "encrypted documents are not supported.";
                    case MALFORMED_DOCUMENT -> "file is damaged or has the wrong format.";
                };
                problems.add(name + ": " + reason);
            } catch (IOException exception) {
                problems.add(name + ": file cannot be read.");
            }
        }
        return new Summary(documents, chunks, withoutText, problems);
    }

    @Override
    protected void done() {
        try {
            if (!active.test(this)) {
                return;
            }
            try {
                completed.accept(get());
            } catch (CancellationException exception) {
                // A closing Tool Window cancels its worker and suppresses presentation.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException exception) {
                completed.accept(new Summary(0, 0, 0,
                        List.of("Import could not finish. Please try again.")));
            }
        } finally {
            finished.accept(this);
        }
    }
}
