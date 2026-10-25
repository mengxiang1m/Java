package com.cst3492.assistant.chat;

import com.cst3492.ta.llm.AnswerGenerationService;
import com.cst3492.ta.llm.LlmConfig;
import com.cst3492.ta.llm.OpenAiCompatibleClient;
import com.cst3492.ta.llm.LlmException;
import com.cst3492.ta.llm.LlmProvider;
import com.cst3492.ta.rag.KnowledgeBaseService;

import java.util.Map;
import java.util.Locale;

/** Reads configuration without making a model request or retaining a key in UI labels. */
public final class ChatServiceFactory {
    private ChatServiceFactory() { }

    public record Selection(ChatService service, String description) { }

    public static Selection create(Map<String, String> environment) {
        return create(environment, new KnowledgeBaseService());
    }

    public static Selection create(Map<String, String> environment, KnowledgeBaseService knowledge) {
        if ("mock".equalsIgnoreCase(environment.get("CST3492_CHAT_MODE"))) {
            return new Selection(new MockChatService(), "Mock mode — no AI request will be sent.");
        }
        String providerName = environment.getOrDefault("CST3492_LLM_PROVIDER", "deepseek").strip();
        LlmProvider provider;
        try {
            provider = LlmProvider.valueOf(providerName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return unavailable("Choose DeepSeek, Tongji or a custom compatible provider in the API setup.");
        }
        String key = environment.getOrDefault("CST3492_LLM_API_KEY", "").strip();
        if (key.isEmpty() && provider == LlmProvider.DEEPSEEK) {
            key = environment.getOrDefault("DEEPSEEK_API_KEY", "").strip();
        }
        if (key.isEmpty()) {
            return new Selection(new MockChatService(), "Mock mode — no AI request will be sent.");
        }
        String baseUrl = environment.getOrDefault("CST3492_LLM_BASE_URL", "").strip();
        String model = environment.getOrDefault("CST3492_LLM_MODEL", "").strip();
        if (provider != LlmProvider.DEEPSEEK && (baseUrl.isEmpty() || model.isEmpty())) {
            return unavailable("Finish the API endpoint and model setup for " + provider + ".");
        }
        LlmConfig.Builder config = LlmConfig.builder(provider, key);
        if (!baseUrl.isEmpty()) config.baseUrl(baseUrl);
        if (!model.isEmpty()) config.model(model);
        return new Selection(new LlmChatService(new AnswerGenerationService(
                config.build(), new OpenAiCompatibleClient()), knowledge),
                provider + " configured — course retrieval ready.");
    }

    private static Selection unavailable(String message) {
        return new Selection(request -> { throw new LlmException(message); }, message);
    }
}
