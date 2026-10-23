/**
 * Retrieval-augmented generation (RAG) pipeline.
 *
 * <p>Responsibility: pre-process course materials (e.g. lecture slides), split
 * them into searchable knowledge chunks, index them, and retrieve the most
 * relevant chunks for a query. Produces the retrieval result consumed by the
 * {@code llm} module.</p>
 */
package com.cst3492.ta.rag;
