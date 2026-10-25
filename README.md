# CST3492 Assistant

## Module A: document ingestion and keyword retrieval

`com.cst3492.ta.rag.KnowledgeBaseService` is a standalone, in-memory service.
It imports **PDF, PPTX and legacy PPT**, indexes course knowledge chunks, and
returns the existing `RetrievedChunk` / `RetrievalResult` contracts. No IntelliJ
instance, API key, LLM call, external search server, or course-file download is
needed to test it. The current chat factory does not yet call this service;
the UI team can integrate it using the example below.

### Run A's tests without IntelliJ

Use a full JDK 25, as required by the existing Gradle Wrapper. Our source still
targets Java 21 (`--release 21`). Set variables only in the current PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat -p rag-standalone test
```

The independent build compiles the **same core/RAG source files** used by the
plugin. Parser versions and JUnit dependencies are shared through
`gradle/rag-dependencies.gradle.kts`. Gradle and Maven dependencies are downloaded
on the first run; after caching them, `--offline` can be added. The HTML test
report is `rag-standalone/build/reports/tests/test/index.html`.

On Windows the standalone Gradle daemon uses `file.encoding=COMPAT` so its JVM
argument files match the launcher's native encoding, including Chinese user and
project paths. Java source compilation and test content explicitly use UTF-8.
No system locale or global environment configuration is changed.

Tests generate small real PDF/PPTX/PPT documents in temporary directories. They
cover original page numbers, blank pages, nested groups/tables, Chinese/English,
Unicode chunk boundaries, BM25 ranking, top-k, no matches, invalid inputs,
encrypted/corrupt files, replacement, removal, immutable results and concurrent
imports. They do not require a live LLM or mutate personal course documents.

### Try a real document

From the repository folder, after setting `JAVA_HOME`:

```powershell
.\gradlew.bat --% -p rag-standalone run --args="\"C:/course/Lecture.pdf\" \"JDBC transaction\" 5"
.\gradlew.bat --% -p rag-standalone run --args="\"C:/course/Lecture.pptx\" \"数据库连接\""
```

PowerShell's `--%` preserves the nested quotes needed by the Windows batch wrapper,
including questions and paths with spaces. Replace the example paths with real
files. `build` also creates a runnable distribution ZIP under
`rag-standalone/build/distributions/`, with a launcher and its parser dependencies.

Arguments are one document path, one question, and an optional positive `topK`
(default 5). Output includes the import counts, original document identity,
physical page/slide, BM25 score and full chunk text. No match prints
`No relevant course material found.`; an import error exits unsuccessfully with
an explanation. Documents without extractable text produce a zero-chunk report.

### API and integration with B/C

```java
KnowledgeBaseService knowledge = new KnowledgeBaseService();
IngestionReport report = knowledge.ingest(Path.of("C:/course/Lecture.pdf"));
knowledge.ingest(Path.of("C:/course/Database.pptx"));

RetrievalResult retrieval = knowledge.retrieve(question); // default top five
// Or: knowledge.retrieve(question, 3)
AssistantQuery query = new AssistantQuery(question, selectedCode, retrieval);
AssistantAnswer answer = generationService.generate(query);

boolean removed = knowledge.removeDocument(report.path());
```

`ingest` returns `IngestionReport(path, documentName, pageCount, chunkCount)`;
`hasExtractableText()` distinguishes an empty/scanned file from extracted text.
Import errors throw `DocumentIngestionException` with `getReason()` equal to
`UNSUPPORTED_FORMAT`, `UNREADABLE_FILE`, `ENCRYPTED_DOCUMENT`, or
`MALFORMED_DOCUMENT`. Removal returns false for a document not present. Callers
should handle I/O exceptions at the import/removal boundary.

Keep one service per course/project and call `retrieve` for **each new question**.
For the current UI adapter, create `new LlmChatService(generationService,
knowledge.retrieve(request.question()))` for that query; its existing constructor
holds a single-query snapshot. Parsing/indexing should run off the Swing EDT.
Import and removal atomically publish an immutable search snapshot. An earlier
`RetrievalResult` remains unchanged after updates.

### Algorithm, source identity and limits

- PDFBox **3.0.8** extracts PDF text. Apache POI **6.0.0** extracts PPT/PPTX text
  boxes, table cells and nested groups. Speaker notes and images are excluded.
- Physical pages/slides start at one, including blank ones. Text is divided into
  windows of at most **1,000 Unicode code points**, with **150** overlapping code
  points. Chunks never cross a page or slide. PDF locations are `p.N`; PPT/PPTX
  locations are `slide N`, matching the current citation adapter.
- A document's canonical full path is both its internal identity and its returned
  `documentName`. This retains the real filename and distinguishes equal filenames
  in different folders. Reimporting the same path replaces its old chunks; a failed
  import retains the previous index. A successful zero-text reimport removes stale
  searchable content. No index survives a process restart.
- Tokenization uses Unicode NFKC normalization, case-insensitive English words,
  camel-case/acronym/underscore identifier parts, and adjacent Chinese bigrams.
  Single-character Chinese queries use indexed individual characters; longer
  Chinese queries use bigrams to avoid matching an unrelated shared character.
  Common question stopwords are ignored; Java keywords such as `for`, `do` and
  `this` remain searchable.
- An inverted index stores term frequencies and chunk lengths. BM25 uses
  **k1 = 1.2, b = 0.75**, rewarding rare matching terms and accounting for chunk
  length and term frequency. Matching is OR across query terms; extra matched
  terms contribute to the score. Repeated query words are counted once. Only
  positive scores are returned, ordered by descending score, then document path,
  physical page and chunk order. Scores are not probabilities.
  Imports rebuild the index snapshot; searches visit only postings for the query
  terms before sorting the matching chunks.
- Null/blank, stopword-only, and unmatched questions return
  `RetrievalResult.empty()`. `topK <= 0` throws `IllegalArgumentException`.
  No artificial sources are created. The existing LLM module handles the
  general-knowledge disclaimer when this result is empty.
- This is lexical retrieval: there is no embedding/synonym inference or OCR.
  Encrypted files are rejected. The service reads a selected local document into
  memory; a future upload UI must define suitable file-size/context policies.

### A's acceptance on 25 October 2026

The acceptance scope is Member A's service and its F0 document-processing and
retrieval responsibilities. The service and tests compile the actual shared
core/RAG sources, without an IntelliJ SDK or a live LLM.

| A deliverable | Implementation and acceptance evidence | Result |
|---|---|---|
| PDF, PPTX and PPT import | Real generated documents; physical pages/slides, blank pages, tables and grouped shapes | Passed |
| Keyword search and relevance ranking | Bilingual tokenization, inverted index, BM25, top-k and deterministic ties | Passed |
| Knowledge chunks with document and page information | Real source paths, original one-based locations and page-local Unicode chunks | Passed |
| No relevant results | Empty, stopword-only and unmatched queries return no sources; CLI displays an explicit message | Passed |
| Independently testable service and unit tests | Standalone build uses the same production sources; 38 JUnit test cases pass | Passed |

Linux re-verification:

- Linux x86_64; the existing PyCharm installation supplies a full JBR/JDK
  **25.0.2**. Gradle **9.6.0** and all dependencies were reused from the mounted
  Windows user cache. No IDE installation or global configuration change was needed.
- `clean build installDist --offline --no-daemon` succeeded: **38 tests passed;
  0 failures, 0 errors, 0 skipped** (18 ingestion, 8 ranking/tokenization,
  12 service cases). All compiled production classes have major version **65**
  (Java 21). The HTML test report remains under the path documented above.
- The new root-directory regression test first failed with the original
  `NullPointerException`, then passed after checking for a readable regular file
  before accessing the filename. Root-directory inputs now return
  `DocumentIngestionException` with reason `UNREADABLE_FILE` on the public API.
- The installed distribution was run with **JDK 21** and the real four-page
  assignment PDF. It imported 10 chunks; `retrieval augmented` returned physical
  pages `p.1` and `p.3` in descending score order. An unmatched question printed
  `No relevant course material found.` and exited with code 0. Root-directory
  and missing-file inputs printed a friendly import error and exited with code 1,
  without an exception stack trace.

To reproduce this Linux pass from the repository folder, using this workstation's
existing tools and cache (these paths are workstation-specific):

```bash
export JAVA_HOME="$HOME/.local/share/JetBrains/Toolbox/apps/pycharm/jbr"
export GRADLE_USER_HOME='/media/qianshun/Windows-SSD/Users/徐千顺/.gradle'
moduleAGradle="$GRADLE_USER_HOME/wrapper/dists/gradle-9.6.0-bin/42k10rwplmzkhuboz9kdazi7s/gradle-9.6.0/bin/gradle"
bash "$moduleAGradle" -p rag-standalone --offline --no-daemon clean build installDist

moduleALauncher=rag-standalone/build/install/cst3492-rag-standalone/bin/cst3492-rag-standalone
JAVA_HOME="$HOME/.local/share/java-exam/jdk-21" bash "$moduleALauncher" '../AY202627S1-CST3492-Assignment-A.pdf' 'retrieval augmented' 2
JAVA_HOME="$HOME/.local/share/java-exam/jdk-21" bash "$moduleALauncher" '../AY202627S1-CST3492-Assignment-A.pdf' 'qwertyunmatchedxyz' 2
JAVA_HOME="$HOME/.local/share/java-exam/jdk-21" bash "$moduleALauncher" / JDBC 2
```

The last command intentionally exits with code 1. The PDF is a local smoke-test
input outside the repository; automated tests generate their own documents and
do not need it. Invoking the cached Gradle distribution directly also avoids
changing the Unix wrapper's CRLF line endings in this shared Windows worktree.
In a normal Linux checkout with LF line endings, set `JAVA_HOME` to a full JDK 25
and use `bash ./gradlew -p rag-standalone clean build installDist` instead.

The earlier documented Windows verification used Zulu JDK 25.0.5 and passed
37 tests, before the new regression case. The Windows JDK under `../.tools/jdk25/`
and the earlier Windows launcher check are separate from this Linux pass.

The full IntelliJ plugin build and UI acceptance were not run. The plugin build
still targets IDEA 2026.2 (`262.*`) and defaults to the Windows path
`D:/IntelliJ/IntelliJ IDEA 2026.2.2`; this Linux pass only reused PyCharm's JDK.
Team integration must import documents off the EDT, retrieve for each new
question, pass the result to the existing LLM adapter and verify the actual UI
and citations in a compatible IDEA sandbox. The shared core contracts are unchanged.
The existing merge-conflict markers in `.gitignore` also remain a repository
integration cleanup item; they predate this A-only change.

## Team Architecture

The project is organised into five modules under the shared `com.cst3492.ta` architecture:

| Module | Responsibility |
|---|---|
| `core` | Shared data models and contracts between modules |
| `rag` | Course document ingestion, chunking, indexing, and retrieval |
| `llm` | LLM integration, answer generation, and citation handling |
| `ui` | Tool Window, chat interface, and editor context actions |
| `settings` | Plugin settings and API-key storage |

### Intended data flow

1. The UI collects a question and optional selected Java code.
2. The RAG module retrieves relevant course material and source locations.
3. The LLM module generates an answer using the question, selected code, and retrieved material.
4. The UI displays the answer and its course citations.

The module structure defines the intended integration architecture. The complete retrieval and LLM pipeline has not yet been implemented in this prototype.

### Current UI implementation

The current UI implementation is located under `com.cst3492.assistant`. It provides:

- A Tool Window with a mock chat interface.
- Structured answers and typed PDF-page/PPT-slide source previews.
- Editor right-click integration for attaching selected Java code.
- Separate code-context preview, context clearing, and send-time request snapshots.
- Unit tests for reply models, request handling, and wrapped labels.

The UI adapts B's core/LLM contracts through `LlmChatService`. Without a configured
API key it falls back to `MockChatService`; its citations remain test fixtures.
RAG is not connected: real requests currently use `RetrievalResult.empty()`.

### UI to LLM integration

Call chain: `AssistantChatPanel` -> `ChatRequestWorker` (SwingWorker background
thread) -> `LlmChatService` -> `AssistantQuery` -> `AnswerGenerationService` ->
`OpenAiCompatibleClient`. Question and selected Java code remain separate in the
immutable request. Completion, errors, cancellation and composer restoration run
on the EDT. Tool Window disposal cancels the worker and prevents further component
updates. Closed projects suppress callbacks and their Tool Window disposal cancels
the request. Cancellation is best-effort; it cannot retract an already-sent request.

There is no implemented settings store yet (`ta.settings` contains only package
documentation). The factory reads **DEEPSEEK_API_KEY** from its process environment
and uses B's existing `LlmConfig.deepseek(...)`. Never put keys in source files,
Gradle properties, logs, or test code. For a sandbox use masked PowerShell input:

```powershell
Set-Location 'D:\Desktop\Java'
$localSecret = Read-Host 'DeepSeek API key' -AsSecureString
$localCredential = [System.Management.Automation.PSCredential]::new('unused', $localSecret)
$env:DEEPSEEK_API_KEY = $localCredential.GetNetworkCredential().Password
Remove-Item Env:CST3492_CHAT_MODE -ErrorAction SilentlyContinue
.\gradlew.bat runIde --offline
# After the sandbox closes:
Remove-Item Env:DEEPSEEK_API_KEY -ErrorAction SilentlyContinue
Remove-Variable localCredential, localSecret
```

Restart an existing sandbox after changing its environment. No request is made
until Send/Enter; attached code is sent with the question. Nonblank configuration
does not prove the key is valid or the endpoint reachable. Errors are displayed
without raw response bodies or credentials. To force Mock mode even with a key,
set `$env:CST3492_CHAT_MODE = 'mock'` before launching. `/sources`, `/many-sources`,
`/no-sources`, `/error`, `/normal` are mock-only fixtures; real mode treats them as
ordinary questions.

Without A's retrieval, successful answers have NORMAL status, no Sources list, and
B's exact general-knowledge disclaimer. Empty citations are not a call failure.
The adapter's constructor accepts B's existing `RetrievalResult` for a single-query
integration later; no new RAG API is invented. Only grounded citations matching
retrieved metadata are mapped. Supported locations are single PDF `p.3`/`Page 3`
or PPT `Slide 5`. Missing/unknown positions, ranges, or PDF-style PPT page labels
are omitted rather than guessed. B's answer body remains intact, so unsupported
locations may retain inline markers without a UI Sources list. A's service now
returns supported single-page/slide locations; per-question UI wiring remains.

### Deterministic tests and manual live test

Default `test` excludes the explicit `llm-integration` tag. The existing live
`LlmIntegrationTest` keeps its environment-variable condition and assertions.
After locally configuring a key and network, explicitly run only the live test:

```powershell
.\gradlew.bat test -PrunLlmIntegrationTests=true --tests '*LlmIntegrationTest' --rerun-tasks --offline
```

`--offline` controls Gradle downloads, not HTTP inside tests. No key means the live
test is skipped by its existing condition. The previous LlmException has not been
diagnosed or fixed by this UI adapter; live API success is a separate manual check.

Manual UI acceptance: verify mock fixtures first, restart with real configuration,
then send a Java question. While waiting, resize/scroll the IDE: it should remain
responsive with the composer disabled. Completion must restore input/focus. Attach
selected code and explicitly send; expect general knowledge with the disclaimer
and no invented sources. Test an invalid local key for friendly error/retry, and
close the project/window during a request to check disposal behavior. These UI and
live-network checks are not proved by unit tests.


A minimal IntelliJ IDEA plugin foundation for our four-person team. It adds a
**CST3492 Assistant** Tool Window containing a minimal interactive mock chat.

## Conversation UI redesign

### Unified question composer

`ChatComposer` is a single rounded Swing surface containing the borderless input
and a bottom action row. The send control is a 38 logical-pixel circle, with the
platform's bundled upward-arrow icon, no text label, a tooltip/accessibility name,
and separate enabled/hover/pressed/disabled treatments. The input grows from two
to seven visual lines and then scrolls vertically. It shrinks after sending or
clearing. The composer stays outside the conversation viewport.

There is no persistent Enter/Shift+Enter instruction in the UI or response status
labels. Keyboard behavior remains Enter to submit and Shift+Enter to insert a
newline at the caret. Empty questions are rejected, and background request state,
cancel/failure recovery, editor context, and stale/disposed guards are unchanged.
Attached Java code remains separate above the input in a compact two-row preview;
its original text and the question draft are preserved when context changes.

**File upload is deferred in the UI.** A's `KnowledgeBaseService` now provides real
file ingestion and retrieval (see Module A above). `ChatRequest` still carries a
question and selected code; the composer has no file chooser. The UI integration
must import chosen paths off the EDT, handle the documented errors, and retrieve
fresh context for each question. File-size/context limits remain a UI integration
decision before enabling uploads.

For manual acceptance, test the icon-only send control at narrow/wide widths,
multiline growth and capped scrolling, Enter/Shift+Enter, empty input, loading,
error/retry and cancellation. Recheck context replacement/clearing and existing mock
source commands. Offscreen snapshots are generated by component tests; they are
not proof of visual verification in the IDE sandbox.

The Tool Window uses native Swing cards and StyledDocument text, with dark charcoal
surfaces and a light-theme fallback. User bubbles are offset right; assistant cards
have a small identity/status label. Markdown headings, bold text, lists, inline code,
and fenced code blocks are rendered with native styles, not a browser or web view.
Raw HTML, remote images and external Markdown URLs are never executed or fetched.
This is a deliberately small Markdown subset, not a complete CommonMark engine.
Original question/code data and the service contracts are unchanged.

`ConversationView` scrolls independently of the bottom composer. `MessageCard`,
`MarkdownView`, `SourceCards`, `ChatColumnLayout` and `ChatUi` keep presentation out
of the request lifecycle. `ChatReplyPresentation.message()` remains a plain-text
export/debug representation for existing callers; the rich UI uses `cardMessage()`
and displays status separately instead of inserting diagnostic prefixes into answers.

Sources are collapsed under **Sources (N)**. Each card keeps the supplied document,
PDF page/PPT slide and test-data status; **Details** reveals a metadata note and
**Copy citation**. Current `CourseSource` has no excerpt, score, URI, or navigation
target, so no preview excerpt, score, or Open action is fabricated. Those fields and
a validated navigation contract are future model extensions. Mock sources remain
explicitly identified as **Test data, not evidence**.

Inline `[n]` tokens are clickable only when that answer has supplied source records.
They expand the answer's source list. B's current citations lack stable IDs and the
adapter can omit unsupported locations, so the UI does **not** assume token `[n]`
means the nth displayed card and does not pretend to open a particular document.

Loading is shown by an indeterminate progress line and status text. Errors use a
compact error card with **Retry question**, which explicitly resends the failed
question and its original code snapshot. Sending, cancellation, disposal and stale
worker guards continue to use the existing `ChatRequestWorker` lifecycle.

Manual acceptance after fully closing any older sandbox:

```powershell
$env:CST3492_CHAT_MODE = 'mock'
.\gradlew.bat runIde --offline
```

Check the empty state; enter a question; try `/sources`, expand Sources/Details and
copy a citation; try `/many-sources` at narrow and wide widths. Check `/no-sources`,
`/normal`, and `/error` (including Retry). Attach and replace Java code, ensure the
draft survives, and clear the context. Enter must send and Shift+Enter must insert
a newline. Inspect scrolling while reading older cards and while following new
answers; verify the composer stays pinned and long filenames/code do not cause a
horizontal scrollbar. Test actual light/dark IDE themes and keyboard focus.

UI tests render the actual panel offscreen at narrow/wide widths, with snapshots
under `build/reports/ui/assistant-*.png`. These are test artifacts, not proof of
visual acceptance inside the IDE. No real API request is needed for these checks.

## Versions and prerequisites

- The existing **IntelliJ IDEA 2026.2.2** installation at
  `D:\IntelliJ\IntelliJ IDEA 2026.2.2` is the development sandbox and API target.
  Gradle uses the supported `local(...)` dependency; it does not download an IDE.
- **JDK 25** runs Gradle and compiles the plugin with **`--release 21`**, retaining
  Java 21 language, Java SE API, and bytecode limits for our own source. The IDE
  itself requires Java 25 and supplies JetBrains Runtime 25 for the sandbox.
- The official `org.jetbrains.intellij.platform` Gradle plugin is pinned to **2.19.0**.
- The Gradle Wrapper pins **Gradle 9.6.0**. Use a full **JDK 25**
  to run Gradle, and make sure `java -version` works. Set `JAVA_HOME` to that JDK
  if needed. There is no automatic JDK provisioning resolver.
- Gradle and plugin tooling dependencies must be cached or downloadable. No global
  Gradle install or API keys are required.

The local build declares compatibility with the **262 (2026.2)** branch only.
This replaces the original 252 target; Java 21 bytecode alone does not establish
compatibility with older IntelliJ APIs. `runIde` uses the local IDE binaries with
separate sandbox settings. To use another installation of the same platform branch,
append `'-PlocalIdePath=C:/path/to/IntelliJ IDEA'` to each Gradle command.

## Open in IntelliJ IDEA

Open this repository folder as a project and load the Gradle build when prompted.
Use the Wrapper distribution and select JDK 25 as the **Gradle JVM** under
Settings > Build, Execution, Deployment > Build Tools > Gradle. Let sync finish.

## Build and validate (PowerShell)

```powershell
Set-Location 'D:\Desktop\Java'
.\gradlew.bat build
.\gradlew.bat buildPlugin
.\gradlew.bat verifyPluginProjectConfiguration verifyPluginStructure
.\gradlew.bat verifyPlugin
```

`build` compiles and packages the plugin ZIP under `build/distributions/`.
`verifyPluginProjectConfiguration` checks build/platform settings;
`verifyPluginStructure` checks the packaged plugin descriptor and layout.
`verifyPlugin` performs binary compatibility checks against the local sandbox
IDE and may download the JetBrains Plugin Verifier. `test` runs JUnit 5 tests for
reply models and presentation; these checks do not replace the UI smoke check below.

## Launch the sandbox (PowerShell)

```powershell
Set-Location 'D:\Desktop\Java'
.\gradlew.bat runIde
```

In the separate sandbox IDE, open or create any project, then select
**View > Tool Windows > CST3492 Assistant**. It displays conversation history,
a multiline question field, and a **Send** button. Close the sandbox IDE to finish `runIde`.
Sandbox settings and logs are under
`.intellijPlatform/sandbox/cst3492-assistant/IU-2026.2.2/` and are ignored by Git.

## Verification on this workstation

The original remote 2025.2.5 build was blocked by downloads. The configuration now
uses the verified local IU-262.10315.125 installation and installed JDK 25.0.4.1.
See the actual task results below; a sandbox launch alone is not a UI smoke check.

- `compileJava`, `verifyPluginStructure`, `buildPlugin`, and `build`: successful
  with `--offline`. Package: `build/distributions/cst3492-assistant-0.1.0.zip`.
- `verifyPluginProjectConfiguration`: completed with warnings about the retained
  Java 21 source level being below platform Java 25 and the explicit `262.*`
  upper compatibility bound. These settings are intentional; this is not a
  warning-free validation result. Class-file major version 65 confirms Java 21 bytecode.
- `runIde --offline`: sandbox started with bundled JBR 25.0.4; its log confirms
  `Loaded custom plugins: CST3492 Assistant (0.1.0)`. Startup also reported
  warnings about other bundled IDE plugins and their dependencies. The Tool
  Window's welcome message was subsequently confirmed manually by the user.
- `verifyPlugin` (the separate binary compatibility verifier) was not run in this
  local verification pass. Structure/configuration validation is not that check.

For the mock chat change, `compileJava`, `verifyPluginStructure`, and `buildPlugin`
passed offline. `test` was executed but reported `NO-SOURCE`: the project has no
automated tests yet. Configuration validation completed with the same two warnings
described above. Chat interaction still needs the manual checks below.
The updated chat plugin also launched successfully with `runIde --offline` on
the local IDE; the startup log confirms the custom plugin loaded. Bundled IDE
plugin warnings remain, and this launch does not verify rendered chat interactions.

## Mock chat and manual acceptance

`AssistantChatPanel` owns the Swing UI; `ChatMessage` stores a role and plain text.
`ChatService` is an injectable synchronous interface taking `ChatRequest`
and returning `ChatReply` (a string-question convenience overload is also provided);
`MockChatService` returns local fixtures explicitly identified as UI test data. No question is sent
to a server in mock mode. Real calls run in `ChatRequestWorker` off the EDT.

Messages wrap in a read-only scrollable transcript with **You**, **Assistant**,
and **Error** labels. History stays in memory for this Tool Window's lifetime;
it is not saved across IDE restarts. Sending disables the composer until the mock
finishes. Services run in SwingWorker's shared pool with disposal checks; completion
callbacks update the UI on the EDT. There are no artificial delays or custom pools.

Manual acceptance in the sandbox:

1. Open a project and show the existing **CST3492 Assistant** Tool Window.
2. Click **Send** with empty or whitespace-only input: a clear prompt should appear,
   with no conversation entry added.
3. Type several lines (**Shift+Enter** inserts a newline at the caret), then click **Send**. Confirm
   one **You** message, one explicitly marked mock answer, and cleared input.
4. Send another message with **Enter**. Verify ordering and input focus recovery.
5. Repeatedly click **Send** or press the shortcut: an in-flight request must not be
   submitted twice. The fixed mock is instantaneous, so sending status may be brief.
6. Send long text and enough messages to scroll; resize the Tool Window and inspect
   previous messages, wrapping, and appearance in light/dark themes.
7. Error handling can be checked in development by injecting a `ChatService` that
   throws a `RuntimeException` instead of the mock. Expect a friendly error message
   and re-enabled input/Send; no failure trigger is exposed in the normal UI.

Close the sandbox to allow the long-running `runIde` task to finish.

## Structured answers and course source previews

`ChatReply` contains answer text, an explicit status (`MOCK`, `NORMAL`, `NO_SOURCES`,
`ERROR`), and an immutable list of `CourseSource` records. A source has a document
name, typed location (`PDF_PAGE` or `PPT_SLIDE`), a positive one-based number, and a
test-data flag. ERROR and NO_SOURCES results cannot contain citations; a NORMAL
answer with an empty source list is permitted. MOCK citations must be test data.

`ChatReplyPresentation` converts the structured result into plain text for the
existing wrapped transcript. The source section appears below the answer only
when actual source records were supplied. Each test citation is individually
marked **TEST DATA - not evidence**. No file is opened or retrieved, and no source
is evidence for a real answer in this prototype. UI code calls only `ChatService`.

Enter these exact commands into the chat for manual acceptance:

| Input | Expected display |
| --- | --- |
| Any ordinary question | MOCK answer, no Sources section |
| `/sources` | MOCK answer plus test PDF Page 3 and PPT Slide 5 citations |
| `/many-sources` | Long mock answer, 30 long test filenames in order; check wrapping/scrolling/resize |
| `/no-sources` | Explicit NO_SOURCES simulation, no citations, not a service error |
| `/error` | Explicit ERROR simulation, no citations; another question can still be sent |
| `/normal` | NORMAL status preview whose text explicitly says it is only a UI test |

Check that Enter sends, Shift+Enter inserts a newline, and consecutive questions
retain order. At the bottom, new messages should remain visible; when scrolled up
to history, appending should not force the view to the bottom. All visual behavior
still requires manual acceptance; unit tests do not verify window geometry.

For this source-display change, offline compilation, 11 JUnit tests, structure
validation, and ZIP packaging passed. Configuration validation completed with the
two existing Java-level/upper-build-bound warnings. JUnit dependencies are test-only;
no runtime UI dependency was added.

## C3: selected Java code context

In a Java editor, **Ask CST3492 Assistant** appears in the editor popup menu. It is
disabled unless there is a project, a live editor, and a non-whitespace selection.
The Action uses `CommonDataKeys.EDITOR` and `SelectionModel`, declares a BGT update
thread, and activates the existing Tool Window before finding its existing panel.
It does not keep editor/project references in global Action fields or create a
second panel. No Java language plugin dependency is needed: the menu is scoped by
the current virtual file's `.java` extension.

`setEditorContext` updates a read-only preview separately from the question field.
The newest selection replaces the preview and leaves the question draft untouched.
Code is preserved verbatim, including whitespace and line endings; the preview
supports its own scrolling. Nothing is sent merely by selecting or attaching code.
The context remains attached to subsequent questions until replaced or cleared
using **Clear context**. Clearing does not modify the source file or chat history.

On explicit send, `ChatRequest` captures the question and exact code separately.
The transcript shows **Editor code context (not executed)** separately from **You**.
The mock receives the request but never interprets, executes, or edits the code.
Changes to the current selection cannot alter an already-created request snapshot.
A future asynchronous service must consume this snapshot off the EDT for slow work.

Manual acceptance (not covered by the pure Java tests):

1. Open a Java file with no selection; right-click and verify the menu item is disabled.
   Whitespace-only selections must also leave it disabled.
2. Select a multiline Java block and verify the menu item becomes enabled.
3. Hide the assistant window first, then choose the action. Verify the same Tool
   Window opens, the selected code appears separately, and no request is sent.
4. Compare indentation and line breaks with the editor selection. Type a question,
   then select another block and invoke the action again. Verify the preview updates
   while the question draft and previous conversation remain intact.
5. Send explicitly with Enter or Send. Confirm question, code context, and mock reply
   appear in order. Shift+Enter must still insert a newline in the question field.
6. Use Clear context and verify the next question has no code attachment.
7. Send `/sources`, `/many-sources`, `/no-sources`, and `/error`; check the existing
   source/status displays, scrolling, resize, and continued sending still work.

C3 verification: offline compilation, all **18 JUnit tests** (11 existing + 7 new),
plugin structure validation, and ZIP packaging passed. Configuration validation
completed with the two existing warnings. Tests cover exact context preservation,
blank selection/question rejection, send-time snapshots, and mock-source regression;
they do not verify IDE menus, lazy Tool Window initialization, focus, or layout.
The C3 `runIde --offline` attempt was blocked in `prepareSandbox_runIde`: the old
sandbox still held `plugins_runIde/cst3492-assistant/lib/cst3492-assistant-0.1.0.jar`
open, so Windows refused to replace it. A normal window-close request was sent;
the process was not forcibly killed. Fully close that sandbox (including any
save/exit prompts), then rerun `./gradlew.bat runIde --offline` to test C3.

## Project layout and team extension points

```text
build.gradle.kts                         Build, Java toolchain, platform target
settings.gradle.kts                      Project name
gradle/wrapper/ + gradlew + gradlew.bat   Reproducible Gradle entry points
src/main/java/com/cst3492/assistant/ui/
    AssistantToolWindowFactory.java      Member C's Tool Window UI entry point
    AssistantChatPanel.java              Chat transcript, composer, send state
    ChatReplyPresentation.java           Status and source text formatting
src/main/java/com/cst3492/assistant/chat/
    ChatMessage.java                     Message role and text
    ChatReply.java                       Structured answer/status/immutable sources
    CourseSource.java                    Document and typed page/slide location
    ChatService.java                     Immediate mock reply contract
    MockChatService.java                 Explicitly labeled placeholder response
src/main/resources/META-INF/plugin.xml   Plugin identity and extension registration
src/test/java/com/cst3492/assistant/chat/
    ChatReplyTest.java                   Model, fixture, and presentation tests
```

Keep UI code in `ui`; as other members add features, place their implementation
in separate packages under `com.cst3492.assistant` and agree on small interfaces
between components. Avoid putting application logic into the Tool Window factory.
Use the shared Gradle build; keep personal IDE settings and secrets out of Git.

Document ingestion and retrieval are available through A's standalone service.
Real LLM integration is available with local configuration; the UI factory still
needs to connect per-question course retrieval.

Version references: [JetBrains Gradle plugin documentation](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html),
[platform Java/build compatibility](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html),
and [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html).
