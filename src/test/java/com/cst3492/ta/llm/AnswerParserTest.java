package com.cst3492.ta.llm;

import com.cst3492.ta.core.AssistantAnswer;
import com.cst3492.ta.core.RetrievedChunk;
import com.cst3492.ta.core.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerParserTest {

    private final AnswerParser parser = new AnswerParser();

    @Test
    void sourceIdUsesRealMetadataAndRenumbersTheVisibleReference() {
        var retrieval = new RetrievalResult(List.of(chunk("Other.pdf", "p.1"),
                chunk("/course/课程资料/Lec-3.1-Exceptions.pdf", "p.5")));
        var answer = parser.parse("""
                {"answer":"Checked exceptions must be caught or declared [2].",
                 "citations":[{"sourceId":2}],"basedOnCourseMaterial":true}
                """, retrieval);
        assertTrue(answer.isBasedOnCourseMaterial());
        assertEquals("Checked exceptions must be caught or declared [1].", answer.getContent());
        assertEquals("/course/课程资料/Lec-3.1-Exceptions.pdf", answer.getCitations().getFirst().getDocumentName());
        assertEquals("p.5", answer.getCitations().getFirst().getPageRange());
    }

    @Test
    void invalidSourceIdsCannotCreateSources() {
        var answer = parser.parse("""
                {"answer":"Answer [0] [9].", "citations":[{"sourceId":0},{"sourceId":9}],
                 "basedOnCourseMaterial":true}
                """, new RetrievalResult(List.of(chunk("Lecture.pdf", "p.5"))));
        assertFalse(answer.isBasedOnCourseMaterial());
        assertTrue(answer.getCitations().isEmpty());
        assertFalse(answer.getContent().contains("[9]"));
    }

    @Test
    void duplicateSourceIdsForOnePageShareOneDisplayedCitation() {
        var answer = parser.parse("""
                {"answer":"First [1], then [2].", "citations":[{"sourceId":1},{"sourceId":2}],
                 "basedOnCourseMaterial":true}
                """, new RetrievalResult(List.of(chunk("Lecture.pdf", "p.5"), chunk("Lecture.pdf", "p.5"))));
        assertTrue(answer.isBasedOnCourseMaterial());
        assertEquals(1, answer.getCitations().size());
        assertEquals("First [1], then [1].", answer.getContent());
    }

    @Test
    void conflictingMetadataCannotOverrideASourceId() {
        var answer = parser.parse("""
                {"answer":"Answer [1].", "citations":[{"sourceId":1,"document":"Fake.pdf","pageRange":"p.99"}],
                 "basedOnCourseMaterial":true}
                """, new RetrievalResult(List.of(chunk("Lecture.pdf", "p.5"))));
        assertFalse(answer.isBasedOnCourseMaterial());
        assertTrue(answer.getCitations().isEmpty());
    }

    private static RetrievedChunk chunk(String document, String pageRange) {
        return new RetrievedChunk(document, pageRange, "chunk content", 0.9);
    }

    @Test
    void parsesValidJsonWithCitations() {
        String raw = "{\"answer\":\"Use JDBC [1]\","
                + "\"citations\":[{\"document\":\"L3.pdf\",\"pageRange\":\"p.12\"}],"
                + "\"basedOnCourseMaterial\":true}";
        RetrievalResult retrieval = new RetrievalResult(List.of(chunk("L3.pdf", "p.12")));

        AssistantAnswer answer = parser.parse(raw, retrieval);

        assertTrue(answer.isBasedOnCourseMaterial());
        assertEquals("Use JDBC [1]", answer.getContent());
        assertEquals(1, answer.getCitations().size());
        assertEquals("L3.pdf", answer.getCitations().get(0).getDocumentName());
        assertEquals("p.12", answer.getCitations().get(0).getPageRange());
    }

    @Test
    void stripsMarkdownFencesAndLeadingProse() {
        String raw = "Here is the answer:\n```json\n"
                + "{\"answer\":\"hi\",\"citations\":[],\"basedOnCourseMaterial\":false}\n```";

        AssistantAnswer answer = parser.parse(raw, RetrievalResult.empty());

        assertEquals("hi", answer.getContent());
        assertFalse(answer.isBasedOnCourseMaterial());
    }

    @Test
    void notGroundedWhenRetrievalFoundNothing() {
        String raw = "{\"answer\":\"Use JDBC [1]\","
                + "\"citations\":[{\"document\":\"L3.pdf\",\"pageRange\":\"p.12\"}],"
                + "\"basedOnCourseMaterial\":true}";

        AssistantAnswer answer = parser.parse(raw, RetrievalResult.empty());

        assertFalse(answer.isBasedOnCourseMaterial());
        assertTrue(answer.getCitations().isEmpty()); // a citation with no backing chunk is dropped
    }

    @Test
    void fallsBackToPlainTextWhenJsonIsInvalid() {
        AssistantAnswer answer = parser.parse("Sorry, I have no idea.", RetrievalResult.empty());

        assertEquals("Sorry, I have no idea.", answer.getContent());
        assertFalse(answer.isBasedOnCourseMaterial());
        assertTrue(answer.getCitations().isEmpty());
    }

    @Test
    void blankProducesGracefulMessage() {
        AssistantAnswer answer = parser.parse("   ", RetrievalResult.empty());

        assertNotNull(answer.getContent());
        assertFalse(answer.isBasedOnCourseMaterial());
    }

    @Test
    void ignoresCitationWithoutDocumentName() {
        String raw = "{\"answer\":\"x\","
                + "\"citations\":[{\"pageRange\":\"p.1\"}],"
                + "\"basedOnCourseMaterial\":true}";

        AssistantAnswer answer = parser.parse(raw,
                new RetrievalResult(List.of(chunk("d", "p.1"))));

        assertEquals(0, answer.getCitations().size());
        assertFalse(answer.isBasedOnCourseMaterial());
    }

    // --- P1 regression tests ---

    @Test
    void plainTextWithRelevantMaterialIsNotGrounded() {
        AssistantAnswer answer = parser.parse(
                "JDBC is a Java API for database access.",
                new RetrievalResult(List.of(chunk("L3.pdf", "p.12"))));

        assertEquals("JDBC is a Java API for database access.", answer.getContent());
        assertTrue(answer.getCitations().isEmpty());
        assertFalse(answer.isBasedOnCourseMaterial());
    }

    @Test
    void dropsCitationWhoseDocumentIsNotRetrieved() {
        String raw = "{\"answer\":\"Use [1].\","
                + "\"citations\":[{\"document\":\"Fake.pdf\",\"pageRange\":\"p.1\"}],"
                + "\"basedOnCourseMaterial\":true}";

        AssistantAnswer answer = parser.parse(raw,
                new RetrievalResult(List.of(chunk("L3.pdf", "p.12"))));

        assertTrue(answer.getCitations().isEmpty());
        assertFalse(answer.isBasedOnCourseMaterial());
    }

    @Test
    void dropsCitationWhosePageRangeDoesNotMatch() {
        String raw = "{\"answer\":\"Use [1].\","
                + "\"citations\":[{\"document\":\"L3.pdf\",\"pageRange\":\"p.99\"}],"
                + "\"basedOnCourseMaterial\":true}";

        AssistantAnswer answer = parser.parse(raw,
                new RetrievalResult(List.of(chunk("L3.pdf", "p.12"))));

        assertTrue(answer.getCitations().isEmpty());
        assertFalse(answer.isBasedOnCourseMaterial());
    }

    @Test
    void keepsOnlyValidCitationsWhenMixed() {
        String raw = "{\"answer\":\"Use [1].\","
                + "\"citations\":["
                + "{\"document\":\"L3.pdf\",\"pageRange\":\"p.12\"},"
                + "{\"document\":\"Fake.pdf\",\"pageRange\":\"p.1\"}],"
                + "\"basedOnCourseMaterial\":true}";

        AssistantAnswer answer = parser.parse(raw,
                new RetrievalResult(List.of(chunk("L3.pdf", "p.12"))));

        assertEquals(1, answer.getCitations().size());
        assertEquals("L3.pdf", answer.getCitations().get(0).getDocumentName());
        assertTrue(answer.isBasedOnCourseMaterial());
    }

    @Test
    void matchesCitationWhenBothPageRangesAbsent() {
        String raw = "{\"answer\":\"Use [1].\","
                + "\"citations\":[{\"document\":\"L3.pdf\"}],"
                + "\"basedOnCourseMaterial\":true}";

        AssistantAnswer answer = parser.parse(raw,
                new RetrievalResult(List.of(chunk("L3.pdf", null))));

        assertEquals(1, answer.getCitations().size());
        assertTrue(answer.isBasedOnCourseMaterial());
    }

    @Test
    void emptyCitationsNeverGroundsEvenIfFlagTrue() {
        String raw = "{\"answer\":\"42\","
                + "\"citations\":[],\"basedOnCourseMaterial\":true}";

        AssistantAnswer answer = parser.parse(raw,
                new RetrievalResult(List.of(chunk("L3.pdf", "p.12"))));

        assertTrue(answer.getCitations().isEmpty());
        assertFalse(answer.isBasedOnCourseMaterial());
    }
}
