package com.cst3492.assistant.chat;

import java.util.List;
import java.util.Objects;

/** Structured service result. An empty source list alone does not mean an error. */
public record ChatReply(String text, Status status, List<CourseSource> sources) {
    public enum Status { MOCK, NORMAL, NO_SOURCES, ERROR }

    public ChatReply {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(status, "status");
        sources = List.copyOf(sources);
        if (text.isBlank()) {
            throw new IllegalArgumentException("A reply needs displayable text");
        }
        if ((status == Status.NO_SOURCES || status == Status.ERROR) && !sources.isEmpty()) {
            throw new IllegalArgumentException("NO_SOURCES and ERROR replies cannot cite sources");
        }
        if (status == Status.MOCK && sources.stream().anyMatch(source -> !source.testData())) {
            throw new IllegalArgumentException("Mock sources must be marked as test data");
        }
    }
}
