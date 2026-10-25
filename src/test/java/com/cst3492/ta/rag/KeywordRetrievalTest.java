package com.cst3492.ta.rag;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeywordRetrievalTest {
    private KeywordIndex index(String... pages) {
        var chunks = new PageChunker().split(Path.of("Lecture.pdf"), new DocumentExtractor.Document(false,
                java.util.stream.IntStream.range(0, pages.length)
                        .mapToObj(i -> new DocumentExtractor.Page(i + 1, pages[i])).toList()));
        return new KeywordIndex(List.of(chunks));
    }

    @Test
    void bm25RewardsRareKeywords() {
        var result = index("rare filler", "common filler", "common filler").retrieve("common rare", 5);
        assertEquals("p.1", result.getChunks().getFirst().getPageRange());
        assertTrue(result.getChunks().get(0).getScore() > result.getChunks().get(1).getScore());
    }

    @Test
    void bm25NormalizesLengthAndRewardsTermFrequency() {
        var shortFirst = index("transaction JDBC", "transaction " + "unrelated ".repeat(40))
                .retrieve("transaction", 5);
        assertEquals("p.1", shortFirst.getChunks().getFirst().getPageRange());
        var repeatedFirst = index("transaction transaction transaction filler", "transaction filler filler filler")
                .retrieve("transaction", 5);
        assertEquals("p.1", repeatedFirst.getChunks().getFirst().getPageRange());
        assertTrue(repeatedFirst.getChunks().get(0).getScore() > repeatedFirst.getChunks().get(1).getScore());
    }

    @Test
    void multipleKeywordMatchesRankFirstAndQueryOrderOrRepetitionDoesNotChangeScores() {
        var index = index("jdbc transaction", "jdbc servlet", "jdbc filter");
        var first = index.retrieve("jdbc transaction", 5).getChunks();
        var reordered = index.retrieve("transaction jdbc jdbc", 5).getChunks();
        assertEquals("p.1", first.getFirst().getPageRange());
        assertEquals(first.stream().map(c -> c.getPageRange()).toList(), reordered.stream().map(c -> c.getPageRange()).toList());
        assertEquals(first.stream().map(c -> c.getScore()).toList(), reordered.stream().map(c -> c.getScore()).toList());
    }

    @Test
    void handlesCaseFullWidthTextAndJavaIdentifierBoundaries() {
        var index = index("Use PreparedStatement and JDBCConnection with user_name");
        for (String question : List.of("preparedstatement", "statement", "connection", "JDBC", "ＪＤＢＣ", "user", "NAME")) {
            assertEquals(1, index.retrieve(question, 5).getChunks().size(), question);
        }
    }

    @Test
    void handlesChineseBigramsSingleCharactersAndAdjacentEnglish() {
        var index = index("Java数据库连接使用JDBC。抽象类和接口提供封装。", "事件监听器");
        for (String question : List.of("数据库连接", "JDBC数据库", "类", "接口")) {
            assertEquals("p.1", index.retrieve(question, 5).getChunks().getFirst().getPageRange(), question);
        }
        assertTrue(index.retrieve("文件", 5).isEmpty(), "A shared single character must not match a different bigram");
    }

    @Test
    void preservesSearchableJavaKeywords() {
        var index = index("for while do this");
        for (String keyword : List.of("for", "while", "do", "this")) {
            assertEquals(1, index.retrieve(keyword, 5).getChunks().size());
        }
    }

    @Test
    void breaksDocumentTiesIndependentlyOfImportOrder() {
        var z = new PageChunker.Chunk(Path.of("Z.pdf"), false, 1, 0, "JDBC");
        var a = new PageChunker.Chunk(Path.of("A.pdf"), false, 1, 0, "JDBC");
        for (var documents : List.of(List.of(List.of(z), List.of(a)), List.of(List.of(a), List.of(z)))) {
            assertEquals("A.pdf", new KeywordIndex(documents).retrieve("JDBC", 1).getChunks().getFirst().getDocumentName());
        }
    }

    @Test
    void overlappingChunksRemainBoundedAndKeepPageIdentity() {
        String text = "数据库😀".repeat(700);
        var document = new DocumentExtractor.Document(false, List.of(
                new DocumentExtractor.Page(1, ""), new DocumentExtractor.Page(2, text),
                new DocumentExtractor.Page(3, "servlet")));
        var chunks = new PageChunker().split(Path.of("Unicode.pdf"), document);
        var pageTwo = chunks.stream().filter(c -> c.page() == 2).toList();
        assertEquals(4, pageTwo.size());
        StringBuilder reconstructed = new StringBuilder(pageTwo.getFirst().content());
        for (int i = 0; i < pageTwo.size(); i++) {
            int[] points = pageTwo.get(i).content().codePoints().toArray();
            assertTrue(points.length <= 1_000);
            assertEquals("p.2", pageTwo.get(i).location());
            assertFalse(pageTwo.get(i).content().contains("servlet"));
            if (i > 0) {
                int[] previous = pageTwo.get(i - 1).content().codePoints().toArray();
                assertArrayEquals(Arrays.copyOfRange(previous, previous.length - 150, previous.length),
                        Arrays.copyOfRange(points, 0, 150));
                reconstructed.append(new String(points, 150, points.length - 150));
            }
        }
        assertEquals(text, reconstructed.toString());
        assertEquals(3, chunks.getLast().page());
    }
}
