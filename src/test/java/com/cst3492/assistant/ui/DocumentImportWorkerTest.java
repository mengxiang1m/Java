package com.cst3492.assistant.ui;

import com.cst3492.ta.rag.KnowledgeBaseService;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DocumentImportWorkerTest {
    @TempDir Path temp;

    @Test
    void importsUsefulAndBlankDocumentsWhileReportingEachFailedFile() throws Exception {
        Path useful = slides("Lecture.pptx", "JDBC transaction rollback");
        Path blank = slides("Blank.pptx", "");
        Path broken = Files.writeString(temp.resolve("Broken.pdf"), "not a PDF");
        var knowledge = new KnowledgeBaseService();
        var presented = new AtomicReference<DocumentImportWorker.Summary>();
        var callbackOnEdt = new AtomicBoolean();
        var finished = new CountDownLatch(1);
        var worker = new DocumentImportWorker(knowledge, List.of(useful, broken, blank),
                pending -> true, summary -> {
                    callbackOnEdt.set(SwingUtilities.isEventDispatchThread());
                    presented.set(summary);
                }, pending -> finished.countDown());

        worker.execute();
        var summary = worker.get(10, TimeUnit.SECONDS);
        assertTrue(finished.await(5, TimeUnit.SECONDS));

        assertEquals(2, summary.documents());
        assertEquals(1, summary.chunks());
        assertEquals(1, summary.withoutText());
        assertEquals(1, summary.problems().size());
        assertTrue(summary.problems().getFirst().startsWith("Broken.pdf:"));
        assertEquals(1, knowledge.retrieve("JDBC").getChunks().size());
        assertEquals(summary, presented.get());
        assertTrue(callbackOnEdt.get());
    }

    @Test
    void rejectsOversizedAndUnreadableInputsWithoutCrashingTheBatch() throws Exception {
        Path large = temp.resolve("Large.pdf");
        try (var file = Files.newByteChannel(large, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            file.position(DocumentImportWorker.MAX_FILE_BYTES);
            file.write(ByteBuffer.wrap(new byte[]{0}));
        }
        var finished = new CountDownLatch(1);
        var worker = new DocumentImportWorker(new KnowledgeBaseService(),
                List.of(large, temp.resolve("missing.pdf"), temp.toAbsolutePath().getRoot()),
                pending -> true, summary -> { }, pending -> finished.countDown());
        worker.execute();
        var summary = worker.get(10, TimeUnit.SECONDS);
        assertTrue(finished.await(5, TimeUnit.SECONDS));

        assertEquals(0, summary.documents());
        assertEquals(3, summary.problems().size());
        assertTrue(summary.problems().getFirst().contains("50 MiB"));
    }

    @Test
    void inactiveOwnerSuppressesPresentationButStillFinishesBookkeeping() throws Exception {
        var completed = new AtomicBoolean();
        var finished = new CountDownLatch(1);
        var worker = new DocumentImportWorker(new KnowledgeBaseService(), List.of(),
                pending -> false, summary -> completed.set(true), pending -> finished.countDown());
        worker.execute();
        worker.get(5, TimeUnit.SECONDS);
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertFalse(completed.get());
    }

    private Path slides(String name, String text) throws Exception {
        Path path = temp.resolve(name);
        try (var show = new XMLSlideShow(); var output = Files.newOutputStream(path)) {
            var slide = show.createSlide();
            if (!text.isBlank()) {
                slide.createTextBox().setText(text);
            }
            show.write(output);
        }
        return path;
    }
}
