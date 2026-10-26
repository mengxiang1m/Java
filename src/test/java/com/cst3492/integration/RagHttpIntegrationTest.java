package com.cst3492.integration;

import com.cst3492.assistant.chat.ChatRequest;
import com.cst3492.assistant.chat.ChatServiceFactory;
import com.cst3492.assistant.chat.CourseSource;
import com.cst3492.ta.llm.LlmException;
import com.cst3492.ta.llm.PromptTemplate;
import com.cst3492.ta.rag.KnowledgeBaseService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RagHttpIntegrationTest {
    @TempDir Path temp;

    @Test
    void structuredCourseResponseUsesSourceIdsInsteadOfCopyingAUnicodeFilePath() throws Exception {
        Path document = CourseFixtures.write(temp.resolve("真实课程 Exceptions.pdf"), "", "", "", "",
                "Checked exceptions must be caught or declared. Runtime exceptions are unchecked.");
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(document);
        String content = """
                {"answer":"Checked exceptions must be caught or declared [1].",
                 "citations":[{"sourceId":1}],"basedOnCourseMaterial":true}
                """;
        try (var endpoint = endpoint(200, content)) {
            var service = ChatServiceFactory.create(environment(endpoint, "tongji"), knowledge).service();
            var reply = service.reply("What is the difference between checked and unchecked exceptions?");
            assertEquals("Checked exceptions must be caught or declared [1].", reply.text());
            assertSourceWithExcerpt(reply.sources(),
                    new CourseSource(document.toRealPath().toString(), CourseSource.Type.PDF_PAGE, 5, false),
                    "Checked exceptions must be caught or declared. Runtime exceptions are unchecked.");
            assertEquals("json_object", endpoint.request().get().getAsJsonObject("response_format").get("type").getAsString());
            assertFalse(reply.text().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "pptx", "ppt"})
    void realImportHttpPromptAndValidatedCitationWorkTogether(String extension) throws Exception {
        Path document = CourseFixtures.write(temp.resolve("Lecture." + extension), "", "JDBC transaction rollback");
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(document);
        String location = extension.equals("pdf") ? "p.2" : "slide 2";
        String source = document.toRealPath().toString();
        String content = new Gson().toJson(Map.of("answer", "Use rollback [1]", "basedOnCourseMaterial", true,
                "citations", List.of(Map.of("document", source, "pageRange", location))));
        try (var endpoint = endpoint(200, content)) {
            var service = ChatServiceFactory.create(environment(endpoint, "tongji"), knowledge).service();
            String code = "\tconnection.rollback();\r\n";
            var reply = service.reply(new ChatRequest("Explain JDBC", code));

            assertEquals("Use rollback [1]", reply.text());
            assertSourceWithExcerpt(reply.sources(), new CourseSource(source, extension.equals("pdf")
                    ? CourseSource.Type.PDF_PAGE : CourseSource.Type.PPT_SLIDE, 2, false),
                    "JDBC transaction rollback");
            JsonObject request = endpoint.request().get();
            assertEquals("account-allowed-model", request.get("model").getAsString());
            String user = request.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString();
            assertTrue(user.contains("JDBC transaction rollback"));
            assertTrue(user.contains(source));
            assertTrue(user.contains(location));
            assertTrue(user.contains(code));
            assertEquals("Bearer unused-local-test-key", endpoint.authorization().get());
        }
    }

    @Test
    void noMatchesIgnoreInventedSourcesAndStateGeneralKnowledge() throws Exception {
        String invented = """
                {"answer":"General explanation", "basedOnCourseMaterial":true,
                 "citations":[{"document":"Invented.pdf","pageRange":"p.99"}]}
                """;
        try (var endpoint = endpoint(200, invented)) {
            var service = ChatServiceFactory.create(environment(endpoint, "deepseek"), new KnowledgeBaseService()).service();
            var reply = service.reply("Unmatched question");
            assertTrue(reply.sources().isEmpty());
            assertTrue(reply.text().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
            assertTrue(endpoint.request().get().toString().contains("No course material was retrieved"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void httpFailureReachesTheExistingUiErrorBoundary(int status) throws Exception {
        try (var endpoint = endpoint(status, "Unavailable")) {
            var service = ChatServiceFactory.create(environment(endpoint, "tongji"), new KnowledgeBaseService()).service();
            assertThrows(LlmException.class, () -> service.reply("JDBC"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void emptyHttpCompletionIsAnErrorRatherThanAGeneralKnowledgeSuccess(String content) throws Exception {
        try (var endpoint = endpoint(200, content)) {
            var service = ChatServiceFactory.create(environment(endpoint, "tongji"), new KnowledgeBaseService()).service();
            assertThrows(LlmException.class, () -> service.reply("JDBC"));
        }
    }

    @Test
    void incompleteTongjiConfigurationDoesNotFallBackToAnUnrelatedDeepSeekKey() {
        var selection = ChatServiceFactory.create(Map.of("CST3492_LLM_PROVIDER", "tongji",
                "CST3492_LLM_API_KEY", "unused-local-test-key"), new KnowledgeBaseService());
        assertThrows(LlmException.class, () -> selection.service().reply("JDBC"));
        assertFalse(selection.description().contains("unused-local-test-key"));
        var missing = ChatServiceFactory.create(Map.of("CST3492_LLM_PROVIDER", "tongji",
                "DEEPSEEK_API_KEY", "unused-deepseek-test-key"), new KnowledgeBaseService());
        assertInstanceOf(com.cst3492.assistant.chat.MockChatService.class, missing.service());
    }

    /** Metadata and verbatim evidence are stable contracts; the exact BM25 value is not. */
    private static void assertSourceWithExcerpt(List<CourseSource> sources, CourseSource expected,
                                                String expectedText) {
        assertEquals(1, sources.size());
        CourseSource actual = sources.getFirst();
        assertAll("validated source and retrieved evidence",
                () -> assertEquals(expected.documentName(), actual.documentName()),
                () -> assertEquals(expected.type(), actual.type()),
                () -> assertEquals(expected.number(), actual.number()),
                () -> assertEquals(expected.testData(), actual.testData()));
        assertEquals(1, actual.excerpts().size());
        CourseSource.Excerpt excerpt = actual.excerpts().getFirst();
        assertEquals(expectedText, excerpt.text());
        assertNotNull(excerpt.score(), "Real retrieval must preserve its score");
        assertTrue(Double.isFinite(excerpt.score()), "Retrieval score must be finite");
        assertTrue(excerpt.score() > 0, "A's matched BM25 results have positive scores");
    }

    private Map<String, String> environment(Endpoint endpoint, String provider) {
        return Map.of("CST3492_LLM_PROVIDER", provider, "CST3492_LLM_API_KEY", "unused-local-test-key",
                "CST3492_LLM_BASE_URL", "http://127.0.0.1:" + endpoint.server().getAddress().getPort() + "/v1",
                "CST3492_LLM_MODEL", "account-allowed-model");
    }

    @Test
    void legacyDeepSeekKeyStillAuthenticatesTheConfiguredEndpoint() throws Exception {
        try (var endpoint = endpoint(200, "A general answer")) {
            var environment = new java.util.HashMap<>(environment(endpoint, "deepseek"));
            environment.remove("CST3492_LLM_API_KEY");
            environment.put("DEEPSEEK_API_KEY", "unused-legacy-test-key");
            ChatServiceFactory.create(environment, new KnowledgeBaseService()).service().reply("Question");
            assertEquals("Bearer unused-legacy-test-key", endpoint.authorization().get());
        }
    }

    @Test
    void explicitMockBypassesInvalidProviderWhileNormalModeReportsIt() {
        var environment = Map.of("CST3492_LLM_PROVIDER", "unknown", "CST3492_LLM_API_KEY", "unused-test-key");
        var selection = ChatServiceFactory.create(environment, new KnowledgeBaseService());
        assertThrows(LlmException.class, () -> selection.service().reply("Question"));
        var mock = new java.util.HashMap<>(environment);
        mock.put("CST3492_CHAT_MODE", "mock");
        assertInstanceOf(com.cst3492.assistant.chat.MockChatService.class,
                ChatServiceFactory.create(mock, new KnowledgeBaseService()).service());
    }

    private Endpoint endpoint(int status, String content) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var request = new AtomicReference<JsonObject>();
        var authorization = new AtomicReference<String>();
        server.createContext("/v1/chat/completions", exchange -> {
            request.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = new Gson().toJson(Map.of("choices", List.of(Map.of("message", Map.of("content", content)))))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            exchange.close();
        });
        server.start();
        return new Endpoint(server, request, authorization);
    }

    private record Endpoint(HttpServer server, AtomicReference<JsonObject> request,
                            AtomicReference<String> authorization) implements AutoCloseable {
        @Override public void close() { server.stop(0); }
    }
}
