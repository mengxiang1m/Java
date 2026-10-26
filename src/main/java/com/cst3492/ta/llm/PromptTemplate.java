package com.cst3492.ta.llm;

import com.cst3492.ta.core.AssistantQuery;
import com.cst3492.ta.core.RetrievedChunk;
import com.cst3492.ta.core.RetrievalResult;

import java.util.List;

/**
 * Turns an {@link AssistantQuery} (question + editor context + retrieved
 * chunks) into a concrete {@link Prompt} for the LLM.
 *
 * <p>The system prompt fixes the persona, the citation rules and the strict
 * JSON output contract; the user prompt injects the question, the selected code
 * and the retrieved course material.</p>
 */
public final class PromptTemplate {

    /** The exact disclaimer the assignment requires when no material is used. */
    public static final String GENERAL_KNOWLEDGE_DISCLAIMER =
            "Response is based on general knowledge; no specific course material is referenced.";

    private final String systemPrompt;
    private final int maxChunks;

    public PromptTemplate() {
        this(defaultSystemPrompt(), 5);
    }

    public PromptTemplate(String systemPrompt, int maxChunks) {
        this.systemPrompt = systemPrompt;
        this.maxChunks = maxChunks;
    }

    public Prompt render(AssistantQuery query) {
        return new Prompt(systemPrompt, buildUserPrompt(query));
    }

    RetrievalResult visibleRetrieval(RetrievalResult retrieval) {
        int count = Math.min(Math.max(0, maxChunks), retrieval.getChunks().size());
        return new RetrievalResult(List.copyOf(retrieval.getChunks().subList(0, count)));
    }

    private String buildUserPrompt(AssistantQuery query) {
        StringBuilder sb = new StringBuilder();
        sb.append("A student is working in IntelliJ IDEA and asks the following question.\n\n");

        if (query.hasSelectedCode()) {
            sb.append("### Selected code\n```java\n")
                    .append(query.getSelectedCode())
                    .append("\n```\n\n");
        }

        sb.append("### Question\n").append(query.getQuestion()).append("\n\n");

        sb.append("### Retrieved course material\n");
        List<RetrievedChunk> chunks = query.getRetrieval().getChunks();
        if (chunks.isEmpty()) {
            sb.append("(No course material was retrieved for this question.)\n");
        } else {
            int limit = Math.min(maxChunks, chunks.size());
            for (int i = 0; i < limit; i++) {
                RetrievedChunk c = chunks.get(i);
                sb.append("[").append(i + 1).append("] (")
                        .append(c.getDocumentName());
                if (c.getPageRange() != null && !c.getPageRange().isBlank()) {
                    sb.append(", ").append(c.getPageRange());
                }
                sb.append("): ").append(c.getContent()).append("\n\n");
            }
        }

        sb.append("Answer the question now, following the rules in the system message. "
                + "Return only the JSON object.");
        return sb.toString();
    }

    static String defaultSystemPrompt() {
        return "You are a teaching assistant (TA) for the course CST3492 - Java Enterprise Application Development.\n"
                + "You help students with the course and with Java enterprise application development in general.\n"
                + "\n"
                + "You are given zero or more chunks retrieved from the course materials. Each chunk is labeled "
                + "with a source id such as [1], [2], and carries a document name and page range.\n"
                + "\n"
                + "Rules:\n"
                + "1. Use the retrieved chunks to answer when they contain the requested course facts. When you use a chunk, cite its id inline "
                + "in the answer, e.g. \"As stated in [1] ...\".\n"
                + "2. Only cite chunks that are actually relevant to the question. Do not force a citation.\n"
                + "3. If no retrieved chunk is relevant (or none were provided), answer from your general Java "
                + "knowledge and set basedOnCourseMaterial to false.\n"
                + "4. In citations, return only the integer sourceId from a supplied [N] label. The application maps it to the real document and page; do not copy filenames or paths. Never invent source ids.\n"
                + "5. The citations list must contain exactly the sources you cited inline, in the order they first appear. If you quote or paraphrase course material, basedOnCourseMaterial must be true and citations must not be empty.\n"
                + "6. Put a space before inline source markers, and put Java code inside fenced blocks or inline backticks.\n"
                + "\n"
                + "Return only a single JSON object with exactly this shape, and nothing else (no markdown fences, "
                + "no commentary):\n"
                + "{\n"
                + "  \"answer\": \"Your answer. Cite chunks inline as [1], [2].\",\n"
                + "  \"citations\": [ { \"sourceId\": 1 } ],\n"
                + "  \"basedOnCourseMaterial\": true\n"
                + "}\n"
                + "\n"
                + "Use \\n for newlines inside the answer string.";
    }
}
