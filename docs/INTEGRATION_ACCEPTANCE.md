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

- Standalone A/B/chat/import/HTTP suite: **112 passed, 0 failures/errors/skipped**.
- Full target IDEA 2026.2.3 (262.10968.63) suite: **148 passed, 0 failures/errors/skipped**, including 39 UI/worker cases. The 112 standalone cases are a subset of the full suite.
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
Operator-reported checks are recorded below; screenshots and final visual review
remain outstanding for member D's report.

## Operator-reported live checks, 25 October 2026

The operator tested commit `e8fa724` on Linux in the IDEA sandbox, through the
Tongji endpoint `https://agi.tongji.edu.cn/v1`, with configured model name
`DeepSeek-V4.1-Flash`. This is the platform's model identifier; no API key is
recorded here. The following results are the operator's reports, not observations
from an automated browser or desktop session.

| Scenario | Reported evidence | Status |
|---|---|---|
| Course question: JDBC transaction rollback | Answer quotes the imported Demo text; operator confirmed three source files and physical page/slide 2 | Passed; screenshot pending |
| Unmatched question: java.time.LocalDate formatting | Exact general-knowledge disclaimer shown; operator confirmed no Sources list | Passed; screenshot pending |
| Question about selected Demo.java method | Answer correctly says success=true commits and success=false rolls back | Functional result confirmed; citation-display recheck pending |
| Native layout, focus, light/dark themes | Not yet reported | Pending |

The selected-code answer also exposed a display mismatch: an unverified `[1]`
remained next to a general-knowledge disclaimer. The parser now removes such
references when the answer cannot be grounded, preserving code and valid course
answers. Three new regression tests first reproduced the mismatch, then passed;
the complete 148-test target suite and plugin packaging/structure checks passed.
The operator must restart the sandbox, reimport the Demo documents and repeat
the selected-code question before the final screenshot is accepted.
