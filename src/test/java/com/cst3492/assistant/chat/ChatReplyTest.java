package com.cst3492.assistant.chat;

import com.cst3492.assistant.ui.ChatReplyPresentation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatReplyTest {
    private final MockChatService service = new MockChatService();

    @Test
    void ordinaryMockDoesNotInventSources() {
        ChatReply reply = service.reply("Explain polymorphism");
        assertEquals(ChatReply.Status.MOCK, reply.status());
        assertTrue(reply.sources().isEmpty());
        assertTrue(reply.text().contains("Mock response"));
        assertFalse(ChatReplyPresentation.message(reply).text().contains("Sources:"));
    }

    @Test
    void sourcePreviewPreservesTypesLocationsAndTestMarkers() {
        ChatReply reply = service.reply(" /sources \n");
        assertEquals(CourseSource.Type.PDF_PAGE, reply.sources().get(0).type());
        assertEquals(3, reply.sources().get(0).number());
        assertEquals(CourseSource.Type.PPT_SLIDE, reply.sources().get(1).type());
        assertEquals(5, reply.sources().get(1).number());
        assertTrue(reply.sources().stream().allMatch(CourseSource::testData));
        String rendered = ChatReplyPresentation.message(reply).text();
        assertTrue(rendered.contains("[MOCK"));
        assertTrue(rendered.contains("[TEST DATA - not evidence] TEST_ONLY_Chapter_01.pdf — Page 3"));
        assertTrue(rendered.contains("[TEST DATA - not evidence] TEST_ONLY_Chapter_02.pptx — Slide 5"));
        assertTrue(rendered.indexOf("Mock response") < rendered.indexOf("Sources:"));
    }

    @Test
    void sourceCollectionIsDefensivelyCopiedAndImmutable() {
        List<CourseSource> sources = new ArrayList<>();
        sources.add(new CourseSource("Lecture.pdf", CourseSource.Type.PDF_PAGE, 1, false));
        ChatReply reply = new ChatReply("Answer", ChatReply.Status.NORMAL, sources);
        sources.clear();
        assertEquals(1, reply.sources().size());
        assertThrows(UnsupportedOperationException.class, () -> reply.sources().clear());
    }

    @Test
    void sourceRequiresNameTypeAndPositiveOneBasedLocation() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSource(" ", CourseSource.Type.PDF_PAGE, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSource("Lecture.pdf", CourseSource.Type.PDF_PAGE, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSource("Lecture.pptx", CourseSource.Type.PPT_SLIDE, -1, false));
        assertThrows(NullPointerException.class, () -> new CourseSource("Lecture.pdf", null, 1, false));
    }

    @Test
    void noSourcesIsAnAssistantOutcomeRatherThanAnError() {
        ChatReply reply = service.reply("/no-sources");
        assertEquals(ChatReply.Status.NO_SOURCES, reply.status());
        ChatMessage message = ChatReplyPresentation.message(reply);
        assertEquals(ChatMessage.Role.ASSISTANT, message.role());
        assertTrue(message.text().contains("[NO_SOURCES"));
        assertFalse(message.text().contains("Sources:"));
        assertFalse(ChatReplyPresentation.statusText(reply).contains("failed"));
    }

    @Test
    void serviceErrorIsNotRenderedAsNormalAnswer() {
        ChatReply reply = service.reply("/error");
        assertEquals(ChatReply.Status.ERROR, reply.status());
        assertEquals(ChatMessage.Role.ERROR, ChatReplyPresentation.message(reply).role());
        assertTrue(ChatReplyPresentation.message(reply).text().contains("[ERROR"));
        assertTrue(ChatReplyPresentation.statusText(reply).contains("failed"));
        assertFalse(ChatReplyPresentation.message(reply).text().contains("Sources:"));
    }

    @Test
    void normalAnswerCanHaveNoCitationsWithoutBecomingAnError() {
        ChatReply reply = new ChatReply("Answer", ChatReply.Status.NORMAL, List.of());
        assertEquals(ChatMessage.Role.ASSISTANT, ChatReplyPresentation.message(reply).role());
        assertTrue(ChatReplyPresentation.message(reply).text().startsWith("[NORMAL]"));
        assertFalse(ChatReplyPresentation.message(reply).text().contains("Sources:"));
    }

    @Test
    void normalSourcesAreRenderedWithoutMockLabelsOrInventedLocations() {
        ChatReply reply = new ChatReply("Answer", ChatReply.Status.NORMAL,
                List.of(new CourseSource("Actual supplied name.pdf", CourseSource.Type.PDF_PAGE, 17, false)));
        String text = ChatReplyPresentation.message(reply).text();
        assertTrue(text.contains("Actual supplied name.pdf — Page 17"));
        assertFalse(text.contains("TEST DATA"));
        assertFalse(text.contains("MOCK"));
    }

    @Test
    void contradictoryReplyStatesAreRejected() {
        List<CourseSource> sources = List.of(new CourseSource("Test.pdf", CourseSource.Type.PDF_PAGE, 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> new ChatReply("No materials", ChatReply.Status.NO_SOURCES, sources));
        assertThrows(IllegalArgumentException.class,
                () -> new ChatReply("Failed", ChatReply.Status.ERROR, sources));
        assertThrows(IllegalArgumentException.class,
                () -> new ChatReply("Mock", ChatReply.Status.MOCK,
                        List.of(new CourseSource("Test.pdf", CourseSource.Type.PDF_PAGE, 1, false))));
        assertThrows(IllegalArgumentException.class,
                () -> new ChatReply(" \n", ChatReply.Status.NORMAL, List.of()));
    }

    @Test
    void manySourcesRetainOrderAndFullNamesWithoutTruncation() {
        ChatReply reply = service.reply("/many-sources");
        assertEquals(30, reply.sources().size());
        String rendered = ChatReplyPresentation.message(reply).text();
        int previous = -1;
        for (CourseSource source : reply.sources()) {
            int position = rendered.indexOf(source.documentName() + " — Page " + source.number());
            assertTrue(position > previous);
            previous = position;
        }
        assertTrue(reply.sources().stream().allMatch(CourseSource::testData));
    }

    @Test
    void normalStatusPreviewStillDisclosesItsSimulatedOrigin() {
        ChatReply reply = service.reply("/normal");
        assertEquals(ChatReply.Status.NORMAL, reply.status());
        assertTrue(reply.text().contains("UI test only"));
        assertTrue(reply.sources().isEmpty());
    }
}
