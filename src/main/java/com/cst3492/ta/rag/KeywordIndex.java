package com.cst3492.ta.rag;

import com.cst3492.ta.core.RetrievalResult;
import com.cst3492.ta.core.RetrievedChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable inverted-index snapshot. Scores are BM25 values, not probabilities. */
final class KeywordIndex {
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final KeywordTokenizer TOKENIZER = new KeywordTokenizer();

    private record Entry(PageChunker.Chunk source, int length) { }

    private final List<Entry> entries;
    private final Map<String, Map<Integer, Integer>> postings;
    private final double averageLength;

    KeywordIndex(Collection<List<PageChunker.Chunk>> documents) {
        List<Entry> buildingEntries = new ArrayList<>();
        Map<String, Map<Integer, Integer>> buildingPostings = new HashMap<>();
        long totalLength = 0;
        for (var document : documents) {
            for (var chunk : document) {
                List<String> terms = TOKENIZER.indexTerms(chunk.content());
                int id = buildingEntries.size();
                buildingEntries.add(new Entry(chunk, terms.size()));
                totalLength += terms.size();
                for (String term : terms) {
                    buildingPostings.computeIfAbsent(term, ignored -> new HashMap<>()).merge(id, 1, Integer::sum);
                }
            }
        }
        entries = List.copyOf(buildingEntries);
        Map<String, Map<Integer, Integer>> immutablePostings = new HashMap<>();
        buildingPostings.forEach((term, frequencies) -> immutablePostings.put(term, Map.copyOf(frequencies)));
        postings = Map.copyOf(immutablePostings);
        averageLength = entries.isEmpty() ? 1.0 : Math.max(1.0, (double) totalLength / entries.size());
    }

    RetrievalResult retrieve(String question, int topK) {
        Map<Integer, Double> scores = new HashMap<>();
        for (String term : TOKENIZER.queryTerms(question)) {
            Map<Integer, Integer> matches = postings.get(term);
            if (matches == null) {
                continue;
            }
            double idf = Math.log1p((entries.size() - matches.size() + 0.5) / (matches.size() + 0.5));
            matches.forEach((id, frequency) -> {
                double lengthFactor = 1.0 - B + B * entries.get(id).length() / averageLength;
                double score = idf * frequency * (K1 + 1.0) / (frequency + K1 * lengthFactor);
                scores.merge(id, score, Double::sum);
            });
        }
        Comparator<Map.Entry<Integer, Double>> order = Comparator
                .<Map.Entry<Integer, Double>>comparingDouble(Map.Entry::getValue).reversed()
                .thenComparing(e -> entries.get(e.getKey()).source().document().toString())
                .thenComparingInt(e -> entries.get(e.getKey()).source().page())
                .thenComparingInt(e -> entries.get(e.getKey()).source().ordinal());
        List<RetrievedChunk> result = scores.entrySet().stream()
                .filter(e -> e.getValue() > 0 && Double.isFinite(e.getValue()))
                .sorted(order).limit(topK).map(e -> {
                    var chunk = entries.get(e.getKey()).source();
                    // Full real paths distinguish different documents with the same filename.
                    return new RetrievedChunk(chunk.document().toString(), chunk.location(), chunk.content(), e.getValue());
                }).toList();
        return result.isEmpty() ? RetrievalResult.empty() : new RetrievalResult(result);
    }
}
