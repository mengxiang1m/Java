# Member D integration and verification plan

**Goal:** Verify and repair the merged F0 implementation before preparing the final report and presentation.

**Scope:** Preserve the current Java/IntelliJ architecture, real document parsers, BM25 retrieval, provider configuration and source-validation contract. Work locally on `integration/member-d-validation`. Do not push to the shared repository during verification.

## Acceptance criteria

- The existing LLM and UI branches are included in the integration baseline.
- Standalone builds compile the actual production sources and their intended tests.
- The project-owned knowledge base reaches the HTTP request, answer parser and UI adapter.
- PDF/PPTX/PPT source locations, general-knowledge fallback and selected code are verified.
- Invalid files, failed imports, provider errors, cancellation and project disposal are exercised.
- Test results distinguish deterministic HTTP fixtures, live-provider calls and actual IDE interaction.

## Tasks

- [x] Clone and inspect the latest main branch; verify feature-branch ancestry.
- [x] Provision a checksum-verified target IDE with full JDK 25 and run unchanged standalone baseline builds (both reproduced compilation defects).
- [x] Repair reproducible source-set and interface defects, with seven failing regression cases observed before behavior fixes.
- [x] Run standalone RAG and integration suites, target-IDE compilation/tests, packaging and structure checks. Binary verifier dependency download timed out; recorded as incomplete.
- [x] Exercise document-to-answer success and failure scenarios; record measured results and remaining limitations in English.
- [x] Review the final changes and deliver a reviewable local branch and verification record.

## Initial findings

- Both `origin/feature/llm` and `origin/feature/ui-context` are ancestors of `437d376`; another blind merge is unnecessary.
- `ProjectKnowledgeBaseIntegrationTest` is under the RAG test package but depends on chat, LLM and project-knowledge sources. The RAG-only build includes that test despite excluding those production sources.
- The integration-only build includes the same test but excludes `ProjectKnowledgeBase` from its main source set.
- The current host's global JAVA_HOME is invalid. Local tooling will be configured per process without changing global settings.

## Execution record

Baseline and regression results will be recorded in `docs/MEMBER_D_VERIFICATION.md`. Final report/slide authoring follows this verification stage, as requested.
