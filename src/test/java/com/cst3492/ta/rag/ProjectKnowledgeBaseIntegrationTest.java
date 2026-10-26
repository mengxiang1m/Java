package com.cst3492.ta.rag;

import com.cst3492.assistant.chat.*;
import com.cst3492.assistant.knowledge.ProjectKnowledgeBase;
import com.cst3492.ta.core.RetrievalResult;
import com.cst3492.ta.llm.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real document extraction + A retrieval + B prompting/parsing + C mapping, no network. */
class ProjectKnowledgeBaseIntegrationTest {
    @TempDir Path directory;
    private final LlmConfig config = LlmConfig.deepseek("offline-test-placeholder");

    @Test void projectReopensSavedKnowledgeOnFirstBackgroundQuestion() throws Exception {
        Path store = directory.resolve("project/state.bin");
        try (var first = new ProjectKnowledgeBase(store)) {
            first.ingest(TestDocuments.write(directory.resolve("course.pdf"), "", "JUnit BeforeEach"));
        }
        try (var reopened = new ProjectKnowledgeBase(store)) {
            assertEquals(ProjectKnowledgeBase.State.UNINITIALIZED, reopened.snapshot().state());
            assertEquals("p.2", reopened.retrieve("单元测试前置", 5).getChunks().getFirst().getPageRange());
            assertEquals(ProjectKnowledgeBase.State.READY, reopened.snapshot().state());
            assertEquals(1, reopened.snapshot().documents().size());
            assertEquals(2, reopened.snapshot().documents().getFirst().pageCount());
        }
    }

    @Test void constructingProjectOnEdtDoesNotReadCorruptDiskAndCloseStaysSafe() throws Exception {
        Path store = directory.resolve("bad.bin");
        Files.writeString(store, "not a snapshot");
        var holder = new java.util.concurrent.atomic.AtomicReference<ProjectKnowledgeBase>();
        SwingUtilities.invokeAndWait(() -> assertDoesNotThrow(() -> holder.set(new ProjectKnowledgeBase(store))));
        try (var kb = holder.get()) {
            assertThrows(java.io.UncheckedIOException.class, () -> kb.retrieve("JUnit", 5));
            assertEquals(ProjectKnowledgeBase.State.UNINITIALIZED, kb.snapshot().state());
        }
        assertEquals("not a snapshot", Files.readString(store));
    }

    @Test void closingProjectInvalidatesDetachedPersistentWriter() throws Exception {
        Path store = directory.resolve("project/state.bin");
        Path pdf = TestDocuments.write(directory.resolve("course.pdf"), "JDBC");
        var oldProject = new ProjectKnowledgeBase(store);
        oldProject.ingest(pdf);
        var field = ProjectKnowledgeBase.class.getDeclaredField("backend");
        field.setAccessible(true);
        var detached = (KnowledgeBaseService) field.get(oldProject);
        SwingUtilities.invokeAndWait(oldProject::close);
        try (var reopened = new ProjectKnowledgeBase(store)) {
            assertTrue(reopened.removeDocument(pdf));
            assertThrows(CancellationException.class, () -> detached.ingest(pdf));
        }
        try (var finalProject = new ProjectKnowledgeBase(store)) {
            assertTrue(finalProject.retrieve("JDBC", 5).isEmpty());
        }
    }

    private static String grounded(RetrievalResult result) {
        return new Gson().toJson(Map.of("answer", "Course answer [1]", "basedOnCourseMaterial", true,
                "citations", result.getChunks().stream().map(chunk -> Map.of(
                        "document", chunk.getDocumentName(), "pageRange", chunk.getPageRange())).toList()));
    }

    @Test void importTracksStateAndRealPhysicalPdfPage() throws Exception {
        try (var kb = new ProjectKnowledgeBase()) {
            assertEquals(ProjectKnowledgeBase.State.UNINITIALIZED, kb.snapshot().state());
            var report = kb.ingest(TestDocuments.write(directory.resolve("course.pdf"), "", "JDBC transactions"));
            assertEquals(2, report.pageCount());
            assertEquals(ProjectKnowledgeBase.State.READY, kb.snapshot().state());
            assertEquals(List.of(report), kb.snapshot().documents());
            assertEquals("p.2", kb.retrieve("JDBC", 5).getChunks().getFirst().getPageRange());
            assertFalse(kb.snapshot().importing());
        }
    }

    @Test void emptyDocumentsAreValidButHaveNoSearchableContent() throws Exception {
        try (var kb = new ProjectKnowledgeBase()) {
            assertFalse(kb.ingest(TestDocuments.write(directory.resolve("blank.pdf"), "")).hasExtractableText());
            assertEquals(ProjectKnowledgeBase.State.EMPTY, kb.snapshot().state());
            assertTrue(kb.retrieve("JDBC", 5).isEmpty());
        }
    }

    @Test void failedReimportPreservesPreviousIndexAndReportsFailure() throws Exception {
        try (var kb = new ProjectKnowledgeBase()) {
            Path path = TestDocuments.write(directory.resolve("course.pdf"), "servlet lifecycle");
            var original = kb.ingest(path);
            Files.writeString(path, "not a PDF");
            assertThrows(DocumentIngestionException.class, () -> kb.ingest(path));
            assertEquals(List.of(original), kb.snapshot().documents());
            assertEquals(DocumentIngestionException.Reason.MALFORMED_DOCUMENT, kb.snapshot().lastImportFailure());
            assertFalse(kb.snapshot().importing());
            assertFalse(kb.retrieve("servlet", 5).isEmpty());
        }
    }

    @Test void removingDeletedFileClearsItsResults() throws Exception {
        try (var kb = new ProjectKnowledgeBase()) {
            var report = kb.ingest(TestDocuments.write(directory.resolve("course.ppt"), "JDBC connections"));
            Files.delete(report.path());
            assertTrue(kb.removeDocument(report.path()));
            assertTrue(kb.snapshot().documents().isEmpty());
            assertEquals(ProjectKnowledgeBase.State.EMPTY, kb.snapshot().state());
            assertTrue(kb.retrieve("JDBC", 5).isEmpty());
        }
    }

    @Test void closedProjectRejectsOperationsAndDropsDocumentReferences() throws Exception {
        var kb = new ProjectKnowledgeBase();
        kb.ingest(TestDocuments.write(directory.resolve("course.pdf"), "JDBC"));
        kb.close();
        kb.close();
        assertEquals(ProjectKnowledgeBase.State.CLOSED, kb.snapshot().state());
        assertTrue(kb.snapshot().documents().isEmpty());
        assertThrows(CancellationException.class, () -> kb.retrieve("JDBC", 5));
        assertThrows(CancellationException.class, () -> kb.ingest(directory.resolve("course.pdf")));
        assertThrows(CancellationException.class, () -> kb.removeDocument(directory.resolve("course.pdf")));
    }

    @Test void expensiveOperationsRejectEdt() throws Exception {
        try (var kb = new ProjectKnowledgeBase()) {
            SwingUtilities.invokeAndWait(() -> {
                assertThrows(IllegalStateException.class, () -> kb.ingest(directory.resolve("x.pdf")));
                assertThrows(IllegalStateException.class, () -> kb.retrieve("JDBC", 5));
                assertThrows(IllegalStateException.class, () -> kb.removeDocument(directory.resolve("x.pdf")));
            });
        }
    }

    @Test void eachQuestionRetrievesFreshMaterialAndPreservesExactCode() throws Exception {
        try (var kb = new ProjectKnowledgeBase()) {
            kb.ingest(TestDocuments.write(directory.resolve("course.pdf"), "JDBC transactions", "servlet lifecycle"));
            AtomicInteger retrievals = new AtomicInteger();
            AtomicInteger calls = new AtomicInteger();
            String code = "\tclass Demo {\r\n  int value;\n}\n";
            LlmChatService service = new LlmChatService(new AnswerGenerationService(config, (cfg, prompt) -> {
                String question = calls.getAndIncrement() == 0 ? "JDBC" : "servlet";
                assertTrue(prompt.getUser().contains("### Question\n" + question));
                assertTrue(prompt.getUser().contains(code));
                assertTrue(prompt.getUser().contains(question.equals("JDBC") ? "p.1" : "p.2"));
                assertFalse(prompt.getUser().contains(question.equals("JDBC") ? "servlet lifecycle" : "JDBC transactions"));
                return grounded(kb.retrieve(question, 5));
            }), question -> { retrievals.incrementAndGet(); return kb.retrieve(question, 5); });
            assertEquals(1, service.reply(new ChatRequest("JDBC", code)).sources().getFirst().number());
            assertEquals(2, service.reply(new ChatRequest("servlet", code)).sources().getFirst().number());
            assertEquals(2, retrievals.get());
        }
    }

    @Test void actualPptAndPptxSlidesMapWithoutGuessing() throws Exception {
        for (String extension : List.of("ppt", "pptx")) {
            try (var kb = new ProjectKnowledgeBase()) {
                kb.ingest(TestDocuments.write(directory.resolve("course." + extension), "", "JDBC"));
                var service = new LlmChatService(new AnswerGenerationService(config,
                        (cfg, prompt) -> grounded(kb.retrieve("JDBC", 5))), q -> kb.retrieve(q, 5));
                var source = service.reply("JDBC").sources().getFirst();
                assertEquals(CourseSource.Type.PPT_SLIDE, source.type());
                assertEquals(2, source.number());
                assertFalse(source.testData());
            }
        }
    }

    @Test void sameBasenameDocumentsRemainDistinctInRealCitations() throws Exception {
        try (var kb = new ProjectKnowledgeBase()) {
            Path first = Files.createDirectories(directory.resolve("one"));
            Path second = Files.createDirectories(directory.resolve("two"));
            kb.ingest(TestDocuments.write(first.resolve("course.pdf"), "JDBC transactions"));
            kb.ingest(TestDocuments.write(second.resolve("course.pdf"), "JDBC connections"));
            var service = new LlmChatService(new AnswerGenerationService(config,
                    (cfg, prompt) -> grounded(kb.retrieve("JDBC", 5))), q -> kb.retrieve(q, 5));
            var sources = service.reply("JDBC").sources();
            assertEquals(2, sources.size());
            assertNotEquals(sources.get(0).documentName(), sources.get(1).documentName());
        }
    }

    @Test void unmatchedQuestionUsesDisclaimerAndCannotInventSources() throws Exception {
        try (var kb = new ProjectKnowledgeBase()) {
            kb.ingest(TestDocuments.write(directory.resolve("course.pdf"), "JDBC"));
            var service = new LlmChatService(new AnswerGenerationService(config, (cfg, prompt) -> {
                assertTrue(prompt.getUser().contains("No course material was retrieved"));
                return "{\"answer\":\"Answer\",\"basedOnCourseMaterial\":true,\"citations\":["
                        + "{\"document\":\"invented.pdf\",\"pageRange\":\"p.1\"}]}";
            }), q -> kb.retrieve(q, 5));
            var reply = service.reply("unmatchedword");
            assertTrue(reply.sources().isEmpty());
            assertTrue(reply.text().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
            assertEquals(ChatReply.Status.NORMAL, reply.status());
        }
    }

    @Test void closedKnowledgeBaseCancelsBeforeCallingModel() {
        var kb = new ProjectKnowledgeBase();
        kb.close();
        var service = new LlmChatService(new AnswerGenerationService(config,
                (cfg, prompt) -> { fail("Must not call model"); return ""; }), q -> kb.retrieve(q, 5));
        assertThrows(CancellationException.class, () -> service.reply("JDBC"));
    }
}
