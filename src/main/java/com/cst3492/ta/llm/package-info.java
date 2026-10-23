/**
 * LLM integration &amp; answer generation.
 *
 * <p>Responsibility: build a prompt from the question + editor context +
 * retrieved chunks, call an OpenAI-compatible LLM endpoint (DeepSeek / Tongji),
 * parse the structured response, and enforce the source-citation /
 * general-knowledge contract.</p>
 */
package com.cst3492.ta.llm;
