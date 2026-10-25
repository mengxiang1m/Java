package com.cst3492.ta.rag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed, overlapping Unicode windows, each tied to exactly one physical page. */
final class PageChunker {
    static final int MAX_CODE_POINTS = 1_000;
    static final int OVERLAP_CODE_POINTS = 150;

    record Chunk(Path document, boolean slides, int page, int ordinal, String content) {
        String location() {
            return slides ? "slide " + page : "p." + page;
        }
    }

    List<Chunk> split(Path path, DocumentExtractor.Document document) {
        List<Chunk> chunks = new ArrayList<>();
        for (var page : document.pages()) {
            int[] points = page.text().replace("\r\n", "\n").replace('\r', '\n').strip().codePoints().toArray();
            int ordinal = 0;
            for (int start = 0; start < points.length; start += MAX_CODE_POINTS - OVERLAP_CODE_POINTS) {
                int count = Math.min(MAX_CODE_POINTS, points.length - start);
                String content = new String(points, start, count).strip();
                if (!content.isBlank()) {
                    chunks.add(new Chunk(path, document.slides(), page.number(), ordinal++, content));
                }
                if (start + count == points.length) {
                    break;
                }
            }
        }
        return List.copyOf(chunks);
    }
}
