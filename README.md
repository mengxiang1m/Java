# CST3492 TA Plugin

An IntelliJ IDEA plugin that acts as an AI teaching assistant (TA) for the
*CST3492 Java Enterprise Application Development* course (Assignment A, Group A,
feature set **F0**): a course-specific Q&amp;A chatbot with retrieval-augmented
generation (RAG) and source citation.

## Architecture

A single IntelliJ Platform plugin (Gradle, Java 17), organised into packages by
module. Each team member owns one module and contributes to the same source
tree and the same `plugin.xml`.

```
com.cst3492.ta
├── core      shared data model — the contract passed between modules
├── rag       retrieval pipeline: ingestion → chunking → indexing → retrieval
├── llm       LLM integration & answer generation (prompt → call → parse → cite)
├── ui        tool window, chat UI, editor right-click action
└── settings  plugin settings / API-key storage
```

### Data flow

```
UI (question + selected code)
        │
        ▼
RAG  ──► RetrievalResult (relevant chunks + source document/page range)
        │
        ▼
LLM  (prompt = question + code + chunks) ──► AssistantAnswer (content + citations + grounded flag)
        │
        ▼
UI  renders the answer, its citations, and a "general knowledge" notice when applicable
```

### Module contracts (the shared data model)

The `core` package defines the objects passed between modules:

| Type | Meaning |
|---|---|
| `RetrievedChunk` | a knowledge chunk + its source (document name, page range, score) |
| `RetrievalResult` | the relevant chunks for a query (empty = nothing found) |
| `AssistantQuery` | question + selected code + retrieval result (UI → LLM) |
| `AssistantAnswer` | answer text + citations + `basedOnCourseMaterial` (LLM → UI) |
| `Citation` | source document + page range |

Every module codes against this contract; the concrete classes are added as each
module is implemented.

## Build & test

This is a standard IntelliJ Platform Gradle project (Java 17).

```bash
./gradlew test      # run the unit tests
./gradlew build     # build the plugin distribution
./gradlew runIde    # launch a sandbox IDE with the plugin
```

Notes for this environment (China / newer JDK):

* The Gradle wrapper downloads Gradle from the **Tencent mirror**
  (`mirrors.cloud.tencent.com`), because `services.gradle.org` is unreachable here.
* The build compiles to Java 17 bytecode regardless of the JDK running Gradle
  (`options.release = 17`).
* Gradle 8.14 does **not** run on JDK 26/27. Open the project in **IntelliJ
  IDEA** and use its bundled JetBrains Runtime (JBR 21) as the Gradle JVM
  (File → Settings → Build Tools → Gradle → Gradle JVM), or install a JDK 17–21.

## LLM configuration

The LLM module targets OpenAI-compatible endpoints and supports both providers:

* **DeepSeek** — `https://api.deepseek.com/v1`, model `deepseek-chat`.
* **Tongji AI Services Platform** — `https://agi.tongji.edu.cn` (base URL and
  model name are account-specific).

API keys are read from plugin settings or an environment variable and are never
hardcoded or committed.
