package com.cst3492.assistant.chat;

/**
 * Immediate, non-blocking reply contract for this UI-only prototype.
 * A future network service must use a background/asynchronous adapter, never block the EDT.
 */
@FunctionalInterface
public interface ChatService {
    ChatReply reply(ChatRequest request);

    /** Convenience for callers with no editor context. */
    default ChatReply reply(String question) {
        return reply(new ChatRequest(question, ""));
    }
}
