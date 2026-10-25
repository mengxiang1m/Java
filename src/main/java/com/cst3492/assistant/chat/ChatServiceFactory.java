package com.cst3492.assistant.chat;

import com.cst3492.ta.llm.AnswerGenerationService;
import com.cst3492.ta.llm.LlmConfig;
import com.cst3492.ta.llm.OpenAiCompatibleClient;
import com.cst3492.ta.rag.KnowledgeBaseService;

import java.util.Map;

/** Reads configuration without making a model request or retaining a key in UI labels. */
public final class ChatServiceFactory {
    private ChatServiceFactory() { }

    public record Selection(ChatService service, String description) { }

    public static Selection create(Map<String, String> environment) {
        return create(environment, new KnowledgeBaseService());
    }

    public static Selection create(Map<String, String> environment, KnowledgeBaseService knowledge) {
        String key = environment.getOrDefault("DEEPSEEK_API_KEY", "").strip();
        if ("mock".equalsIgnoreCase(environment.get("CST3492_CHAT_MODE")) || key.isEmpty()) {
            return new Selection(new MockChatService(), "Mock mode — no AI request will be sent.");
        }
        return new Selection(new LlmChatService(new AnswerGenerationService(
                LlmConfig.deepseek(key), new OpenAiCompatibleClient()), knowledge),
                "DeepSeek configured — course retrieval ready.");
    }
}
