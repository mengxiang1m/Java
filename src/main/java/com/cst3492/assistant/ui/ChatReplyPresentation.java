package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.ChatMessage;
import com.cst3492.assistant.chat.ChatReply;
import com.cst3492.assistant.chat.CourseSource;

/** Plain-text rendering kept separate from the service models and Swing widgets. */
public final class ChatReplyPresentation {
    private ChatReplyPresentation() { }

    public static ChatMessage message(ChatReply reply) {
        String heading = switch (reply.status()) {
            case MOCK -> "[MOCK - test data, no AI service]";
            case NORMAL -> "[NORMAL]";
            case NO_SOURCES -> "[NO_SOURCES - no relevant course materials]";
            case ERROR -> "[ERROR - service call failed]";
        };
        StringBuilder text = new StringBuilder(heading).append('\n').append(reply.text());
        if (!reply.sources().isEmpty()) {
            text.append("\n\nSources:");
            for (CourseSource source : reply.sources()) {
                text.append("\n- ");
                if (source.testData()) {
                    text.append("[TEST DATA - not evidence] ");
                }
                text.append(source.documentName()).append(" — ")
                        .append(source.type() == CourseSource.Type.PDF_PAGE ? "Page " : "Slide ")
                        .append(source.number());
            }
        }
        return new ChatMessage(reply.status() == ChatReply.Status.ERROR
                ? ChatMessage.Role.ERROR : ChatMessage.Role.ASSISTANT, text.toString());
    }

    public static String statusText(ChatReply reply) {
        return switch (reply.status()) {
            case MOCK -> "Mock reply received. Enter to send another question.";
            case NORMAL -> "Reply received. Enter to send another question.";
            case NO_SOURCES -> "No relevant course materials. You can ask another question.";
            case ERROR -> "Reply failed. You can send another question.";
        };
    }
}
