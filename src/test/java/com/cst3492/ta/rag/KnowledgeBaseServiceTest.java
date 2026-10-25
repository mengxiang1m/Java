package com.cst3492.ta.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseServiceTest {
    @TempDir Path temp;

    @Test
    void repeatedImportsReplaceOldContentWithoutDuplicatingChunks() throws Exception {
        Path path = TestDocuments.write(temp.resolve("Lecture.pdf"), "obsoletealpha");
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(path);
        knowledge.ingest(temp.resolve(".").resolve("Lecture.pdf"));
        assertEquals(1, knowledge.retrieve("obsoletealpha").getChunks().size());
        TestDocuments.write(path, "currentbeta");
        knowledge.ingest(path);
        assertTrue(knowledge.retrieve("obsoletealpha").isEmpty());
        assertEquals(1, knowledge.retrieve("currentbeta").getChunks().size());
        TestDocuments.write(path, "");
        assertEquals(0, knowledge.ingest(path).chunkCount());
        assertTrue(knowledge.retrieve("currentbeta").isEmpty());
    }

    @Test
    void failedReimportPreservesThePreviousSearchableSnapshot() throws Exception {
        Path path = TestDocuments.write(temp.resolve("Lecture.pptx"), "rollback transaction");
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(path);
        Files.writeString(path, "broken replacement");
        assertThrows(DocumentIngestionException.class, () -> knowledge.ingest(path));
        assertEquals(1, knowledge.retrieve("rollback").getChunks().size());
    }

    @Test
    void removesOnlyTheRequestedDocumentEvenAfterItsFileIsDeleted() throws Exception {
        Path first = TestDocuments.write(temp.resolve("First.pdf"), "JDBC connection");
        Path second = TestDocuments.write(temp.resolve("Second.ppt"), "servlet lifecycle");
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(first);
        knowledge.ingest(second);
        Files.delete(first);
        assertTrue(knowledge.removeDocument(first));
        assertFalse(knowledge.removeDocument(first));
        assertTrue(knowledge.retrieve("jdbc").isEmpty());
        assertEquals(1, knowledge.retrieve("servlet").getChunks().size());
    }

    @Test
    void sameNamedFilesInDifferentDirectoriesHaveDistinctRealSourceIdentities() throws Exception {
        Path first = Files.createDirectory(temp.resolve("chapter1")).resolve("Lecture.pdf");
        Path second = Files.createDirectory(temp.resolve("chapter2")).resolve("Lecture.pdf");
        TestDocuments.write(first, "common JDBC");
        TestDocuments.write(second, "common servlet");
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(first);
        knowledge.ingest(second);
        var chunks = knowledge.retrieve("common").getChunks();
        assertEquals(2, chunks.size());
        assertEquals(first.toRealPath().toString(), chunks.get(0).getDocumentName());
        assertEquals(second.toRealPath().toString(), chunks.get(1).getDocumentName());
    }

    @Test
    void emptyQuestionsStopwordsAndUnmatchedTermsReturnNoSources() throws Exception {
        var knowledge = new KnowledgeBaseService();
        assertTrue(knowledge.retrieve("JDBC").isEmpty());
        knowledge.ingest(TestDocuments.write(temp.resolve("Lecture.pptx"), "JDBC 数据库连接"));
        for (String question : new String[]{null, "", "  \n", "the of to and", "什么", "unmatchedxyz", "!?#"}) {
            var result = knowledge.retrieve(question);
            assertTrue(result.isEmpty(), "Question: " + question);
            assertFalse(result.isRelevant());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsInvalidTopK(int topK) {
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeBaseService().retrieve("jdbc", topK));
    }

    @Test
    void defaultTopFiveAndPageTieBreakAreDeterministic() throws Exception {
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(TestDocuments.write(temp.resolve("Lecture.pdf"),
                "JDBC", "JDBC", "JDBC", "JDBC", "JDBC", "JDBC", "JDBC"));
        assertEquals(List.of("p.1", "p.2", "p.3", "p.4", "p.5"),
                knowledge.retrieve("jdbc").getChunks().stream().map(c -> c.getPageRange()).toList());
        assertEquals(2, knowledge.retrieve("jdbc", 2).getChunks().size());
        assertEquals(7, knowledge.retrieve("jdbc", 100).getChunks().size());
    }

    @Test
    void resultsRemainImmutableAfterLaterImportsAndRemovals() throws Exception {
        var knowledge = new KnowledgeBaseService();
        Path first = TestDocuments.write(temp.resolve("First.pdf"), "JDBC");
        knowledge.ingest(first);
        var before = knowledge.retrieve("jdbc");
        knowledge.ingest(TestDocuments.write(temp.resolve("Second.pptx"), "JDBC JDBC"));
        knowledge.removeDocument(first);
        assertEquals(1, before.getChunks().size());
        assertEquals(first.toRealPath().toString(), before.getChunks().getFirst().getDocumentName());
        assertThrows(UnsupportedOperationException.class, () -> before.getChunks().clear());
    }

    @Test
    void concurrentImportsDoNotLoseDocuments() throws Exception {
        var paths = List.of(TestDocuments.write(temp.resolve("A.pdf"), "uniquealpha"),
                TestDocuments.write(temp.resolve("B.pptx"), "uniquebeta"),
                TestDocuments.write(temp.resolve("C.ppt"), "uniquegamma"));
        var knowledge = new KnowledgeBaseService();
        try (var executor = Executors.newFixedThreadPool(3)) {
            List<Callable<IngestionReport>> imports = paths.stream()
                    .<Callable<IngestionReport>>map(path -> () -> knowledge.ingest(path)).toList();
            for (var completed : executor.invokeAll(imports)) {
                assertEquals(1, completed.get().chunkCount());
            }
        }
        assertEquals(3, knowledge.retrieve("uniquealpha uniquebeta uniquegamma").getChunks().size());
    }

    @Test
    void mixedFormatsReturnOnlyRelevantPhysicalLocations() throws Exception {
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(TestDocuments.write(temp.resolve("Jdbc.pdf"), "Servlet lifecycle", "JDBC transaction rollback"));
        knowledge.ingest(TestDocuments.write(temp.resolve("Database.pptx"), "", "数据库连接使用 JDBC"));
        knowledge.ingest(TestDocuments.write(temp.resolve("Web.ppt"), "HTTP servlet lifecycle"));
        var result = knowledge.retrieve("JDBC 数据库连接");
        assertEquals(2, result.getChunks().size());
        assertTrue(result.getChunks().stream().anyMatch(c -> c.getDocumentName().endsWith("Jdbc.pdf")
                && c.getPageRange().equals("p.2")));
        assertTrue(result.getChunks().stream().anyMatch(c -> c.getDocumentName().endsWith("Database.pptx")
                && c.getPageRange().equals("slide 2")));
        assertTrue(result.getChunks().stream().noneMatch(c -> c.getDocumentName().endsWith("Web.ppt")));
    }
}
