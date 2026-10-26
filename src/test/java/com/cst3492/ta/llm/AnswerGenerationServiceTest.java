package com.cst3492.ta.llm;

import com.cst3492.ta.core.AssistantAnswer;
import com.cst3492.ta.core.AssistantQuery;
import com.cst3492.ta.core.RetrievedChunk;
import com.cst3492.ta.core.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerGenerationServiceTest {

    @Test
    void sourcesOutsideTheRenderedPromptCannotBeCited() {
        var template = new PromptTemplate(PromptTemplate.defaultSystemPrompt(), 1);
        var service = new AnswerGenerationService(LlmConfig.deepseek("unused-test-key"),
                (config, prompt) -> """
                        {"answer":"Hidden source [2].", "citations":[{"sourceId":2}],"basedOnCourseMaterial":true}
                        """, template, new AnswerParser());
        var retrieval = new RetrievalResult(List.of(new RetrievedChunk("Shown.pdf", "p.1", "shown", 2),
                new RetrievedChunk("Hidden.pdf", "p.2", "hidden", 1)));
        var answer = service.generate("q", retrieval, null);
        assertFalse(answer.isBasedOnCourseMaterial());
        assertTrue(answer.getCitations().isEmpty());
    }

    @Test
    void plainTextFallbackDoesNotLeaveUnverifiedSourceNumbers() {
        var service = new AnswerGenerationService(LlmConfig.deepseek("unused-test-key"),
                (config, prompt) -> "On success call commit; otherwise rollback [1].");
        var retrieval = new RetrievalResult(List.of(new RetrievedChunk("Lecture.pdf", "p.2", "JDBC rollback", 1)));

        var answer = service.generate("Explain the method", retrieval, "connection.rollback();");

        assertFalse(answer.isBasedOnCourseMaterial());
        assertTrue(answer.getCitations().isEmpty());
        assertTrue(answer.getContent().startsWith("On success call commit; otherwise rollback."));
        assertFalse(answer.getContent().contains("[1]"));
        assertTrue(answer.getContent().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
    }

    @Test
    void generalKnowledgeJsonCannotRetainCourseReferencesEvenIfMetadataMatches() {
        var service = new AnswerGenerationService(LlmConfig.deepseek("unused-test-key"),
                (config, prompt) -> """
                        {"answer":"Use rollback [1].", "basedOnCourseMaterial":false,
                         "citations":[{"document":"Lecture.pdf","pageRange":"p.2"}]}
                        """);
        var retrieval = new RetrievalResult(List.of(new RetrievedChunk("Lecture.pdf", "p.2", "JDBC rollback", 1)));

        var answer = service.generate("Explain the method", retrieval, null);

        assertFalse(answer.isBasedOnCourseMaterial());
        assertTrue(answer.getCitations().isEmpty());
        assertTrue(answer.getContent().startsWith("Use rollback."));
        assertFalse(answer.getContent().contains("[1]"));
    }

    @Test
    void removingUnverifiedReferencesPreservesCodeIndicesAndMarkdownLinks() {
        String code = "`values[1]`\n```java\nint value = values[1];\n```";
        String body = "Use rollback [1].\n" + code + "\nArray: values[1]. Link: [1](https://example.com).";
        var service = new AnswerGenerationService(LlmConfig.deepseek("unused-test-key"), (config, prompt) -> body);

        var answer = service.generate("Explain", RetrievalResult.empty(), null);

        assertTrue(answer.getContent().startsWith("Use rollback."));
        assertTrue(answer.getContent().contains(code));
        assertTrue(answer.getContent().contains("Array: values[1]."));
        assertTrue(answer.getContent().contains("[1](https://example.com)"));
    }

    @Test
    void generatesGroundedAnswerWithCitations() {
        LlmClient fakeClient = (config, prompt) ->
                "{\"answer\":\"Use [1].\","
                        + "\"citations\":[{\"document\":\"L1.pdf\",\"pageRange\":\"p.3\"}],"
                        + "\"basedOnCourseMaterial\":true}";
        AnswerGenerationService service = new AnswerGenerationService(
                LlmConfig.deepseek("test-key"), fakeClient);

        RetrievedChunk chunk = new RetrievedChunk("L1.pdf", "p.3", "content", 0.9);
        RetrievalResult retrieval = new RetrievalResult(List.of(chunk));
        AssistantAnswer answer = service.generate(
                new AssistantQuery("q", null, retrieval));

        assertTrue(answer.isBasedOnCourseMaterial());
        assertEquals("Use [1].", answer.getContent());
        assertEquals(1, answer.getCitations().size());
    }

    @Test
    void appendsDisclaimerWhenAnswerIsGeneralKnowledge() {
        LlmClient fakeClient = (config, prompt) ->
                "{\"answer\":\"42\",\"citations\":[],\"basedOnCourseMaterial\":false}";
        AnswerGenerationService service = new AnswerGenerationService(
                LlmConfig.deepseek("test-key"), fakeClient);

        AssistantAnswer answer = service.generate("q", RetrievalResult.empty(), null);

        assertFalse(answer.isBasedOnCourseMaterial());
        assertTrue(answer.getContent().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
    }

    @Test
    void passesRenderedPromptToClient() {
        AtomicReference<String> captured = new AtomicReference<>();
        LlmClient fakeClient = (config, prompt) -> {
            captured.set(prompt.getUser());
            return "{\"answer\":\"ok\",\"citations\":[],\"basedOnCourseMaterial\":false}";
        };
        AnswerGenerationService service = new AnswerGenerationService(
                LlmConfig.deepseek("k"), fakeClient);

        service.generate(new AssistantQuery(
                "How does JDBC work?", "Code", RetrievalResult.empty()));

        assertTrue(captured.get().contains("How does JDBC work?"));
        assertTrue(captured.get().contains("Code"));
        assertTrue(captured.get().contains("No course material was retrieved"));
    }

    @Test
    void plainTextWithRelevantMaterialStillGetsDisclaimer() {
        LlmClient fakeClient = (config, prompt) -> "JDBC is a Java API for database access.";
        AnswerGenerationService service = new AnswerGenerationService(
                LlmConfig.deepseek("test-key"), fakeClient);

        RetrievedChunk chunk = new RetrievedChunk("L3.pdf", "p.12", "content", 0.9);
        AssistantAnswer answer = service.generate(
                new AssistantQuery("What is JDBC?", null, new RetrievalResult(List.of(chunk))));

        assertFalse(answer.isBasedOnCourseMaterial());
        assertTrue(answer.getCitations().isEmpty());
        assertTrue(answer.getContent().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
    }

    @Test
    void fabricatedCitationStillGetsDisclaimer() {
        LlmClient fakeClient = (config, prompt) ->
                "{\"answer\":\"Use [1].\","
                        + "\"citations\":[{\"document\":\"Fake.pdf\",\"pageRange\":\"p.1\"}],"
                        + "\"basedOnCourseMaterial\":true}";
        AnswerGenerationService service = new AnswerGenerationService(
                LlmConfig.deepseek("test-key"), fakeClient);

        RetrievedChunk chunk = new RetrievedChunk("L3.pdf", "p.12", "content", 0.9);
        AssistantAnswer answer = service.generate(
                new AssistantQuery("q", null, new RetrievalResult(List.of(chunk))));

        assertFalse(answer.isBasedOnCourseMaterial());
        assertTrue(answer.getCitations().isEmpty());
        assertTrue(answer.getContent().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
    }
}
