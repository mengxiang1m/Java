package com.cst3492.assistant.chat;

import java.util.List;
import java.util.stream.IntStream;

/** Local UI fixtures only: no retrieval, network, artificial delay, or worker thread. */
public final class MockChatService implements ChatService {
    @Override
    public ChatReply reply(ChatRequest request) {
        // Context arrives separately, but this UI fixture does not interpret or execute it.
        return switch (request.question()) {
            case "/sources" -> new ChatReply(
                    "Mock response: source display preview only. These test documents do not support a real answer.",
                    ChatReply.Status.MOCK, List.of(
                    new CourseSource("TEST_ONLY_Chapter_01.pdf", CourseSource.Type.PDF_PAGE, 3, true),
                    new CourseSource("TEST_ONLY_Chapter_02.pptx", CourseSource.Type.PPT_SLIDE, 5, true)));
            case "/many-sources" -> new ChatReply(
                    "Mock response: long-answer and source-list layout test only. ".repeat(25),
                    ChatReply.Status.MOCK, IntStream.rangeClosed(1, 30)
                    .mapToObj(number -> new CourseSource(
                            "TEST_ONLY_very_long_course_document_name_for_wrapping_and_resize_checks_" + number + ".pdf",
                            CourseSource.Type.PDF_PAGE, number, true)).toList());
            case "/no-sources" -> new ChatReply(
                    "UI test only: no relevant course materials found in this simulated scenario. No search was performed.",
                    ChatReply.Status.NO_SOURCES, List.of());
            case "/error" -> new ChatReply(
                    "UI test only: simulated service error. Please try again.", ChatReply.Status.ERROR, List.of());
            case "/normal" -> new ChatReply(
                    "UI test only: preview of NORMAL status. This is not a real AI answer.",
                    ChatReply.Status.NORMAL, List.of());
            default -> new ChatReply(
                    "Mock response: This is a placeholder answer. No AI service was called.",
                    ChatReply.Status.MOCK, List.of());
        };
    }
}
