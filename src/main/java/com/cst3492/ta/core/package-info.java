/**
 * Shared data model: the contract passed between the RAG, LLM and UI modules.
 *
 * <p>Intended types:</p>
 * <ul>
 *   <li>{@code RetrievedChunk} / {@code RetrievalResult} — the retrieval output.</li>
 *   <li>{@code AssistantQuery} — question + editor context + retrieval result.</li>
 *   <li>{@code AssistantAnswer} / {@code Citation} — the generated answer and its sources.</li>
 * </ul>
 */
package com.cst3492.ta.core;
