package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ResponseCopyTest {
    @ParameterizedTest
    @ValueSource(strings = {"Short answer", "# 标题\n\n- 列表\n1. 顺序\n[链接](https://example.com)\n```java\n\tint x = 1;\n```\n",
            "中文回答\r\n第二行\n  缩进", "LONG"})
    void buttonCopiesEntireOriginalBodyWithoutSourcesOrRenderedFormatting(String content) throws Exception {
        String text = content.equals("LONG") ? "## Long answer\n".repeat(2000) : content;
        SwingUtilities.invokeAndWait(() -> {
            var reply = new ChatReply(text, ChatReply.Status.NORMAL, List.of(
                    new CourseSource("not-body.pdf", CourseSource.Type.PDF_PAGE, 3, false)));
            AtomicReference<String> clipboard = new AtomicReference<>();
            var card = new MessageCard(ChatReplyPresentation.cardMessage(reply), reply, null, clipboard::set);
            assertNotNull(card.copyResponseButton().getIcon());
            assertEquals("", card.copyResponseButton().getText());
            assertEquals("Copy response", card.copyResponseButton().getToolTipText());
            card.copyResponseButton().doClick();
            assertEquals(text, clipboard.get());
            assertTrue(card.copyFeedbackText().contains("Copied"));
            assertNotNull(card.sources());
        });
    }

    @Test void mockCopiesBodyWithoutDiagnosticPrefix() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var reply = new MockChatService().reply("/sources");
            AtomicReference<String> clipboard = new AtomicReference<>();
            var card = new MessageCard(ChatReplyPresentation.cardMessage(reply), reply, null, clipboard::set);
            card.copyResponseButton().doClick();
            assertEquals(reply.text(), clipboard.get());
            assertFalse(clipboard.get().contains("TEST_ONLY_Chapter"));
        });
    }

    @Test void errorsAndUnfinishedMessagesCannotBeCopiedAsAnswers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var error = new MockChatService().reply("/error");
            var card = new MessageCard(ChatReplyPresentation.cardMessage(error), error, () -> { }, text -> fail());
            assertNull(card.copyResponseButton());
            for (var role : List.of(ChatMessage.Role.USER, ChatMessage.Role.EDITOR_CONTEXT, ChatMessage.Role.ASSISTANT)) {
                assertNull(new MessageCard(new ChatMessage(role, "Pending"), null, null, text -> fail()).copyResponseButton());
            }
        });
    }

    @Test void clipboardFailureIsSanitizedAndCopyCanBeRetriedWithoutChatRetry() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var reply = new MockChatService().reply("Question");
            var attempts = new java.util.concurrent.atomic.AtomicInteger();
            var clipboard = new AtomicReference<String>();
            var card = new MessageCard(ChatReplyPresentation.cardMessage(reply), reply,
                    () -> fail("Must not retry the model"), text -> {
                        if (attempts.getAndIncrement() == 0) throw new IllegalStateException("private clipboard details");
                        clipboard.set(text);
                    });
            assertDoesNotThrow(() -> card.copyResponseButton().doClick());
            assertTrue(card.copyFeedbackText().contains("Copy failed"));
            assertFalse(card.copyFeedbackText().contains("private"));
            card.copyResponseButton().doClick();
            assertEquals(reply.text(), clipboard.get());
            assertTrue(card.copyFeedbackText().contains("Copied"));
        });
    }

    @Test void placeholderFollowsFocusWithoutChangingQuestionDocument() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var input = new com.intellij.ui.components.JBTextArea();
            new ChatComposer(input, new javax.swing.JButton());
            assertTrue(input.getEmptyText().getText().contains("Ask anything..."));
            for (var listener : input.getFocusListeners()) listener.focusGained(
                    new java.awt.event.FocusEvent(input, java.awt.event.FocusEvent.FOCUS_GAINED));
            assertEquals("", input.getEmptyText().getText());
            input.setText("draft\n  code");
            for (var listener : input.getFocusListeners()) listener.focusLost(
                    new java.awt.event.FocusEvent(input, java.awt.event.FocusEvent.FOCUS_LOST));
            assertEquals("draft\n  code", input.getText());
            assertTrue(input.getEmptyText().getText().contains("Ask anything..."));
        });
    }

    @Test void disposedResponseDoesNotCopyOrUpdateFeedback() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var reply = new MockChatService().reply("Question");
            var card = new MessageCard(ChatReplyPresentation.cardMessage(reply), reply, null, text -> fail());
            card.dispose();
            card.copyResponseButton().doClick();
            assertFalse(card.copyFeedbackText().contains("Copied"));
        });
    }
}
