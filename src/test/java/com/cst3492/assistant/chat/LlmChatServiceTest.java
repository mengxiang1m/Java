package com.cst3492.assistant.chat;

import com.cst3492.ta.core.RetrievalResult;
import com.cst3492.ta.core.RetrievedChunk;
import com.cst3492.ta.llm.AnswerGenerationService;
import com.cst3492.ta.llm.LlmConfig;
import com.cst3492.ta.llm.LlmException;
import com.cst3492.ta.llm.PromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmChatServiceTest {
    @Test
    void retrievalFailureDoesNotCallLlmOrBecomeEmptyResult() {
        var service = new LlmChatService(new AnswerGenerationService(CONFIG,
                (config, prompt) -> { fail("Retrieval failed before generation"); return ""; }),
                question -> { throw new IllegalStateException("private retrieval details"); });
        assertThrows(LlmChatService.RetrievalFailureException.class, () -> service.reply("JDBC"));
    }

    @Test
    void sixthChunkIsNeitherPromptedNorAcceptedAsCitation() {
        var chunks = java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(i ->
                new RetrievedChunk("Lecture.pdf", "p." + i, "chunk-marker-" + i, 1)).toList();
        var service = new LlmChatService(new AnswerGenerationService(CONFIG, (config, prompt) -> {
            assertTrue(prompt.getUser().contains("chunk-marker-5"));
            assertFalse(prompt.getUser().contains("chunk-marker-6"));
            return "{\"answer\":\"Answer\",\"basedOnCourseMaterial\":true,\"citations\":["
                    + "{\"document\":\"Lecture.pdf\",\"pageRange\":\"p.6\"}]}";
        }), question -> new RetrievalResult(chunks));
        var reply = service.reply("JDBC");
        assertTrue(reply.sources().isEmpty());
        assertTrue(reply.text().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
    }
    private static final LlmConfig CONFIG = LlmConfig.deepseek("unused-unit-test-value");

    private LlmChatService service(String response, RetrievalResult retrieval) {
        return new LlmChatService(new AnswerGenerationService(CONFIG, (config, prompt) -> response), retrieval);
    }

    @Test
    void questionAndExactSelectedCodeReachBPrompt() {
        String code = "\tclass Demo {\r\n    int x;\n}\n";
        LlmChatService service = new LlmChatService(new AnswerGenerationService(CONFIG, (config, prompt) -> {
            assertTrue(prompt.getUser().contains("### Question\nExplain this code"));
            assertTrue(prompt.getUser().contains(code));
            assertTrue(prompt.getUser().contains("No course material was retrieved"));
            return "An explanation";
        }));
        ChatReply reply = service.reply(new ChatRequest("Explain this code", code));
        assertTrue(reply.text().startsWith("An explanation"));
        assertTrue(reply.text().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
        assertEquals(ChatReply.Status.NORMAL, reply.status());
        assertTrue(reply.sources().isEmpty());
    }

    @Test
    void validatedPdfAndPptLocationsMapToRealSourcesAndPreserveBody() {
        RetrievalResult retrieval = new RetrievalResult(List.of(
                new RetrievedChunk("Lecture.pdf", "p.3", "PDF content", 1),
                new RetrievedChunk("Lecture.pptx", "slide 5", "PPT content", 1)));
        ChatReply reply = service("""
                {"answer":"Course answer [1] [2]", "basedOnCourseMaterial":true,
                 "citations":[{"document":"Lecture.pdf","pageRange":"p.3"},
                              {"document":"Lecture.pptx","pageRange":"slide 5"}]}
                """, retrieval).reply("Question");
        assertEquals("Course answer [1] [2]", reply.text());
        assertEquals(List.of(new CourseSource("Lecture.pdf", CourseSource.Type.PDF_PAGE, 3, false,
                        List.of(new CourseSource.Excerpt("PDF content", 1.0))),
                new CourseSource("Lecture.pptx", CourseSource.Type.PPT_SLIDE, 5, false,
                        List.of(new CourseSource.Excerpt("PPT content", 1.0)))), reply.sources());
    }

    @Test
    void inventedSourceOrWrongLocationCannotAppearWithoutMatchingRetrieval() {
        String response = """
                {"answer":"Answer", "basedOnCourseMaterial":true,
                 "citations":[{"document":"Lecture.pdf","pageRange":"p.99"}]}
                """;
        for (RetrievalResult retrieval : List.of(RetrievalResult.empty(), new RetrievalResult(List.of(
                new RetrievedChunk("Lecture.pdf", "p.3", "content", 1))))) {
            ChatReply reply = service(response, retrieval).reply("Question");
            assertTrue(reply.sources().isEmpty());
            assertTrue(reply.text().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
        }
    }

    @Test
    void missingRangesUnknownFormatsAndPdfStylePptPagesAreNotGuessed() {
        for (String location : List.of("p.3-5", "", "slides 3-5", "p.3", "slide 99999999999999999999")) {
            RetrievalResult retrieval = new RetrievalResult(List.of(
                    new RetrievedChunk("Lecture.pptx", location, "content", 1)));
            String response = "{\"answer\":\"Answer\",\"basedOnCourseMaterial\":true,\"citations\":["
                    + "{\"document\":\"Lecture.pptx\",\"pageRange\":\"" + location + "\"}]}";
            assertTrue(service(response, retrieval).reply("Question").sources().isEmpty());
        }
    }

    @Test
    void generalKnowledgeNeverDisplaysCitationsEvenIfModelListsAValidOne() {
        ChatReply reply = service("""
                {"answer":"General answer", "basedOnCourseMaterial":false,
                 "citations":[{"document":"Lecture.pdf","pageRange":"p.3"}]}
                """, new RetrievalResult(List.of(new RetrievedChunk("Lecture.pdf", "p.3", "content", 1))))
                .reply("Question");
        assertEquals(ChatReply.Status.NORMAL, reply.status());
        assertTrue(reply.sources().isEmpty());
        assertTrue(reply.text().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
    }

    @Test
    void callFailurePropagatesToBackgroundBoundary() {
        LlmChatService service = new LlmChatService(new AnswerGenerationService(CONFIG, (config, prompt) -> {
            throw new LlmException("Simulated transport failure");
        }));
        assertThrows(LlmException.class, () -> service.reply("Question"));
    }

    @Test
    void factoryFallsBackSafelyAndCanExplicitlyKeepMockMode() {
        assertInstanceOf(MockChatService.class, ChatServiceFactory.create(Map.of()).service());
        assertInstanceOf(MockChatService.class, ChatServiceFactory.create(Map.of("DEEPSEEK_API_KEY", "  ")).service());
        assertInstanceOf(MockChatService.class, ChatServiceFactory.create(Map.of(
                "DEEPSEEK_API_KEY", "unused-unit-test-value", "CST3492_CHAT_MODE", "mock")).service());
        ChatServiceFactory.Selection real = ChatServiceFactory.create(Map.of("DEEPSEEK_API_KEY", "unused-unit-test-value"));
        assertInstanceOf(LlmChatService.class, real.service());
        assertFalse(real.description().contains("unused-unit-test-value"));
    }
}
