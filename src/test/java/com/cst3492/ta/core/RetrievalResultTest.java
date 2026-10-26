package com.cst3492.ta.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetrievalResultTest {
    @Test
    void callerMutationCannotChangeThePromptAndCitationSnapshot() {
        var chunk = new RetrievedChunk("Lecture.pdf", "p.2", "JDBC transactions", 1.0);
        var callerOwned = new ArrayList<>(List.of(chunk));
        var result = new RetrievalResult(callerOwned);

        callerOwned.clear();

        assertEquals(List.of(chunk), result.getChunks());
        assertTrue(result.isRelevant());
        assertThrows(UnsupportedOperationException.class, () -> result.getChunks().clear());
    }
}
