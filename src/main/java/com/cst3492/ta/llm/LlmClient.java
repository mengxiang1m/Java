package com.cst3492.ta.llm;

/**
 * Low-level chat client: sends a rendered {@link Prompt} to an LLM endpoint and
 * returns the assistant's raw text content (the JSON string we ask the model to
 * emit).
 *
 * <p>The rest of the module depends only on this interface, never on HTTP, so
 * it can be faked in unit tests and swapped between providers.</p>
 */
@FunctionalInterface
public interface LlmClient {

    /**
     * Completes a prompt and returns the assistant message content.
     *
     * @throws LlmException on transport errors, non-2xx responses, or an
     *                      unparseable response body
     */
    String complete(LlmConfig config, Prompt prompt);
}
