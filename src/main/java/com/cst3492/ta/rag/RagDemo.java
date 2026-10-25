package com.cst3492.ta.rag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Usage: RagDemo <document-path> <question> [topK]. No LLM request is made. */
public final class RagDemo {
    private RagDemo() { }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: RagDemo <document-path> <question> [topK]");
            System.exit(2);
        }
        try {
            int topK = args.length == 3 ? Integer.parseInt(args[2]) : KnowledgeBaseService.DEFAULT_TOP_K;
            if (topK <= 0) {
                throw new IllegalArgumentException("topK must be positive");
            }
            KnowledgeBaseService knowledge = new KnowledgeBaseService();
            var report = knowledge.ingest(Path.of(args[0]));
            System.out.printf("Imported %s: %d pages/slides, %d chunks%n",
                    report.documentName(), report.pageCount(), report.chunkCount());
            if (!report.hasExtractableText()) {
                System.out.println("No extractable text. Scanned images require OCR, which is not included.");
            }
            var result = knowledge.retrieve(args[1], topK);
            if (result.isEmpty()) {
                System.out.println("No relevant course material found.");
            }
            for (int i = 0; i < result.getChunks().size(); i++) {
                var chunk = result.getChunks().get(i);
                System.out.printf(Locale.ROOT, "[%d] %s, %s (BM25 %.4f)%n%s%n%n",
                        i + 1, chunk.getDocumentName(), chunk.getPageRange(), chunk.getScore(), chunk.getContent());
            }
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Import/search failed: " + exception.getMessage());
            System.exit(1);
        }
    }
}
