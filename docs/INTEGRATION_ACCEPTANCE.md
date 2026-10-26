# A/B/C integration acceptance

## Implemented flow

The Tool Window owns one in-memory knowledge base shared by document import and
chat. Selected PDF/PPTX/PPT files are imported on a SwingWorker. Each question
retrieves a fresh snapshot, then sends the question, exact editor context and
retrieved text through the existing LLM module. Only validated source identities
and physical page/slide locations are mapped into UI citations.

Imports accept up to 50 MiB per file. Failures are reported individually; valid
files in the same batch remain available. Empty documents are distinguished from
parse failures. Drafts survive import, overlapping operations are guarded, and
disposed Tool Windows suppress late callbacks. Restarting requires reimport.

## Automated evidence

- Standalone A/B/chat/import/HTTP suite: **120 passed, 0 failures/errors/skipped**.
- Full target IDEA 2026.2.3 (262.10968.63) suite: **156 passed, 0 failures/errors/skipped**, including 39 UI/worker cases. The 120 standalone cases are a subset of the full suite.
- HTTP integration uses a localhost server and the real HTTP client, prompt
  renderer, parser and citation adapter. PDF, PPTX and legacy PPT are exercised.
- Successive questions, later imports/removals, exact selected code, fabricated
  citations, no-match disclaimers, HTTP errors, provider configuration and legacy
  DeepSeek credentials are covered. No live API credentials are used by default.
- Target IDEA 262: `build`, `verifyPluginStructure` and `verifyPluginProjectConfiguration` succeeded. Configuration checking retained two pre-existing warnings: Java 21 output below platform Java 25, and the explicit `262.*` upper compatibility bound. This is not a warning-free result. The separate binary Plugin Verifier was not run.
- Linux IDEA was downloaded from JetBrains and its archive SHA-256 verified. A full JDK 25 runs Gradle; source and bytecode remain Java 21.
- Plugin ZIP packaging: final artifact checked after `buildPlugin`.

Reproduce the independent checks and generated demonstration files:

```bash
bash scripts/gradle.sh -p integration-standalone --no-daemon test prepareAcceptanceMaterials
```

Reports: `integration-standalone/build/reports/tests/test/index.html`.
Demo files: `integration-standalone/build/acceptance-materials/`.

## Provider configuration

| Environment variable | Meaning |
|---|---|
| `CST3492_LLM_PROVIDER` | `deepseek` (default), `tongji`, or `custom` |
| `CST3492_LLM_API_KEY` | Secret for the selected provider, supplied in process environment |
| `CST3492_LLM_BASE_URL` | Compatible API base URL, without `/chat/completions` |
| `CST3492_LLM_MODEL` | Model ID available to the account |
| `CST3492_LLM_JSON_MODE` | Optional override; false disables JSON mode for an incompatible endpoint |
| `DEEPSEEK_API_KEY` | Legacy fallback, used only with DeepSeek |
| `CST3492_CHAT_MODE=mock` | Explicit local mock mode |

Tongji/custom require explicit base URL and model. Missing credentials select
mock mode; incomplete provider configuration is reported without exposing secrets.
The assignment lists both Tongji and DeepSeek and encourages free services.

## Human acceptance and delivery

Run `bash scripts/manual-acceptance.sh` and follow
[the Chinese operator guide](MANUAL_ACCEPTANCE.zh-CN.md). The operator must log in,
select an account-authorized model and enter their key locally. Public setup
values are stored under the user's configuration directory; API keys are not saved.

Automated tests and mock mode do not establish live-provider or visual results.
Operator-reported checks are recorded below. Their screenshots have now been
collected and inspected; [the presentation evidence](PRESENTATION_EVIDENCE.md)
contains images and English captions for member D's report and slides.

## Operator-reported live checks, 25 October 2026

The operator initially tested commit `e8fa724` on Linux in the IDEA sandbox, through the
Tongji endpoint `https://agi.tongji.edu.cn/v1`, with configured model name
`DeepSeek-V4.1-Flash`. This is the platform's model identifier; no API key is
recorded here. The final selected-code scenario was repeated after restarting
with the fix included in `918d9dc`. Results combine the operator's reports with
inspection of their captured UI regions; they were not produced by automated
desktop interaction.

| Scenario | Reported evidence | Status |
|---|---|---|
| Course question: JDBC transaction rollback | Operator confirmed the answer; screenshots show the question and PDF/PPT/PPTX sources at physical page/slide 2 | Passed; evidence collected |
| Unmatched question: java.time.LocalDate formatting | Screenshot shows the exact disclaimer; operator confirmed no Sources list | Passed; evidence collected |
| Question about selected Demo.java method | Post-fix screenshot shows attached code and the correct commit/rollback branches, without the earlier unverified reference | Passed after restart; evidence collected |
| Native layout, focus, light/dark themes | Not yet reported | Pending |

The selected-code answer also exposed a display mismatch: an unverified `[1]`
remained next to a general-knowledge disclaimer. The parser now removes such
references when the answer cannot be grounded, preserving code and valid course
answers. Three new regression tests first reproduced the mismatch, then passed;
the complete 148-test target suite and plugin packaging/structure checks passed.
The operator restarted, repeated the selected-code question and supplied the
final screenshot. Its visible answer no longer contains the earlier unsupported
reference. The archived images are unchanged copies of the operator's captures.
The source-list and general-answer crops do not show every part of the original
conversation; the operator's confirmations provide the remaining context.

## Real course follow-up, 26 October 2026

The operator reported an exceptions answer that described the course definitions
but ended with the general-knowledge disclaimer. Raw model payloads were not
captured, so the exact rejected response shape cannot be established retrospectively.
The old integration only requested JSON in the prompt; invalid JSON or unverified
document/page metadata caused a general fallback.

An offline reproduction with all nine supplied PDFs imported 199 physical pages
and 201 chunks. The checked/unchecked-exceptions question returned five hits;
`Lec-3.1-Exceptions.pdf` pages 5 and 6 ranked first and second. This verifies local
retrieval, without a live model call, but cannot establish the earlier GUI session's
knowledge-base contents.

The fix requests `response_format: {"type":"json_object"}` for Tongji/DeepSeek.
The model returns `citations: [{"sourceId":1}]`; the parser derives actual paths
and pages from the supplied chunks, deduplicates sources and renumbers references.
Legacy metadata citations remain supported. Custom endpoints default to JSON mode
off; `CST3492_LLM_JSON_MODE` can override the setting. See the provider's
[JSON output guidance](https://api-docs.deepseek.com/guides/json_mode).

Safe diagnostics record retrieval/validation counts, HTTP status and completion
reason, without prompts, full responses or API keys. All 120 standalone and 156
target-IDE tests pass; packaging and structure verification succeed.

The operator subsequently confirmed that the updated `c08dadb` sandbox displays
course Sources for the real-material question. Status: **passed, operator-confirmed**;
the final real-course screenshot is still pending collection. The source locations
were checked against the requested exceptions page 5/6 scenario; the new image
will provide the archived visual evidence.

Safe runtime diagnostics independently show the 26 October request used five
retrieved chunks, five prompt chunks and JSON mode, and completed with
`finishReason=stop` (1,623 response characters). No raw response or credential was
read or added to the repository.
