package com.cst3492.assistant.chat;

/**
 * Synchronous reply contract. May perform network I/O; callers must execute off the EDT.
 */
@FunctionalInterface
public interface ChatService {
    ChatReply reply(ChatRequest request);

    /** Convenience for callers with no editor context. */
    default ChatReply reply(String question) {
        return reply(new ChatRequest(question, ""));
    }
}
