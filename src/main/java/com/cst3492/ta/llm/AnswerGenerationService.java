package com.cst3492.ta.llm;

import com.cst3492.ta.core.AssistantAnswer;
import com.cst3492.ta.core.AssistantQuery;
import com.cst3492.ta.core.RetrievalResult;

import java.util.Objects;

/**
 * High-level entry point of the LLM module.
 *
 * <p>It turns a query into a prompt, calls the LLM through a {@link LlmClient},
 * parses the structured response, and enforces the source-citation /
 * general-knowledge contract required by the assignment: when the answer is not
 * grounded in course material, the required disclaimer is appended.</p>
 */
public final class AnswerGenerationService {

    private final LlmConfig config;
    private final LlmClient client;
    private final PromptTemplate template;
    private final AnswerParser parser;

    public AnswerGenerationService(LlmConfig config, LlmClient client) {
        this(config, client, new PromptTemplate(), new AnswerParser());
    }

    public AnswerGenerationService(LlmConfig config, LlmClient client,
                                   PromptTemplate template, AnswerParser parser) {
        this.config = Objects.requireNonNull(config, "config");
        this.client = Objects.requireNonNull(client, "client");
        this.template = Objects.requireNonNull(template, "template");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    /** Generates an answer for a fully-formed query. */
    public AssistantAnswer generate(AssistantQuery query) {
        Objects.requireNonNull(query, "query");
        Prompt prompt = template.render(query);
        String raw = client.complete(config, prompt);
        AssistantAnswer answer = parser.parse(raw, query.getRetrieval());
        return enforceDisclaimer(answer);
    }

    /** Convenience overload for callers that do not build a query object. */
    public AssistantAnswer generate(String question, RetrievalResult retrieval, String selectedCode) {
        return generate(new AssistantQuery(question, selectedCode, retrieval));
    }

    private AssistantAnswer enforceDisclaimer(AssistantAnswer answer) {
        if (answer.isBasedOnCourseMaterial()) {
            return answer;
        }
        String content = answer.getContent();
        if (content.contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER)) {
            return answer;
        }
        return new AssistantAnswer(
                content + "\n\n> " + PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER,
                answer.getCitations(),
                false);
    }
}
