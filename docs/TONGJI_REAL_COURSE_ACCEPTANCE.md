# Tongji configuration and real-course acceptance

Date: 26 October 2026 (America/Chicago).
Integration branch: `integration/member-d-validation`.

Update: bilingual retrieval and project snapshot persistence were subsequently
implemented. See [the enhancement verification](BILINGUAL_PERSISTENCE_VERIFICATION.md)
for the expanded Chinese question set and post-change checks. The original
measurements below remain attributed to their earlier run.

## Configuration completed locally

| Setting | Value |
|---|---|
| Provider | Tongji |
| API base URL | `https://agi.tongji.edu.cn/v1` |
| Model identifier supplied by the user | `DeepSeek-V4.1-Flash` |
| JSON response mode | Enabled |
| Credentials | Current-user Windows DPAPI storage, outside Git |

Public settings are in `%LOCALAPPDATA%\CST3492Assistant\config.json`.
The protected key is in the same private profile directory as `tongji-key.dpapi`.
The launcher decrypts it only for the child process environment and restores
the caller's environment afterwards. No key is embedded in this document,
the scripts, source files, or result JSON. Normal IDE launches do not read this
profile: use the configured launcher below.

The remote service accepted the protected key and model identifier after the
network repair described below. Five real-model scenarios completed successfully
through the original Java acceptance runner.

## Actual uploaded corpus

The assignment specification was excluded. The runner imported only the eight
uploaded `Lec-*.pdf` lecture documents:

| Lecture | Physical pages | Extracted chunks |
|---|---|---|
| Java Fundamentals | 34 | 34 |
| OOP with Java | 32 | 32 |
| Exceptions | 11 | 11 |
| Generics and Collections | 17 | 17 |
| Modern Syntax, Annotations and Reflection | 16 | 16 |
| JUnit | 7 | 7 |
| AI-Assisted Java Development | 34 | 34 |
| Building AI-Powered Java Applications | 44 | 45 |
| **Total** | **195** | **196** |

These are the user's real course PDFs, not the earlier generated demonstration
documents. The extra chunk results from the existing page-bounded chunk size.

## Local retrieval measurements

Twenty English course questions, one Chinese course question and one question
with selected Java code were labelled with expected documents and physical pages.
A separate deliberately unmatched query tests the empty-result path.

- Expected reference page ranked first: **16/22 (72.7%)**.
- Expected reference page appeared in the top five: **21/22 (95.5%)**.
- Pure Chinese checked/unchecked-exceptions question: **zero hits** against
  these English lecture texts.
- Deliberately unmatched query: **zero hits**.

These are reference-page hit rates on a small manually selected set, not answer
accuracy or a statistically representative retrieval benchmark. Some questions
can have other valid supporting pages; the labels are not an exhaustive relevance
judgment. The code-context question sends code to generation but retrieval still
uses the question text alone.

Examples inspected against complete source pages:

| Question | First retrieved source |
|---|---|
| Difference between checked and unchecked exceptions | `Lec-3.1-Exceptions.pdf`, physical page 5 |
| Purpose of `@BeforeEach` and `@AfterEach` | `Lec-3.4-JUnit.pdf`, physical page 4 |
| Four steps of RAG | `Lec-5-Building-AI-Powered-Java-Applications.pdf`, physical page 23 |

Rankings, scores, timings and original text excerpts are archived in
[the machine-readable real-course record](verification/tongji-real-course-results.json).

## Real-provider result: successful after network repair

The initial attempts were blocked:

1. PowerShell requests to the supplied endpoint returned **HTTP 403** with
   `当前网络环境不支持` (current network environment is unsupported).
2. The production Java HTTP client using its default direct route timed out
   connecting after approximately 30 seconds.
3. The opt-in Java acceptance process was retried using the current Windows
   system proxy. The connection failed with **`Tunnel failed, got: 502`**.

The connected MotionPro VPN used local DNS at `127.0.0.1`, which resolved the
API to campus addresses `10.67.0.251` and `10.67.0.252`. Ordinary resolution
preferred the WLAN DNS configuration and returned unreachable public addresses.
The configured Windows proxy was also owned by 365VPN and failed its CONNECT
request. See [the diagnosis and reversible repair](TONGJI_NETWORK_DIAGNOSIS.md).

With Windows administrator approval, the connected MotionPro IPv4 interface
metric was changed from automatic 25 to manual 1, and the exact API hostname was
added to the current user's proxy exceptions. Original settings were backed up
outside Git. An ordinary DNS lookup then returned campus addresses. The original
Java runner was repeated without a special IP override or altered TLS settings:
**BUILD SUCCESSFUL**, **liveStatus COMPLETE**, five answers, no mock service.

| Live scenario | Time | Result and displayed sources |
|---|---|---|
| Checked versus unchecked exceptions | 6.40 s | Answer; Exceptions PDF, pages 5 and 6 |
| JUnit lifecycle | 2.34 s | Answer; JUnit PDF, page 4 |
| Four steps of RAG | 2.49 s | Answer; AI-Powered Java Applications PDF, page 23 |
| Selected Java code and checked exception | 5.79 s | Explains the declaration and caller obligation; Exceptions PDF, pages 4 and 5 |
| Deliberately unmatched term | 3.35 s | No sources; explicit general-knowledge disclaimer |

The responses were inspected against the retrieved excerpts and reference pages.
Core explanations are consistent with them. The exceptions answer adds a broad
generalization that unchecked exceptions signal programming bugs; that wording
does not cover all `Error` cases and should not be treated as a perfect answer.
This small sample verifies working generation and grounded source selection,
not exhaustive factual accuracy. Native IDE interaction was not automated here.

The full post-repair record is in
[the live-course result](verification/tongji-live-course-results-2026-10-26.json).
Earlier blocked attempts remain archived in the network retry record.

## Reproduce using the saved local profile

Run from the checkout:

```powershell
# Check public settings and protected-key availability without displaying the key.
.\scripts\run-tongji.ps1 -Mode Check

# No model request: import the real PDFs and measure retrieval.
.\scripts\run-tongji.ps1 -Mode Retrieve

# With the campus VPN connected: five real-model scenarios, stopping on errors.
.\scripts\run-tongji.ps1 -Mode Live

# Start the actual IDEA sandbox with the configured model environment.
.\scripts\run-tongji.ps1 -Mode RunIde
```

`-CourseDirectory` can select another folder of `Lec-*.pdf` files.
The existing Tool Window still requires selecting course attachments and sending
a question to import them; the CLI acceptance runner imports the corpus itself.

The live and retrieval outputs are separate files under
`integration-standalone/build/reports/real-course/`, so a local-only run does not
overwrite a live failure/answer record. The live check exercises the real existing
factory, retrieval service, prompt, HTTP client, parser and source adapter. It
does not automate the native editor or file chooser.

## Regression verification

- Standalone integration suite: **149 passed, 0 failures/errors/skipped**.
- Full target-IDE suite: **215 passed, 0 failures/errors/skipped**.
- Plugin packaging and structure verification: passed; production plugin code
  and the existing packaged plugin are unchanged by this configuration/test pass.
- The launcher was exercised from the local profile; retrieval mode restored
  both the caller directory and its previous environment.
- Three isolated PowerShell launcher-state tests pass, covering absent settings,
  existing settings and a failed child process. The absent-key test first failed
  before correcting Windows/.NET null-to-empty restoration behavior. These tests
  use a test-only protected value and a fake launcher, with no network calls.
- Independent read-only review found no actionable findings in the launcher,
  runner and opt-in task. That review did not inspect private profile contents
  or claim live connectivity.

The five live checks and the three launcher-state tests were repeated after the
network repair. The deterministic Java suite counts above are from the earlier
code verification; no production Java code changed in the network repair.
