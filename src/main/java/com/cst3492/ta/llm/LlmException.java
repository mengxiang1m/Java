package com.cst3492.ta.llm;

/**
 * Thrown when the LLM endpoint cannot be reached, returns a non-success status,
 * or returns an unparseable response.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
