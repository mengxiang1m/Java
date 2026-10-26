package com.cst3492.integration;

import com.cst3492.ta.rag.KnowledgeBaseService;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A small, labelled fixture evaluation, not an estimate of real-course accuracy. */
class RetrievalAcceptanceTest {
    @TempDir Path directory;
    private record Question(String text, int page) { }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "pptx", "ppt"})
    void measuresKnownAnswerRankingAndDocumentsTheCrossLanguageLimit(String format) throws Exception {
        Path document = CourseFixtures.write(directory.resolve("Acceptance." + format), "",
                "JDBC PreparedStatement uses bind parameters to prevent SQL injection.",
                "Servlet lifecycle invokes init service destroy.",
                "Checked exceptions must be caught or declared with throws.",
                "Dependency injection supplies collaborators through constructors.");
        var knowledge = new KnowledgeBaseService();
        long importStarted = System.nanoTime();
        var ingestion = knowledge.ingest(document);
        double importMs = (System.nanoTime() - importStarted) / 1_000_000.0;
        List<Question> questions = List.of(new Question("PreparedStatement parameters", 2),
                new Question("SQL injection", 2), new Question("Servlet lifecycle", 3),
                new Question("destroy service", 3), new Question("checked exceptions throws", 4),
                new Question("caught declared", 4), new Question("dependency injection constructors", 5),
                new Question("collaborators constructors", 5));
        var rows = new ArrayList<Map<String, Object>>();
        for (var question : questions) {
            long started = System.nanoTime();
            var result = knowledge.retrieve(question.text(), 5);
            double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
            String expectedLocation = format.equals("pdf") ? "p." + question.page() : "slide " + question.page();
            assertFalse(result.isEmpty(), question.text());
            var first = result.getChunks().getFirst();
            assertEquals(document.toRealPath().toString(), first.getDocumentName());
            assertEquals(expectedLocation, first.getPageRange(), question.text());
            rows.add(Map.of("question", question.text(), "expected", expectedLocation,
                    "actualTop1", first.getPageRange(), "retrievalMs", elapsedMs));
        }
        assertTrue(knowledge.retrieve("quasarpulsar", 5).isEmpty());
        // Translation is deliberately absent: report this limitation without claiming multilingual recall.
        int crossLanguageHits = knowledge.retrieve("依赖注入", 5).getChunks().size();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scope", "Generated five-page fixture; exact/lexical questions, not real-course accuracy");
        report.put("format", format);
        report.put("pages", ingestion.pageCount());
        report.put("chunks", ingestion.chunkCount());
        report.put("importMs", importMs);
        report.put("top1Correct", rows.size());
        report.put("questions", rows);
        report.put("unmatchedQueryHits", 0);
        report.put("crossLanguageProbe", Map.of("question", "依赖注入", "hits", crossLanguageHits));
        Path output = Path.of("build", "reports", "retrieval", format + ".json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
