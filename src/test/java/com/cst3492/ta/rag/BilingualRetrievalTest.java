package com.cst3492.ta.rag;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BilingualRetrievalTest {
    private KeywordIndex index(String... pages) {
        return new KeywordIndex(List.of(new PageChunker().split(Path.of("English.pdf"),
                new DocumentExtractor.Document(false, java.util.stream.IntStream.range(0, pages.length)
                        .mapToObj(i -> new DocumentExtractor.Page(i + 1, pages[i])).toList()))));
    }

    @Test void chineseCourseTermsRetrieveEnglishPassagesAndPhysicalPages() {
        var index = index("Checked exceptions and unchecked exceptions", "Generics type erasure",
                "JUnit BeforeEach AfterEach lifecycle", "Retrieval augmented generation RAG",
                "Inheritance polymorphism encapsulation", "BM25 keyword search vector hybrid retrieval");
        String[] questions = {"什么是受检异常和非受检异常？", "泛型的类型擦除是什么？", "单元测试的前置和后置操作？",
                "检索增强生成有哪些步骤？", "继承和多态是什么？", "为什么结合关键词检索和向量检索？"};
        for (int i = 0; i < questions.length; i++) {
            var result = index.retrieve(questions[i], 5);
            assertFalse(result.isEmpty(), questions[i]);
            assertEquals("p." + (i + 1), result.getChunks().getFirst().getPageRange(), questions[i]);
        }
    }

    @Test void uncheckedPhraseDoesNotAlsoExpandToCheckedException() {
        var terms = new KeywordTokenizer().queryTerms("非受检异常");
        assertTrue(terms.contains("unchecked"));
        assertFalse(terms.contains("checked"));
    }

    @Test void unknownChineseQuestionKeepsNoResultAndEnglishScoresUnchanged() {
        var index = index("JDBC transactions", "JUnit lifecycle");
        assertTrue(index.retrieve("量子香蕉的颜色", 5).isEmpty());
        assertEquals(new KeywordTokenizer().queryTerms("JDBC transactions"),
                new KeywordTokenizer().queryTerms("transactions JDBC"));
    }
}
