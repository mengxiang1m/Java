package com.cst3492.assistant.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatRequestTest {
    @Test
    void selectedCodePreservesIndentationTabsLineEndingsAndTrailingWhitespace() {
        String code = "\tclass Demo {\r\n    void run() {\n        // 中文 <>& ```\n    }  \r\n}\n";
        ChatRequest request = new ChatRequest("  Explain this code \n", code);
        assertEquals(code, request.editorContext());
        assertEquals("Explain this code", request.question());
    }

    @Test
    void missingAndWhitespaceOnlySelectionsAreNotCode() {
        assertFalse(ChatRequest.hasSelectedCode(null));
        assertFalse(ChatRequest.hasSelectedCode(""));
        assertFalse(ChatRequest.hasSelectedCode(" \t\r\n"));
        assertTrue(ChatRequest.hasSelectedCode("\n  int x = 1;\n"));
    }

    @Test
    void contextAloneDoesNotAllowSendingWithoutAQuestion() {
        assertThrows(IllegalArgumentException.class, () -> new ChatRequest(" \n", "int x;"));
    }

    @Test
    void ordinaryQuestionsDoNotNeedEditorContext() {
        ChatRequest request = new ChatRequest("/sources", "");
        assertEquals("", request.editorContext());
        assertEquals(2, new MockChatService().reply(request).sources().size());
    }

    @Test
    void requestSnapshotDoesNotChangeWhenLatestSelectionChanges() {
        String latestSelection = "int first;\n";
        ChatRequest first = new ChatRequest("Explain", latestSelection);
        latestSelection = "int second;\n";
        ChatRequest second = new ChatRequest("Explain", latestSelection);
        assertEquals("int first;\n", first.editorContext());
        assertEquals("int second;\n", second.editorContext());
    }

    @Test
    void contextDoesNotReplaceQuestionOrBreakMockSourceScenarios() {
        ChatService service = new MockChatService();
        ChatReply reply = service.reply(new ChatRequest("/sources", "// /error\nint x;"));
        assertEquals(ChatReply.Status.MOCK, reply.status());
        assertEquals(2, reply.sources().size());
    }

    @Test
    void invalidContextAndNullFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ChatRequest("Question", " \n"));
        assertThrows(NullPointerException.class, () -> new ChatRequest("Question", null));
        assertThrows(NullPointerException.class, () -> new ChatRequest(null, ""));
    }
}
