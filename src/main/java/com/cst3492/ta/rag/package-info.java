/**
 * Retrieval-augmented generation (RAG) pipeline.
 *
 * <p>{@link com.cst3492.ta.rag.KnowledgeBaseService} imports PDF/PPTX/PPT,
 * splits text into overlapping page-local chunks, and retrieves relevant chunks
 * with a bilingual keyword index and BM25. Results use the shared core contracts
 * consumed by the LLM and UI. The service has no IntelliJ or LLM dependency.</p>
 */
package com.cst3492.ta.rag;
