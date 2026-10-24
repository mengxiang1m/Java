package com.cst3492.ta.llm;

import com.cst3492.ta.core.AssistantAnswer;
import com.cst3492.ta.core.AssistantQuery;
import com.cst3492.ta.core.RetrievalResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual end-to-end check against the real DeepSeek API.
 *
 * <p>Skipped by default. Run it with a key set, e.g.
 * {@code DEEPSEEK_API_KEY=sk-... ./gradlew test --tests '*LlmIntegrationTest'}.</p>
 */
class LlmIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    void realDeepSeekCallReturnsGeneralKnowledgeAnswer() {
        String key = System.getenv("DEEPSEEK_API_KEY");
        AnswerGenerationService service = new AnswerGenerationService(
                LlmConfig.deepseek(key), new OpenAiCompatibleClient());

        AssistantAnswer answer = service.generate(new AssistantQuery(
                "What is dependency injection?", null, RetrievalResult.empty()));

        assertFalse(answer.isBasedOnCourseMaterial());
        assertFalse(answer.getContent().isBlank());
        assertTrue(answer.getContent().contains(PromptTemplate.GENERAL_KNOWLEDGE_DISCLAIMER));
    }
}
