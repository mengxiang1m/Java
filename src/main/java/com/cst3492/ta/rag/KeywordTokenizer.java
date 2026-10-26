package com.cst3492.ta.rag;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Deterministic lexical matching with query-only bilingual course terminology. */
final class KeywordTokenizer {
    private static final Pattern WORD = Pattern.compile("\\p{IsHan}+|[\\p{IsLatin}\\p{N}_]+");
    private static final Pattern IDENTIFIER_BOUNDARY = Pattern.compile(
            "(?<=[\\p{Ll}\\p{N}])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})|_+");
    // Preserve Java keywords such as 'for', 'do', and 'this'.
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "at", "from", "with", "without",
            "is", "are", "was", "were", "be", "been", "being", "it", "its", "that", "these", "those",
            "what", "which", "how", "why", "when", "where", "who", "can", "could", "would", "should",
            "does", "did", "please", "explain", "about", "me", "you", "your");
    private static final Set<String> CHINESE_STOP_WORDS = Set.of(
            "什么", "为什", "怎么", "如何", "为何", "请问", "一下", "关于", "这个", "一个", "哪些", "以及", "是否");

    List<String> indexTerms(String text) {
        return tokenize(text, false);
    }

    Set<String> queryTerms(String text) {
        // Sort to keep floating-point summation independent of query word order.
        var terms = new TreeSet<>(tokenize(text, true));
        terms.addAll(tokenize(CourseQueryExpansion.englishTerms(text), true));
        return terms;
    }

    private List<String> tokenize(String text, boolean query) {
        List<String> terms = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return terms;
        }
        var matcher = WORD.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC));
        while (matcher.find()) {
            String word = matcher.group();
            int[] points = word.codePoints().toArray();
            if (Character.UnicodeScript.of(points[0]) == Character.UnicodeScript.HAN) {
                // Long queries use bigrams, avoiding false matches on a shared single character.
                if (!query || points.length == 1) {
                    for (int point : points) {
                        terms.add(new String(Character.toChars(point)));
                    }
                }
                for (int i = 0; i + 1 < points.length; i++) {
                    String pair = new String(points, i, 2);
                    if (!CHINESE_STOP_WORDS.contains(pair)) {
                        terms.add(pair);
                    }
                }
            } else {
                String normalized = word.toLowerCase(Locale.ROOT);
                addWord(normalized, terms);
                for (String part : IDENTIFIER_BOUNDARY.split(word)) {
                    String lower = part.toLowerCase(Locale.ROOT);
                    if (!lower.equals(normalized)) {
                        addWord(lower, terms);
                    }
                }
            }
        }
        return terms;
    }

    private void addWord(String word, List<String> terms) {
        if (!word.isBlank() && word.codePoints().anyMatch(Character::isLetterOrDigit)
                && !STOP_WORDS.contains(word)) {
            terms.add(word);
        }
    }
}
