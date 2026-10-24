package com.cst3492.ta.llm;

import com.cst3492.ta.core.AssistantQuery;
import com.cst3492.ta.core.RetrievedChunk;
import com.cst3492.ta.core.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateTest {

    private final PromptTemplate template = new PromptTemplate();

    @Test
    void rendersQuestionAndSelectedCode() {
        RetrievalResult retrieval = RetrievalResult.empty();
        AssistantQuery query = new AssistantQuery(
                "How do I inject a bean?", "public class A { }", retrieval);

        Prompt prompt = template.render(query);

        assertTrue(prompt.getUser().contains("How do I inject a bean?"));
        assertTrue(prompt.getUser().contains("public class A { }"));
        assertTrue(prompt.getUser().contains("### Selected code"));
    }

    @Test
    void rendersRetrievedChunksWithSourceIds() {
        RetrievedChunk chunk = new RetrievedChunk("Lecture3_JDBC.pdf", "p.12", "JDBC is ...", 0.9);
        RetrievalResult retrieval = new RetrievalResult(List.of(chunk));
        AssistantQuery query = new AssistantQuery("What is JDBC?", null, retrieval);

        Prompt prompt = template.render(query);

        assertTrue(prompt.getUser().contains("[1] (Lecture3_JDBC.pdf, p.12)"));
        assertTrue(prompt.getUser().contains("JDBC is ..."));
    }

    @Test
    void marksEmptyRetrieval() {
        AssistantQuery query = new AssistantQuery("q?", null, RetrievalResult.empty());

        Prompt prompt = template.render(query);

        assertTrue(prompt.getUser().contains("No course material was retrieved"));
    }

    @Test
    void systemPromptEnforcesJsonAndCitations() {
        String system = PromptTemplate.defaultSystemPrompt();

        assertTrue(system.contains("CST3492"));
        assertTrue(system.contains("basedOnCourseMaterial"));
        assertTrue(system.contains("citations"));
        assertTrue(system.contains("document"));
    }

    @Test
    void capsNumberOfChunks() {
        PromptTemplate capped = new PromptTemplate(PromptTemplate.defaultSystemPrompt(), 2);
        RetrievedChunk chunk = new RetrievedChunk("d", "p.1", "x", 1.0);
        RetrievalResult retrieval = new RetrievalResult(List.of(chunk, chunk, chunk));
        Prompt prompt = capped.render(new AssistantQuery("q", null, retrieval));

        assertTrue(prompt.getUser().contains("[1] ("));
        assertTrue(prompt.getUser().contains("[2] ("));
        assertFalse(prompt.getUser().contains("[3] ("));
    }
}
