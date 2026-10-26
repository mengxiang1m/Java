package com.cst3492.ta.rag;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Query-only course glossary, not a general translator or semantic retriever. */
final class CourseQueryExpansion {
    private static final Map<String, String> TERMS = Map.ofEntries(
            Map.entry("非受检异常", "unchecked exceptions"), Map.entry("非检查型异常", "unchecked exceptions"),
            Map.entry("非受查异常", "unchecked exceptions"), Map.entry("未检查异常", "unchecked exceptions"),
            Map.entry("受检异常", "checked exceptions"), Map.entry("检查型异常", "checked exceptions"),
            Map.entry("受查异常", "checked exceptions"), Map.entry("已检查异常", "checked exceptions"),
            Map.entry("运行时异常", "RuntimeException runtime exceptions"),
            Map.entry("异常处理", "exception handling catch throws"), Map.entry("捕获异常", "catch exceptions"),
            Map.entry("抛出异常", "throw throws exceptions"),
            Map.entry("类型擦除", "type erasure"), Map.entry("泛型", "generics generic"),
            Map.entry("通配符", "wildcards bounded"), Map.entry("上界", "upper bounded"),
            Map.entry("下界", "lower bounded"), Map.entry("协变", "covariant covariance"),
            Map.entry("不变性", "invariant invariance"),
            Map.entry("单元测试", "JUnit tests"), Map.entry("前置", "BeforeEach"),
            Map.entry("后置", "AfterEach"), Map.entry("参数化测试", "ParameterizedTest ValueSource"),
            Map.entry("异常断言", "assertThrows"), Map.entry("断言", "assertions assert"),
            Map.entry("检索增强生成", "retrieval augmented generation RAG"),
            Map.entry("关键词检索", "keyword search BM25"), Map.entry("关键字检索", "keyword search BM25"),
            Map.entry("向量检索", "vector search embeddings"), Map.entry("混合检索", "hybrid retrieval search"),
            Map.entry("倒数排名融合", "reciprocal rank fusion RRF"), Map.entry("分块", "chunk passages"),
            Map.entry("自动装箱", "autoboxing"), Map.entry("自动拆箱", "unboxing"),
            Map.entry("基本数据类型", "primitive data types"), Map.entry("构造方法", "constructors"),
            Map.entry("构造函数", "constructors"), Map.entry("继承", "inheritance inherit subclass"),
            Map.entry("多态", "polymorphism"), Map.entry("封装", "encapsulation"),
            Map.entry("抽象类", "abstract class"), Map.entry("接口", "interface"),
            Map.entry("注解", "annotations"), Map.entry("反射", "reflection"),
            Map.entry("数据库连接", "JDBC database connection"), Map.entry("依赖注入", "dependency injection"));
    private static final Pattern PHRASES = Pattern.compile(TERMS.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
            .map(Pattern::quote).collect(Collectors.joining("|")));

    static String englishTerms(String query) {
        if (query == null) return "";
        var matcher = PHRASES.matcher(Normalizer.normalize(query, Normalizer.Form.NFKC));
        StringBuilder expanded = new StringBuilder();
        // Longest, nonoverlapping matches prevent 非受检异常 from matching 受检异常.
        while (matcher.find()) expanded.append(' ').append(TERMS.get(matcher.group()));
        return expanded.toString();
    }
}
