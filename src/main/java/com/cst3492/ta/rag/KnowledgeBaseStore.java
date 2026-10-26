package com.cst3492.ta.rag;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Versioned local snapshots. Never uses Java object deserialization. */
final class KnowledgeBaseStore {
    // Restore must wait for an old writer's final atomic move, even across project instances.
    // The lock is only taken on background operations; disposal never waits for it.
    private static final Object DISK_GATE = new Object();
    private static final int MAGIC = 0x4353544B;
    private static final int VERSION = 1;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int MAX_DOCUMENTS = 10_000;
    private static final int MAX_CHUNKS = 500_000;
    record Document(IngestionReport report, List<PageChunker.Chunk> chunks) { }

    static List<Document> read(Path file) throws IOException {
        synchronized (DISK_GATE) { return readSnapshot(file); }
    }

    private static List<Document> readSnapshot(Path file) throws IOException {
        if (Files.notExists(file)) return List.of();
        if (Files.size(file) > MAX_BYTES) throw new IOException("Knowledge snapshot exceeds 64 MiB");
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unknown knowledge snapshot format; existing file was preserved");
            }
            int count = bounded(input.readInt(), MAX_DOCUMENTS);
            List<Document> documents = new ArrayList<>();
            Set<Path> identities = new HashSet<>();
            int totalChunks = 0;
            for (int i = 0; i < count; i++) {
                Path path = Path.of(input.readUTF());
                if (!path.isAbsolute() || !identities.add(path)) throw new IOException("Invalid document identity");
                int pages = bounded(input.readInt(), 1_000_000);
                int chunkCount = bounded(input.readInt(), MAX_CHUNKS - totalChunks);
                totalChunks += chunkCount;
                List<PageChunker.Chunk> chunks = new ArrayList<>();
                Set<String> positions = new HashSet<>();
                for (int j = 0; j < chunkCount; j++) {
                    boolean slides = input.readBoolean();
                    int page = input.readInt();
                    int ordinal = bounded(input.readInt(), MAX_CHUNKS);
                    String text = input.readUTF();
                    if (page < 1 || page > pages || text.isBlank()
                            || text.codePointCount(0, text.length()) > PageChunker.MAX_CODE_POINTS
                            || !positions.add(page + ":" + ordinal)) {
                        throw new IOException("Invalid knowledge chunk");
                    }
                    chunks.add(new PageChunker.Chunk(path, slides, page, ordinal, text));
                }
                documents.add(new Document(new IngestionReport(path, path.toString(), pages, chunkCount), List.copyOf(chunks)));
            }
            if (input.read() != -1) throw new IOException("Unexpected trailing snapshot data");
            return List.copyOf(documents);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid knowledge snapshot", exception);
        }
    }

    static void write(Path file, Collection<Document> documents, java.util.function.BooleanSupplier active) throws IOException {
        synchronized (DISK_GATE) {
            checkActive(active);
            writeSnapshot(file, documents, active);
        }
    }

    private static void writeSnapshot(Path file, Collection<Document> documents,
                                      java.util.function.BooleanSupplier active) throws IOException {
        bounded(documents.size(), MAX_DOCUMENTS);
        bounded(documents.stream().mapToInt(d -> d.chunks().size()).sum(), MAX_CHUNKS);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "knowledge-", ".tmp");
        try {
            try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(documents.size());
                for (Document document : documents) {
                    output.writeUTF(document.report().path().toString());
                    output.writeInt(document.report().pageCount());
                    output.writeInt(document.chunks().size());
                    for (var chunk : document.chunks()) {
                        output.writeBoolean(chunk.slides());
                        output.writeInt(chunk.page());
                        output.writeInt(chunk.ordinal());
                        output.writeUTF(chunk.content());
                    }
                }
            }
            if (Files.size(temporary) > MAX_BYTES) throw new IOException("Knowledge snapshot exceeds 64 MiB");
            checkActive(active);
            // Fail safely on file systems without atomic replacement; do not discard the old snapshot.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void checkActive(java.util.function.BooleanSupplier active) {
        if (!active.getAsBoolean()) throw new java.util.concurrent.CancellationException("Knowledge snapshot write cancelled");
    }

    private static int bounded(int value, int maximum) throws IOException {
        if (value < 0 || value > maximum) throw new IOException("Invalid or excessive knowledge snapshot count");
        return value;
    }
}
