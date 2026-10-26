# Member D: integration and verification

Verification date: 25 October 2026 (America/Chicago).
Upstream baseline: `437d37658d791dce31945b1550ccd8746974b6bf`.
Local branch: `integration/member-d-validation`.

## Scope and integration status

Both `origin/feature/llm` and `origin/feature/ui-context` are ancestors of the
baseline main branch. No additional feature-branch merge was necessary. This
pass repaired and verified the already-merged modules without adding F1/F2.

The production flow is:

1. The Tool Window obtains the project's `KnowledgeBaseProjectService`.
2. Send snapshots the question, exact selected Java code and pending attachments.
3. The background request imports selected documents into `ProjectKnowledgeBase`.
4. `LlmChatService` retrieves a fresh top-five result for the question.
5. `AnswerGenerationService` combines the question, code and retrieved chunks.
6. `OpenAiCompatibleClient` sends an OpenAI-compatible HTTP request.
7. `AnswerParser` resolves source IDs against the prompt's retrieval snapshot.
8. The UI displays the answer, validated document/page references and excerpts.

The parser checks source identities, not the semantic truth of every claim.
General answers carry the required disclaimer. Retrieval failures remain errors
rather than being silently represented as an empty knowledge base.

## Defects reproduced and repaired

| Defect | Reproduction | Repair |
|---|---|---|
| RAG-only build compiled a cross-module test without its dependencies | Unmodified `rag-standalone test` failed in `compileTestJava` with 41 errors | Keep core/RAG tests in the A-only build; run the project/chat test in the integration and full suites |
| Integration build omitted project-knowledge production code | Unmodified `integration-standalone test` failed in `compileTestJava` with 19 errors | Include the actual production `ProjectKnowledgeBase` source in its source set |
| Retrieval results exposed a mutable caller-owned list | Clearing the original list changed the supposedly immutable result | Copy the list at the core interface boundary |
| Blank HTTP completions were treated as successful general answers | Empty, space-only and newline-only content did not throw | Reject blank completion text as `LlmException`, reaching the existing UI error boundary |

The focused regression run executed 31 cases before the two behavior fixes:
seven failed as expected (one mutable-snapshot case and three blank-content
cases at each of the client and HTTP integration boundaries). The same cases
pass after repair.

## Toolchain and dependencies

- Windows; IntelliJ IDEA 2026.2.3, build `262.10968.63`.
- Bundled full JetBrains JDK/JBR 25.0.4; source/bytecode target remains Java 21.
- Gradle 9.6.0. IDE and Gradle archives were checked against their published
  SHA-256 checksums. Global JAVA_HOME was not changed.
- PDFBox 3.0.8 and Apache POI 6.0.0 are shared through
  `gradle/rag-dependencies.gradle.kts`, with JUnit BOM 5.13.4.
- Gson 2.10.1 is compile-only in the plugin and supplied by the IDE at runtime;
  standalone integration supplies its own Gson. The packaged plugin contains
  no extra Gson JAR.
- Runtime dependency resolution selected one commons-codec 1.23.0 and one
  commons-io 2.22.0 despite lower transitive requests. The resolved graph is
  archived in [runtime-dependencies.txt](verification/runtime-dependencies.txt).

## Results measured in this pass

| Check | Result |
|---|---|
| Standalone core/RAG tests | 39 passed; 0 failures, errors or skipped cases |
| Standalone cross-module/HTTP tests | 147 passed; 0 failures, errors or skipped cases |
| Full target-IDE suite | 213 passed; 0 failures, errors or skipped cases |
| `build` and `buildPlugin` | Passed |
| `verifyPluginStructure` | Passed |
| `verifyPluginProjectConfiguration` | Completed with the two existing warnings below |
| Binary `verifyPlugin` | Not completed: dependency resolution failed with a network connection timeout |
| Independent read-only change review | No actionable findings in the reviewed changes |

These suites overlap. Do not add 39, 147 and 213 and describe that sum as unique
tests. Counts were collected from generated JUnit XML, not copied from historical
acceptance notes. The machine-readable record is
[member-d-results.json](verification/member-d-results.json).

The two retained configuration warnings are the Java 21 output target below the
platform's Java 25 level, and the explicit `262.*` upper compatibility bound.
The complete plugin compiles and tests against the specified target; compatibility
with other IDE branches is not established.

## End-to-end and exception coverage

The new target-IDE component tests exercise the actual chain from attaching a
PDF/PPTX/PPT and pressing Send to project-owned import/retrieval, a real localhost
HTTP request, JSON parsing, and rendered answer/source cards. They verify exact
selected code, document identity, physical page/slide 1, original excerpts and
restored controls. The localhost response is deterministic test data, not a live
model answer. The native file picker and editor right-click event are not driven
by these component tests.

The HTTP suite additionally exercises physical page/slide 2 after a blank first
page, Unicode paths, source IDs, fabricated citations, no-match disclaimers,
provider setup and legacy DeepSeek credentials. HTTP 401, 403, 429, 500 and 503,
and empty HTTP 200 completions are checked as errors.

Existing tests, rerun in this pass, cover corrupt/encrypted/unreadable/unsupported
files, empty documents, replacement/removal, failed reimport preserving the old
index, concurrent imports, cancellation, duplicate sends, worker disposal,
closed-project callbacks, exact code snapshots and retry behavior.

Two generated offscreen UI images (340 and 760 pixels wide) were inspected.
They show readable wrapping and visible composer controls, but are explicitly
mock fixture views. They do not establish native theme/focus behavior or live
provider success.

## Small retrieval-effectiveness check

Each format used a generated five-page document with one blank page and four
topics: JDBC, servlet lifecycle, checked exceptions and dependency injection.
Eight lexical questions per format were checked against labelled physical pages.

| Format | Correct top-one page | Import time | Median retrieval time | Maximum retrieval time |
|---|---|---|---|---|
| PDF | 8/8 | 14.210 ms | 0.102 ms | 0.128 ms |
| PPTX | 8/8 | 29.857 ms | 0.098 ms | 0.185 ms |
| PPT | 8/8 | 11.804 ms | 0.091 ms | 0.154 ms |

The deliberately unmatched token returned zero hits. A Chinese query for
dependency injection against the English-only fixture also returned zero hits
in all three formats. This exposes a lexical/cross-language limitation rather
than a translated semantic search capability.

These are small fixture measurements on this workstation, not real-course
accuracy, a production latency guarantee, or LLM answer-quality measurements.
The original per-question measurements are retained in the JSON record.

## Reproduce on Windows

```powershell
.\scripts\verify.ps1 -JdkPath 'C:\path\to\jdk-25'
.\scripts\verify.ps1 -JdkPath 'C:\path\to\jdk-25' -IncludeIde -IdePath 'C:\path\to\IntelliJ IDEA 2026.2'
```

The script restores JAVA_HOME and the caller's directory. Use `-GradleHome` for a
local Gradle 9.6.0 installation, otherwise it uses the repository Wrapper.

Reports:

- `rag-standalone/build/reports/tests/test/index.html`
- `integration-standalone/build/reports/tests/test/index.html`
- `build/reports/tests/test/index.html`
- `integration-standalone/build/reports/retrieval/{pdf,pptx,ppt}.json`
- `build/reports/ui/assistant-{340,760}.png`

The packaged plugin is `build/distributions/cst3492-ta-0.1.0.zip` (23,360,188 bytes).
Its SHA-256 is
`efb23a733e2925f0881fe3e00bbbd68b90230ffb0718f0f48e4192a0e16d04ff`.
Generated demonstration inputs are under
`integration-standalone/build/acceptance-materials/`.

## Remaining acceptance and delivery work

- No live provider was called in this pass: this host has no configured API key,
  endpoint/model or supplied real-course corpus. Historical live screenshots
  remain separately attributed in `PRESENTATION_EVIDENCE.md`.
- Run the final installed plugin with the team's real course materials and
  account-authorized provider, including right-click, focus and light/dark themes.
- The index remains in memory. Previously imported attachment paths use cached
  content until project close; explicit refresh/removal is not exposed in chat.
- Search uses the question text only; selected code is sent to the LLM but does
  not augment retrieval. Cross-language/synonym retrieval and total-library
  memory limits are not implemented.
- Complete the binary verifier once its dependency can be downloaded.
- Prepare the final English report/slide PDFs, demo script, Q&A and submission ZIP
  after this integration/testing stage. No remote branch was changed by this pass.
