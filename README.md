# CST3492 Assistant
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

The UI currently uses `MockChatService`. Its responses and source citations are test fixtures, not real AI answers or evidence from retrieved course materials. Integration with the shared `core`, `rag`, and `llm` contracts remains future work.


A minimal IntelliJ IDEA plugin foundation for our four-person team. It adds a
**CST3492 Assistant** Tool Window containing a minimal interactive mock chat.

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
`ChatService` is an injectable immediate-response interface taking `ChatRequest`
and returning `ChatReply` (a string-question convenience overload is also provided);
`MockChatService` returns local fixtures explicitly identified as UI test data. No question is sent
to a server. A future network service needs an asynchronous/background adapter;
do not put blocking calls into this interface's current EDT invocation.

Messages wrap in a read-only scrollable transcript with **You**, **Assistant**,
and **Error** labels. History stays in memory for this Tool Window's lifetime;
it is not saved across IDE restarts. Sending disables the composer until the mock
finishes. The callback is scheduled with `ToolWindowManager.invokeLater` on the EDT,
with disposal checks; there are no artificial delays or new worker threads.

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

This prototype includes no retrieval, LLM integration, or document ingestion.

Version references: [JetBrains Gradle plugin documentation](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html),
[platform Java/build compatibility](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html),
and [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html).
