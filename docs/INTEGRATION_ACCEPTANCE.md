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

- Standalone A/B/chat/import/HTTP suite: **109 passed, 0 failures/errors/skipped**.
- Full target IDEA 2026.2.3 (262.10968.63) suite: **145 passed, 0 failures/errors/skipped**, including 39 UI/worker cases. The 109 standalone cases are a subset of the full suite.
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

Actual provider availability, rendered IDEA interactions, light/dark appearance
and answer correctness remain **pending human acceptance**. Automated tests and
mock mode do not establish those results. Record the tested commit, IDE version,
provider/model, actual outcomes and screenshots for member D's final report.
