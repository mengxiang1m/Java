package com.cst3492.integration;

import com.cst3492.assistant.chat.*;
import com.cst3492.ta.core.RetrievalResult;
import com.cst3492.ta.rag.KnowledgeBaseService;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Opt-in acceptance runner over user-supplied course files; never serializes configuration or keys. */
public final class RealCourseAcceptance {
    record Question(String id, String question, String document, List<String> pages, String code, boolean live) { }
    record Imported(String document, int pages, int chunks, double milliseconds) { }
    record Hit(String document, String location, Double score, String excerpt) { }
    record RetrievalCheck(String id, String question, String expectedDocument, List<String> expectedPages,
                          boolean top1Correct, boolean top5Correct, double milliseconds, List<Hit> hits) { }
    record LiveCheck(String id, String status, double milliseconds, String answer, List<Hit> sources, String error) { }
    record Report(String scope, List<Imported> documents, List<RetrievalCheck> retrieval,
                  String liveStatus, List<LiveCheck> liveChecks) { }

    static final List<Question> QUESTIONS = List.of(
            q("primitives", "What are the eight primitive data types in Java?", "Lec-1-Java-Fundamentals.pdf", 10),
            q("autoboxing", "When does autoboxing and unboxing happen?", "Lec-1-Java-Fundamentals.pdf", 26),
            q("inheritance", "Does a Java subclass inherit constructors?", "Lec-2-OOP-with-Java.pdf", 19),
            q("exceptions", "What is the difference between checked and unchecked exceptions?", "Lec-3.1-Exceptions.pdf", 5),
            q("throws", "What is the purpose of the throws clause?", "Lec-3.1-Exceptions.pdf", 7),
            q("generics", "What benefits do generics provide at compile time?", "Lec-3.2-Generics-and-Collections.pdf", 4),
            q("invariance", "Are Java generics invariant and arrays covariant?", "Lec-3.2-Generics-and-Collections.pdf", 8),
            q("wildcards", "Explain upper bounded and lower bounded wildcards.", "Lec-3.2-Generics-and-Collections.pdf", 10),
            q("erasure", "How does type erasure replace type parameters?", "Lec-3.2-Generics-and-Collections.pdf", 11),
            q("annotations", "What are annotations used for?", "Lec-3.3-ModernSyntax-Annotations-and-Reflection.pdf", 5),
            q("reflection", "How do getDeclaredMethods and getMethods differ?", "Lec-3.3-ModernSyntax-Annotations-and-Reflection.pdf", 12),
            q("junit-lifecycle", "What do @BeforeEach and @AfterEach do?", "Lec-3.4-JUnit.pdf", 4),
            q("junit-exceptions", "How does assertThrows handle an unexpected exception type?", "Lec-3.4-JUnit.pdf", 5),
            q("junit-parameters", "How does @ParameterizedTest with @ValueSource run multiple cases?", "Lec-3.4-JUnit.pdf", 6),
            q("ai-evidence", "What evidence is needed before accepting AI generated code?", "Lec-4-AI-Assisted-Java-Development.pdf", 3),
            q("rag", "What are the four steps of RAG?", "Lec-5-Building-AI-Powered-Java-Applications.pdf", 23),
            q("bm25", "Why do rare words count more in BM25 keyword search?", "Lec-5-Building-AI-Powered-Java-Applications.pdf", 24),
            q("hybrid", "Why combine keyword search and vector search?", "Lec-5-Building-AI-Powered-Java-Applications.pdf", 26),
            q("rrf", "How does reciprocal rank fusion merge rankings?", "Lec-5-Building-AI-Powered-Java-Applications.pdf", 27),
            q("citation-check", "Why reject a citation to a passage never supplied to the model?", "Lec-5-Building-AI-Powered-Java-Applications.pdf", 28),
            new Question("chinese", "什么是受检异常和非受检异常？", "Lec-3.1-Exceptions.pdf", List.of("p.5"), "", false),
            new Question("code-context", "Explain this code and whether the checked exception must be caught or declared.",
                    "Lec-3.1-Exceptions.pdf", List.of("p.4", "p.5", "p.7"),
                    "void read() throws java.io.IOException {\n    new java.io.FileReader(\"missing.txt\").close();\n}\n", true),
            new Question("no-match", "Explain quasarpulsarxyz.", "", List.of(), "", true));

    private static Question q(String id, String text, String document, int page) {
        return new Question(id, text, document, List.of("p." + page), "",
                Set.of("exceptions", "junit-lifecycle", "rag").contains(id));
    }

    static List<Path> courseFiles(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("Lec-") && name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
            }).sorted().toList();
        }
    }

    static boolean matches(Question question, String document, String location) {
        return Path.of(document).getFileName().toString().equals(question.document()) && question.pages().contains(location);
    }

    private static Hit hit(String document, String location, Double score, String text) {
        return new Hit(Path.of(document).getFileName().toString(), location, score,
                text.substring(0, Math.min(500, text.length())));
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        boolean live = Boolean.parseBoolean(args[1]);
        Path output = Path.of(args[2]);
        var knowledge = new KnowledgeBaseService();
        var imported = new ArrayList<Imported>();
        for (var path : courseFiles(directory)) {
            long started = System.nanoTime();
            var report = knowledge.ingest(path);
            imported.add(new Imported(path.getFileName().toString(), report.pageCount(), report.chunkCount(), elapsed(started)));
        }
        if (imported.isEmpty()) throw new IllegalArgumentException("No Lec-*.pdf files found in the course directory");
        var checks = new ArrayList<RetrievalCheck>();
        for (var question : QUESTIONS) {
            long started = System.nanoTime();
            RetrievalResult retrieval = knowledge.retrieve(question.question(), 5);
            double duration = elapsed(started);
            boolean top1 = !retrieval.isEmpty() && matches(question, retrieval.getChunks().getFirst().getDocumentName(),
                    retrieval.getChunks().getFirst().getPageRange());
            boolean top5 = retrieval.getChunks().stream().anyMatch(chunk -> matches(question, chunk.getDocumentName(), chunk.getPageRange()));
            checks.add(new RetrievalCheck(question.id(), question.question(), question.document(), question.pages(), top1, top5,
                    duration, retrieval.getChunks().stream().map(chunk -> hit(chunk.getDocumentName(), chunk.getPageRange(),
                    chunk.getScore(), chunk.getContent())).toList()));
        }
        var liveChecks = new ArrayList<LiveCheck>();
        String liveStatus = live ? "COMPLETE" : "NOT_REQUESTED";
        if (live) {
            var environment = System.getenv();
            ChatService service = ChatServiceFactory.create(environment, knowledge).service();
            if (service instanceof MockChatService) {
                liveStatus = "CONFIGURATION_REQUIRED";
            } else {
                for (var question : QUESTIONS.stream().filter(Question::live).toList()) {
                    long started = System.nanoTime();
                    try {
                        ChatReply reply = service.reply(new ChatRequest(question.question(), question.code()));
                        liveChecks.add(new LiveCheck(question.id(), reply.status().name(), elapsed(started), reply.text(),
                                reply.sources().stream().map(source -> hit(source.documentName(),
                                source.type() == CourseSource.Type.PDF_PAGE ? "p." + source.number() : "slide " + source.number(),
                                source.excerpts().isEmpty() ? null : source.excerpts().getFirst().score(),
                                source.excerpts().isEmpty() ? "" : source.excerpts().getFirst().text())).toList(), null));
                    } catch (RuntimeException failure) {
                        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                        for (String key : List.of("CST3492_LLM_API_KEY", "DEEPSEEK_API_KEY")) {
                            String value = environment.get(key);
                            if (value != null && !value.isBlank()) message = message.replace(value, "[REDACTED]");
                        }
                        message = message.replaceAll("sk-[A-Za-z0-9_-]+", "[REDACTED]");
                        liveChecks.add(new LiveCheck(question.id(), "ERROR", elapsed(started), null, List.of(), message));
                        liveStatus = "BLOCKED";
                        break; // Do not repeat a known provider/network failure for every course question.
                    }
                }
            }
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(
                new Report("User-supplied real course PDFs; reference pages labelled by inspection; live answers require human review",
                        imported, checks, liveStatus, liveChecks)));
        System.out.printf("Imported %d course PDFs, %d physical pages, %d chunks.%n", imported.size(),
                imported.stream().mapToInt(Imported::pages).sum(), imported.stream().mapToInt(Imported::chunks).sum());
        var labelled = checks.stream().filter(check -> !check.expectedPages().isEmpty()).toList();
        System.out.printf("Labelled retrieval questions: %d; expected page top1=%d, top5=%d.%n", labelled.size(),
                labelled.stream().filter(RetrievalCheck::top1Correct).count(), labelled.stream().filter(RetrievalCheck::top5Correct).count());
        System.out.println("Live model status: " + liveStatus);
        System.out.println("Report: " + output.toAbsolutePath());
        if (live && !liveStatus.equals("COMPLETE")) System.exit(2);
    }

    private static double elapsed(long started) { return (System.nanoTime() - started) / 1_000_000.0; }
}
