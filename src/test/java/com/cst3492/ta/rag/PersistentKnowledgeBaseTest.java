package com.cst3492.ta.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PersistentKnowledgeBaseTest {
    @TempDir Path directory;

    private KnowledgeBaseService persisted(Path store) throws IOException {
        return new KnowledgeBaseService(store);
    }

    @Test void restartRestoresTextAndPhysicalPageWithoutOriginalFile() throws Exception {
        Path store = directory.resolve("cache/state.bin");
        Path pdf = TestDocuments.write(directory.resolve("课件.pdf"), "", "Checked exceptions require catch or throws");
        // Ingestion canonicalizes Windows short names and directory aliases.
        // Resolve while the file exists, since this test deletes the original.
        Path expectedSource = pdf.toRealPath();
        persisted(store).ingest(pdf);
        Files.delete(pdf);
        var restored = persisted(store).retrieve("受检异常", 5);
        assertFalse(restored.isEmpty());
        assertEquals("p.2", restored.getChunks().getFirst().getPageRange());
        assertEquals(expectedSource.toString(), restored.getChunks().getFirst().getDocumentName());
    }

    @Test void replacementAndRemovalAreDurable() throws Exception {
        Path store = directory.resolve("state.bin");
        Path pdf = TestDocuments.write(directory.resolve("course.pdf"), "JDBC transactions");
        var kb = persisted(store);
        kb.ingest(pdf);
        TestDocuments.write(pdf, "JUnit lifecycle");
        kb.ingest(pdf);
        var reopened = persisted(store);
        assertTrue(reopened.retrieve("JDBC", 5).isEmpty());
        assertFalse(reopened.retrieve("JUnit", 5).isEmpty());
        Files.delete(pdf);
        assertTrue(reopened.removeDocument(pdf));
        assertTrue(persisted(store).retrieve("JUnit", 5).isEmpty());
    }

    @Test void failedWriteDoesNotPublishNewMemoryState() throws Exception {
        Path store = directory.resolve("state.bin");
        Path pdf = TestDocuments.write(directory.resolve("course.pdf"), "JDBC transactions");
        var kb = persisted(store);
        kb.ingest(pdf);
        byte[] previous = Files.readAllBytes(store);
        Files.delete(store);
        Files.createDirectory(store);
        Files.writeString(store.resolve("block"), "nonempty directory prevents replacement");
        Path another = TestDocuments.write(directory.resolve("new.pdf"), "JUnit lifecycle");
        assertThrows(IOException.class, () -> kb.ingest(another));
        assertFalse(kb.retrieve("JDBC", 5).isEmpty());
        assertTrue(kb.retrieve("JUnit", 5).isEmpty());
        assertThrows(IOException.class, () -> kb.removeDocument(pdf));
        assertFalse(kb.retrieve("JDBC", 5).isEmpty());
        assertTrue(previous.length > 0);
    }

    @Test void truncatedOrUnknownStoreIsReportedAndNotOverwritten() throws Exception {
        Path store = directory.resolve("state.bin");
        byte[] malformed = {1, 2, 3, 4, 5};
        Files.write(store, malformed);
        assertThrows(IOException.class, () -> persisted(store));
        assertArrayEquals(malformed, Files.readAllBytes(store));
    }

    @Test void separateStoresDoNotLeakProjectDocuments() throws Exception {
        var first = persisted(directory.resolve("a/state.bin"));
        first.ingest(TestDocuments.write(directory.resolve("one.pdf"), "JDBC"));
        assertTrue(persisted(directory.resolve("b/state.bin")).retrieve("JDBC", 5).isEmpty());
    }

    @Test void disposedBackendCannotOverwriteAReopenedProject() throws Exception {
        Path store = directory.resolve("state.bin");
        Path pdf = TestDocuments.write(directory.resolve("old.pdf"), "JDBC");
        var old = persisted(store);
        old.ingest(pdf);
        var lifecycle = assertInstanceOf(AutoCloseable.class, old, "Disposal must invalidate detached writers");
        lifecycle.close();
        var reopened = persisted(store);
        reopened.removeDocument(pdf);
        Path fresh = TestDocuments.write(directory.resolve("new.pdf"), "JUnit");
        reopened.ingest(fresh);
        assertThrows(java.util.concurrent.CancellationException.class, () -> old.ingest(pdf));
        var finalState = persisted(store);
        assertTrue(finalState.retrieve("JDBC", 5).isEmpty());
        assertFalse(finalState.retrieve("JUnit", 5).isEmpty());
    }

    @Test void blankAndSlideMetadataSurviveRestart() throws Exception {
        Path store = directory.resolve("state.bin");
        var kb = persisted(store);
        kb.ingest(TestDocuments.write(directory.resolve("blank.pdf"), "", ""));
        for (String format : java.util.List.of("ppt", "pptx")) {
            kb.ingest(TestDocuments.write(directory.resolve("course." + format), "", "JDBC"));
        }
        var restored = persisted(store);
        assertEquals(3, restored.documents().size());
        assertEquals(2, restored.documents().stream().filter(r -> r.chunkCount() == 0).findFirst().orElseThrow().pageCount());
        assertEquals(2, restored.retrieve("JDBC", 5).getChunks().size());
        assertTrue(restored.retrieve("JDBC", 5).getChunks().stream().allMatch(c -> c.getPageRange().equals("slide 2")));
    }

    @Test void cancellationAfterStagingPreservesDiskAndCleansTemporaryFile() throws Exception {
        Path store = directory.resolve("state.bin");
        persisted(store).ingest(TestDocuments.write(directory.resolve("course.pdf"), "JDBC"));
        byte[] before = Files.readAllBytes(store);
        var saved = KnowledgeBaseStore.read(store);
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> KnowledgeBaseStore.write(store, saved, () -> checks.incrementAndGet() == 1));
        assertArrayEquals(before, Files.readAllBytes(store));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }
}
