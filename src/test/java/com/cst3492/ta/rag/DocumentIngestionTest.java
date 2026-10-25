package com.cst3492.ta.rag;

import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.cst3492.ta.rag.DocumentIngestionException.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentIngestionTest {
    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "pptx", "ppt", "PDF", "PPTX", "PPT"})
    void extractsRealFilesAndPreservesPhysicalPageNumbers(String extension) throws Exception {
        Path path = TestDocuments.write(temp.resolve("Lecture." + extension), "",
                "JDBC uses PreparedStatement", "Transactions support rollback");
        var knowledge = new KnowledgeBaseService();
        var report = knowledge.ingest(path);
        assertEquals(3, report.pageCount());
        assertEquals(2, report.chunkCount());
        assertTrue(report.hasExtractableText());
        assertEquals(path.toRealPath(), report.path());
        var chunks = knowledge.retrieve("preparedstatement").getChunks();
        assertEquals(1, chunks.size());
        var chunk = chunks.getFirst();
        assertEquals(report.documentName(), chunk.getDocumentName());
        assertEquals(extension.equalsIgnoreCase("pdf") ? "p.2" : "slide 2", chunk.getPageRange());
        assertTrue(chunk.getContent().contains("PreparedStatement"));
        assertFalse(chunk.getContent().contains("rollback"));
        assertTrue(chunk.getScore() > 0 && Double.isFinite(chunk.getScore()));
    }

    @Test
    void extractsPptxTablesAndNestedGroupsWithoutDuplicatingCells() throws Exception {
        Path path = temp.resolve("Shapes.pptx");
        try (var show = new XMLSlideShow(); var output = Files.newOutputStream(path)) {
            var slide = show.createSlide();
            var group = slide.createGroup();
            group.setAnchor(new Rectangle2D.Double(10, 10, 400, 200));
            group.setInteriorAnchor(new Rectangle2D.Double(0, 0, 400, 200));
            var nested = group.createGroup();
            nested.setAnchor(new Rectangle2D.Double(10, 10, 300, 100));
            nested.setInteriorAnchor(new Rectangle2D.Double(0, 0, 300, 100));
            nested.createTextBox().setText("Grouped JDBC connection");
            var table = slide.createTable(1, 2);
            table.getCell(0, 0).setText("PreparedStatement");
            table.getCell(0, 1).setText("参数绑定防止注入");
            show.write(output);
        }
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(path);
        String text = knowledge.retrieve("JDBC PreparedStatement 参数绑定").getChunks().getFirst().getContent();
        assertTrue(text.contains("Grouped JDBC connection"));
        assertTrue(text.contains("参数绑定防止注入"));
        assertEquals(1, text.split("PreparedStatement", -1).length - 1);
    }

    @Test
    void extractsLegacyPptTablesAndGroups() throws Exception {
        Path path = temp.resolve("Shapes.ppt");
        try (var show = new HSLFSlideShow(); var output = Files.newOutputStream(path)) {
            var slide = show.createSlide();
            var group = slide.createGroup();
            group.setAnchor(new Rectangle2D.Double(10, 10, 400, 200));
            group.setInteriorAnchor(new Rectangle2D.Double(0, 0, 400, 200));
            group.createTextBox().setText("Grouped servlet lifecycle");
            var table = slide.createTable(1, 2);
            table.getCell(0, 0).setText("HttpSession");
            table.getCell(0, 1).setText("会话管理");
            show.write(output);
        }
        var knowledge = new KnowledgeBaseService();
        knowledge.ingest(path);
        var chunks = knowledge.retrieve("servlet HttpSession 会话").getChunks();
        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().getContent().contains("Grouped servlet lifecycle"));
        assertTrue(chunks.getFirst().getContent().contains("会话管理"));
        assertEquals(1, chunks.getFirst().getContent().split("HttpSession", -1).length - 1);
    }

    @Test
    void importedLongSlideIsChunkedWithoutLeakingIntoOtherSlides() throws Exception {
        String text = "prefix ".repeat(150) + "数据库连接管理😀" + " suffix".repeat(150);
        Path path = TestDocuments.write(temp.resolve("Long.pptx"), "", text, "servlet lifecycle");
        var knowledge = new KnowledgeBaseService();
        var report = knowledge.ingest(path);
        assertEquals(3, report.pageCount());
        assertTrue(report.chunkCount() > 2);
        var chunks = knowledge.retrieve("数据库连接").getChunks();
        assertFalse(chunks.isEmpty());
        for (var chunk : chunks) {
            assertEquals("slide 2", chunk.getPageRange());
            assertTrue(chunk.getContent().codePointCount(0, chunk.getContent().length()) <= 1_000);
            assertFalse(chunk.getContent().contains("servlet"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "pptx", "ppt"})
    void validBlankDocumentsProduceAnExplicitZeroChunkReport(String extension) throws Exception {
        var knowledge = new KnowledgeBaseService();
        var report = knowledge.ingest(TestDocuments.write(temp.resolve("Blank." + extension), "", ""));
        assertEquals(2, report.pageCount());
        assertEquals(0, report.chunkCount());
        assertFalse(report.hasExtractableText());
        assertTrue(knowledge.retrieve("JDBC").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "pptx", "ppt"})
    void malformedFilesFailInsteadOfPretendingThereAreNoMatches(String extension) throws Exception {
        Path path = temp.resolve("Broken." + extension);
        Files.writeString(path, "This is not a document");
        var failure = assertThrows(DocumentIngestionException.class,
                () -> new KnowledgeBaseService().ingest(path));
        assertEquals(MALFORMED_DOCUMENT, failure.getReason());
        assertNotNull(failure.getCause());
    }

    @Test
    void rejectsUnsupportedMissingAndDirectoryInputs() throws Exception {
        Path unsupported = Files.writeString(temp.resolve("Lecture.txt"), "JDBC");
        var knowledge = new KnowledgeBaseService();
        assertEquals(UNSUPPORTED_FORMAT,
                assertThrows(DocumentIngestionException.class, () -> knowledge.ingest(unsupported)).getReason());
        assertEquals(UNREADABLE_FILE,
                assertThrows(DocumentIngestionException.class, () -> knowledge.ingest(temp.resolve("missing.pdf"))).getReason());
        Path directory = Files.createDirectory(temp.resolve("directory.pdf"));
        assertEquals(UNREADABLE_FILE,
                assertThrows(DocumentIngestionException.class, () -> knowledge.ingest(directory)).getReason());
    }

    @Test
    void rejectsFileSystemRootAsUnreadableInput() {
        Path root = temp.toAbsolutePath().getRoot();
        var failure = assertThrows(DocumentIngestionException.class,
                () -> new KnowledgeBaseService().ingest(root));
        assertEquals(UNREADABLE_FILE, failure.getReason());
    }

    @Test
    void rejectsEncryptedPdfAndPptxWithAnExplicitReason() throws Exception {
        Path pdf = TestDocuments.pdf(temp.resolve("Encrypted.pdf"), true, "JDBC");
        Path pptx = TestDocuments.encryptedPptx(temp.resolve("Encrypted.pptx"));
        for (Path path : new Path[]{pdf, pptx}) {
            var error = assertThrows(DocumentIngestionException.class, () -> new KnowledgeBaseService().ingest(path));
            assertEquals(ENCRYPTED_DOCUMENT, error.getReason());
        }
    }
}
