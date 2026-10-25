package com.cst3492.assistant.ui;

import com.cst3492.assistant.chat.*;
import com.cst3492.ta.core.*;
import com.cst3492.ta.llm.*;
import com.cst3492.ta.rag.KnowledgeBaseService;
import com.google.gson.Gson;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SourceEvidenceTest {
    @TempDir Path directory;

    private ChatReply reply(RetrievalResult retrieval) {
        String response = new Gson().toJson(Map.of("answer", "AI answer is not source evidence [1]",
                "basedOnCourseMaterial", true, "citations", retrieval.getChunks().stream().map(chunk ->
                        Map.of("document", chunk.getDocumentName(), "pageRange", chunk.getPageRange())).toList()));
        return new LlmChatService(new AnswerGenerationService(LlmConfig.deepseek("offline-test-placeholder"),
                (config, prompt) -> response), retrieval).reply("JDBC");
    }

    @Test void localPdfAndSlidesReachExpandedCardsWithExactTextScoresAndCopyIdentity() throws Exception {
        Path pdf = directory.resolve("lecture.pdf");
        try (var document = new PDDocument()) {
            document.addPage(new PDPage());
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(40, 700);
                stream.showText("JDBC original PDF evidence");
                stream.endText();
            }
            document.save(pdf.toFile());
        }
        Path ppt = directory.resolve("lecture.pptx");
        try (var slides = new XMLSlideShow(); var output = Files.newOutputStream(ppt)) {
            slides.createSlide();
            var text = slides.createSlide().createTextBox();
            text.setAnchor(new Rectangle2D.Double(10, 10, 500, 200));
            text.setText("JDBC original slide evidence");
            slides.write(output);
        }
        var kb = new KnowledgeBaseService();
        kb.ingest(pdf);
        kb.ingest(ppt);
        RetrievalResult retrieval = kb.retrieve("JDBC", 5);
        ChatReply reply = reply(retrieval);
        assertEquals(2, reply.sources().size());
        SwingUtilities.invokeAndWait(() -> {
            List<String> copied = new ArrayList<>();
            var cards = new SourceCards(reply.sources(), copied::add);
            cards.setExpanded(true);
            for (var card : cards.cards()) {
                card.setExpanded(true);
                assertTrue(card.isExpanded());
                var source = card.source();
                RetrievedChunk chunk = retrieval.getChunks().stream().filter(c ->
                        c.getDocumentName().equals(source.documentName())).findFirst().orElseThrow();
                assertEquals(List.of(new CourseSource.Excerpt(chunk.getContent(), chunk.getScore())), source.excerpts());
                assertEquals(2, source.number());
                assertFalse(source.testData());
                assertTrue(views(card).stream().anyMatch(v -> v.isVisible() && v.getText().equals(chunk.getContent())));
                assertTrue(labels(card).stream().anyMatch(s -> s.contains("Retrieval score: " + chunk.getScore())));
                assertTrue(views(card).stream().anyMatch(v -> v.getText().equals(source.displayName())));
                assertFalse(views(card).stream().anyMatch(v -> v.getText().equals(reply.text())));
                components(card).stream().filter(c -> c instanceof JButton b && b.getText().equals("Copy citation"))
                        .map(c -> (JButton) c).findFirst().orElseThrow().doClick();
                assertTrue(copied.getLast().startsWith(source.documentName()));
                assertTrue(copied.getLast().contains(source.type() == CourseSource.Type.PDF_PAGE ? "Page 2" : "Slide 2"));
                card.setExpanded(false);
                card.setExpanded(true);
                assertEquals(chunk.getContent(), source.excerpts().getFirst().text());
            }
        });
    }

    @Test void allSamePageChunksStaySeparateAndVerbatimIncludingMarkupAndLineBreaks() throws Exception {
        String first = "  **literal**, <tag>\r\n\tcode();\n";
        String second = "another unmodified excerpt";
        var reply = reply(new RetrievalResult(List.of(
                new RetrievedChunk("C:\\course\\same.pdf", "p.3", first, 2.5),
                new RetrievedChunk("C:\\course\\same.pdf", "p.3", second, 1.2))));
        assertEquals(1, reply.sources().size());
        assertEquals(List.of(first, second), reply.sources().getFirst().excerpts().stream()
                .map(CourseSource.Excerpt::text).toList());
        SwingUtilities.invokeAndWait(() -> {
            var cards = new SourceCards(reply.sources(), ignored -> { });
            cards.setExpanded(true);
            var card = cards.cards().getFirst();
            card.setExpanded(true);
            assertTrue(views(card).stream().anyMatch(v -> v.getText().equals(first)));
            assertTrue(views(card).stream().anyMatch(v -> v.getText().equals(second)));
        });
    }

    @Test void absentAndBlankExcerptsNeverProduceInventedTextOrScores() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (CourseSource source : List.of(
                    new CourseSource("course.pdf", CourseSource.Type.PDF_PAGE, 1, false),
                    new CourseSource("course.pdf", CourseSource.Type.PDF_PAGE, 1, false,
                            List.of(new CourseSource.Excerpt(" \n\t", null))))) {
                var card = new SourceCards(List.of(source), ignored -> { }).cards().getFirst();
                card.setExpanded(true);
                assertTrue(labels(card).stream().anyMatch(s -> s.contains("当前来源未提供文本摘录")));
                assertFalse(labels(card).stream().anyMatch(s -> s.contains("Retrieval score:")));
            }
        });
    }

    @Test void nonFiniteRetrievalScoreIsAbsentRatherThanInvented() throws Exception {
        for (double score : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var result = reply(new RetrievalResult(List.of(new RetrievedChunk("course.pdf", "p.1", "actual text", score))));
            assertNull(result.sources().getFirst().excerpts().getFirst().score());
            SwingUtilities.invokeAndWait(() -> {
                var card = new SourceCards(result.sources(), ignored -> { }).cards().getFirst();
                card.setExpanded(true);
                assertTrue(views(card).stream().anyMatch(v -> v.getText().equals("actual text")));
                assertFalse(labels(card).stream().anyMatch(s -> s.contains("Retrieval score:")));
            });
        }
    }

    @Test void mockEvidenceStaysExplicitlyMarkedAndMetadataOnly() throws Exception {
        var reply = new MockChatService().reply("/sources");
        SwingUtilities.invokeAndWait(() -> {
            var cards = new SourceCards(reply.sources(), ignored -> { });
            for (var card : cards.cards()) {
                card.setExpanded(true);
                assertTrue(card.source().testData());
                assertTrue(card.source().excerpts().isEmpty());
                assertTrue(labels(card).stream().anyMatch(s -> s.contains("Test data, not evidence")));
                assertTrue(labels(card).stream().anyMatch(s -> s.contains("当前来源未提供文本摘录")));
            }
        });
    }

    private static List<Component> components(Container root) {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            result.add(child);
            if (child instanceof Container container) result.addAll(components(container));
        }
        return result;
    }
    private static List<MarkdownView> views(Container root) {
        return components(root).stream().filter(c -> c instanceof MarkdownView).map(c -> (MarkdownView) c).toList();
    }
    private static List<String> labels(Container root) {
        return components(root).stream().filter(c -> c instanceof WrappingLabel)
                .map(c -> ((WrappingLabel) c).getText()).toList();
    }
}
